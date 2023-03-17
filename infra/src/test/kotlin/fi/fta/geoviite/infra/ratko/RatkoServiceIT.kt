package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.authorization.getCurrentUserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.PublishRequestIds
import fi.fta.geoviite.infra.ratko.model.RatkoNodeType
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumberStateType
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class RatkoServiceIT @Autowired constructor(
    val trackNumberService: LayoutTrackNumberService,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
    val referenceLineService: ReferenceLineService,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
    val ratkoService: RatkoService,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
    val publicationService: PublicationService,
    val switchService: LayoutSwitchService,
    val kmPostService: LayoutKmPostService,
    val kmPostDao: LayoutKmPostDao,
    val fakeRatkoService: FakeRatkoService,

    ) : ITTestBase() {
    @BeforeEach
    fun cleanup() {
        val sql = """
            truncate publication.publication cascade;
            truncate integrations.lock cascade;
            truncate layout.track_number cascade;
        """.trimIndent()
        jdbc.execute(sql) { it.execute() }
    }

    lateinit var fakeRatko: FakeRatko

    @BeforeEach
    fun startServer() {
        fakeRatko = fakeRatkoService.start()
        fakeRatko.isOnline()
    }

    @AfterEach
    fun stopServer() {
        fakeRatko.stop()
    }

    @Test
    fun startRatkoPublish() {
        ratkoService.pushChangesToRatko(getCurrentUserName())
    }

    @Test
    fun testChangeSet() {
        val referenceLineAlignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val locationTrackAlignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        referenceLineDao.insert(referenceLine(trackNumberId).copy(alignmentVersion = referenceLineAlignmentVersion))
        val officialVersion = locationTrackDao.insert(
            locationTrack(trackNumberId = trackNumberId).copy(alignmentVersion = locationTrackAlignmentVersion)
        )
        val draft = locationTrackService.getDraft(officialVersion.id).let { orig ->
            orig.copy(name = AlignmentName("${orig.name}-draft"))
        }
        locationTrackService.saveDraft(draft, alignmentDao.fetch(locationTrackAlignmentVersion)).rowVersion
        fakeRatko.hasLocationTrack(ratkoLocationTrack(id = draft.externalId.toString()))
        publishAndPush(locationTracks = listOf(officialVersion.rowVersion))
    }

    @Test
    fun pushNewTrackNumber() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = null)).rowVersion
        insertSomeOfficialReferenceLineFor(originalTrackNumberVersion.id)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(originalTrackNumberVersion))

        val pushedRouteNumber = fakeRatko.getPushedRouteNumber(Oid("1.2.3.4.5"))
        assertEquals(trackNumber.value, pushedRouteNumber[0].name)
        assertEquals("augh", pushedRouteNumber[0].description)
        assertEquals(RatkoRouteNumberStateType.VALID, pushedRouteNumber[0].state.name)
    }

    @Test
    fun pushTwoTrackNumbers() {
        val trackNumber1 = getUnusedTrackNumber()
        val trackNumber1Version = trackNumberService.saveDraft(trackNumber(trackNumber1, externalId = null))
        insertSomeOfficialReferenceLineFor(trackNumber1Version.id)
        val trackNumber2 = getUnusedTrackNumber()
        val trackNumber2Version = trackNumberService.saveDraft(trackNumber(trackNumber2, externalId = null))
        insertSomeOfficialReferenceLineFor(trackNumber2Version.id)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("2.3.4.5.6", "3.4.5.6.7"))
        publishAndPush(trackNumbers = listOf(trackNumber1Version.rowVersion, trackNumber2Version.rowVersion))
        val pushedRouteNumber1 = fakeRatko.getPushedRouteNumber(Oid("2.3.4.5.6"))
        val pushedRouteNumber2 = fakeRatko.getPushedRouteNumber(Oid("3.4.5.6.7"))
        assertEquals(trackNumber1.value, pushedRouteNumber1[0].name)
        assertEquals(trackNumber2.value, pushedRouteNumber2[0].name)
    }

    @Test
    fun lengthenReferenceLine() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = null)).rowVersion
        val originalReferenceLineDaoResponse = insertSomeOfficialReferenceLineFor(originalTrackNumberVersion.id)
        val originalReferenceLine = referenceLineDao.fetch(originalReferenceLineDaoResponse.rowVersion)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(originalTrackNumberVersion))
        referenceLineService.saveDraft(
            originalReferenceLine,
            alignment(segment(Point(0.0, 0.0), Point(20.0, 0.0)))
        )
        publishAndPush(referenceLines = listOf(originalReferenceLineDaoResponse.rowVersion))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")
        assertEquals(9, pushedPoints[0].size)
        assertEquals(19, pushedPoints[1].size)
        assertEquals(
            pushedPoints[0].map { p -> p.geometry?.coordinates },
            pushedPoints[1].take(9).map { p -> p.geometry?.coordinates })
        assertEquals((1..19).toList(), pushedPoints[1].map { p -> p.kmM.meters.toInt() })
    }

    @Test
    fun removeKmPostBeforeSwitch() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.insert(
            trackNumber(trackNumber, description = "augh", externalId = Oid("1.1.1.1.1"))
        ).rowVersion.id
        insertSomeOfficialReferenceLineFor(trackNumberId)
        val kmPost1 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(1), Point(2.0, 0.0)))
        val kmPost2 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(2), Point(4.0, 0.0)))

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.1.1.1.1"))

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion),
            kmPosts = listOf(kmPost1.rowVersion, kmPost2.rowVersion),
        )

        kmPostService.saveDraft(
            draft(kmPostDao.fetch(kmPostDao.fetchVersion(kmPost2.id, PublishType.OFFICIAL)!!)).copy(
                state = LayoutState.DELETED
            )
        )
        fakeRatko.hasLocationTrack(fakeRatko.getLastPushedLocationTrack("1.2.3.4.5"))
        fakeRatko.hasLocationTrack(fakeRatko.getLastPushedLocationTrack("2.3.4.5.6"))
        fakeRatko.hasSwitch(fakeRatko.getLastPushedSwitch("3.4.5.6.7"))

        publishAndPush(kmPosts = listOf(kmPost2.rowVersion))

        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")
        // switch was originally after kmPost2, but it got removed and then we pushed again
        assertEquals("0002+0001", switchLocations[0][0].nodecollection.nodes[0].point.kmM.toString())
        assertEquals("0002+0003.5", switchLocations[0][1].nodecollection.nodes[0].point.kmM.toString())
        assertEquals("0001+0003", switchLocations[1][0].nodecollection.nodes[0].point.kmM.toString())
        assertEquals("0001+0005.5", switchLocations[1][1].nodecollection.nodes[0].point.kmM.toString())
    }

    @Test
    fun pushSwitch() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)

        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion)
        )
        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")[0]
        assertEquals(
            listOf(listOf(5f, 5f), listOf(7.5f)),
            switchLocations.map { l -> l.nodecollection.nodes.map { p -> p.point.kmM.meters.toFloat() } })
        assertEquals(listOf(listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C), listOf(RatkoNodeType.JOINT_C)),
            switchLocations.map { l -> l.nodecollection.nodes.map { n -> n.nodeType } })
        assertEquals(listOf(listOf("1.2.3.4.5", "1.2.3.4.5"), listOf("2.3.4.5.6")),
            switchLocations.map { push -> push.nodecollection.nodes.map { n -> n.point.locationtrack!!.toString() } })
        assertEquals(
            trackNumber.externalId!!.toString(),
            switchLocations.map { s -> s.nodecollection.nodes[0].point.routenumber!!.toString() }.distinct().single()
        )
        assertEquals(listOf(1, 2), switchLocations.map { s -> s.priority })
    }

    @Test
    fun moveSwitch() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)

        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion)
        )
        fakeRatko.hasSwitch(fakeRatko.getLastPushedSwitch("3.4.5.6.7"))
        fakeRatko.hasLocationTrack(fakeRatko.getLastPushedLocationTrack("1.2.3.4.5"))
        fakeRatko.hasLocationTrack(fakeRatko.getLastPushedLocationTrack("2.3.4.5.6"))

        detachSwitchesFromTrack(throughTrack.id)
        detachSwitchesFromTrack(branchingTrack.id)

        val differentThroughTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(0.0, 10.0), Point(5.0, 10.0), switchId = switch.id, endJointNumber = JointNumber(1)),
                segment(Point(5.0, 10.0), Point(10.0, 10.0), switchId = switch.id, startJointNumber = JointNumber(3)),
            )
        )
        val differentBranchingTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(5.0, 10.0), Point(7.5, 10.5), switchId = switch.id, endJointNumber = JointNumber(3)),
                segment(Point(7.5, 10.5), Point(10.0, 11.0), switchId = switch.id, startJointNumber = JointNumber(3))
            )
        )
        listOf("4.4.4.4.4", "5.5.5.5.5").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        publishAndPush(locationTracks = listOf(differentThroughTrack.rowVersion, differentBranchingTrack.rowVersion))
        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")

        val expectedNodeTypes = listOf(
            listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C),
            listOf(RatkoNodeType.JOINT_C)
        )
        assertEquals(
            listOf(expectedNodeTypes, expectedNodeTypes),
            switchLocations.map { push -> push.map { location -> location.nodecollection.nodes.map { n -> n.nodeType } } }
        )

        assertEquals(
            listOf(listOf("1.2.3.4.5", "1.2.3.4.5"), listOf("2.3.4.5.6")),
            switchLocations[0].map { location -> location.nodecollection.nodes.map { n -> n.point.locationtrack.toString() } })
        assertEquals(
            listOf(listOf("4.4.4.4.4", "4.4.4.4.4"), listOf("5.5.5.5.5")),
            switchLocations[1].map { location -> location.nodecollection.nodes.map { n -> n.point.locationtrack.toString() } })

    }

    private fun setupDraftSwitchAndLocationTracks(trackNumberId: IntId<TrackLayoutTrackNumber>): Triple<DaoResponse<TrackLayoutSwitch>, DaoResponse<LocationTrack>, DaoResponse<LocationTrack>> {
        val switch = switchService.saveDraft(
            switch(
                123,
                joints = listOf(
                    switchJoint(124).copy(number = JointNumber(1)),
                    switchJoint(125).copy(number = JointNumber(3)),
                )
            )
        )
        val throughTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(0.0, 0.0), Point(5.0, 0.0), switchId = switch.id, endJointNumber = JointNumber(1)),
                segment(Point(5.0, 0.0), Point(10.0, 0.0), switchId = switch.id, startJointNumber = JointNumber(3)),
            )
        )
        val branchingTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(5.0, 0.0), Point(7.5, 0.5), switchId = switch.id, endJointNumber = JointNumber(3)),
                segment(Point(7.5, 0.5), Point(10.0, 1.0), switchId = switch.id, startJointNumber = JointNumber(3)),
            )
        )

        return Triple(switch, throughTrack, branchingTrack)
    }

    @Test
    fun linkKmPost() {
        val trackNumber = layoutTrackNumberDao.insert(trackNumber(getUnusedTrackNumber(), externalId = null))
        insertSomeOfficialReferenceLineFor(trackNumber.id)
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(trackNumber.rowVersion))
        fakeRatko.hasRouteNumber(ratkoRouteNumber(id = "1.2.3.4.5"))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")

        val kmPost = kmPostService.saveDraft(kmPost(trackNumber.id, KmNumber(1), Point(5.0, 0.0)))
        publishAndPush(kmPosts = listOf(kmPost.rowVersion))
        val deletions = fakeRatko.getRouteNumberPointDeletions("1.2.3.4.5")
        val updatedPoints = fakeRatko.getUpdatedRouteNumberPoints("1.2.3.4.5")
        assertEquals(listOf("0000", "0001"), deletions)
        // km post was placed precisely at meter point => coordinates shouldn't change
        assertEquals(
            pushedPoints.flatten().map { point -> point.geometry!!.coordinates },
            updatedPoints.flatten().map { point -> point.geometry!!.coordinates }
        )
        assertEquals("0000", updatedPoints[0].map { p -> p.kmM.kmNumber.toString() }.distinct().single())
        assertEquals("0001", updatedPoints[1].map { p -> p.kmM.kmNumber.toString() }.distinct().single())
    }

    @Test
    fun pushTrackNumberDeletion() {
        val originalTrackNumberVersion = layoutTrackNumberDao.insert(trackNumber(getUnusedTrackNumber())).rowVersion
        val trackNumberId = originalTrackNumberVersion.id
        val trackNumber = layoutTrackNumberDao.fetch(originalTrackNumberVersion)
        insertSomeOfficialReferenceLineFor(trackNumberId)
        trackNumberService.saveDraft(trackNumber.copy(state = LayoutState.DELETED))

        val originalLocationTrackVersion = locationTrackService.saveDraft(
            locationTrack(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        ).rowVersion
        val locationTrack = locationTrackDao.fetch(originalLocationTrackVersion)
        locationTrackService.saveDraft(locationTrack.copy(state = LayoutState.DELETED))

        fakeRatko.hasRouteNumber(ratkoRouteNumber(id = trackNumber.externalId.toString()))
        fakeRatko.hasLocationTrack(ratkoLocationTrack(id = locationTrack.externalId.toString()))

        publishAndPush(
            trackNumbers = listOf(originalTrackNumberVersion),
            locationTracks = listOf(originalLocationTrackVersion)
        )
        assertEquals(listOf(""), fakeRatko.getRouteNumberPointDeletions(trackNumber.externalId!!.toString()))
        assertEquals(listOf(""), fakeRatko.getLocationTrackPointDeletions(locationTrack.externalId!!.toString()))
    }

    private fun insertSomeOfficialReferenceLineFor(trackNumberId: IntId<TrackLayoutTrackNumber>): DaoResponse<ReferenceLine> {
        return insertOfficialReferenceLineFromPair(
            referenceLineAndAlignment(
                trackNumberId,
                segment(
                    Point(0.0, 0.0),
                    Point(10.0, 0.0)
                )
            )
        )
    }

    private fun insertOfficialReferenceLineFromPair(pair: Pair<ReferenceLine, LayoutAlignment>): DaoResponse<ReferenceLine> {
        val alignmentVersion = alignmentDao.insert(pair.second)
        return referenceLineDao.insert(pair.first.copy(alignmentVersion = alignmentVersion))
    }

    private fun detachSwitchesFromTrack(locationTrackId: IntId<LocationTrack>) {
        val locationTrack = locationTrackDao.fetch(locationTrackDao.fetchDraftVersion(locationTrackId)!!)
        val alignment = alignmentDao.fetch(locationTrack.alignmentVersion!!)
        val deSwitchedAlignment = alignment.copy(segments = alignment.segments.map { s ->
            s.copy(switchId = null, startJointNumber = null, endJointNumber = null)
        })
        locationTrackService.saveDraft(locationTrack, deSwitchedAlignment)
    }

    private fun publishAndPush(
        trackNumbers: List<RowVersion<TrackLayoutTrackNumber>> = listOf(),
        referenceLines: List<RowVersion<ReferenceLine>> = listOf(),
        locationTracks: List<RowVersion<LocationTrack>> = listOf(),
        switches: List<RowVersion<TrackLayoutSwitch>> = listOf(),
        kmPosts: List<RowVersion<TrackLayoutKmPost>> = listOf(),
    ) {
        val ids = PublishRequestIds(
            trackNumbers = trackNumbers.map { it.id },
            referenceLines = referenceLines.map { it.id },
            locationTracks = locationTracks.map { it.id },
            switches = switches.map { it.id },
            kmPosts = kmPosts.map { it.id },
        )
        publicationService.updateExternalId(ids)
        val versions = publicationService.getValidationVersions(ids)
        val calculatedChanges = publicationService.getCalculatedChanges(versions)
        publicationService.publishChanges(
            versions,
            calculatedChanges,
            ""
        )
        ratkoService.pushChangesToRatko(getCurrentUserName())
    }
}
