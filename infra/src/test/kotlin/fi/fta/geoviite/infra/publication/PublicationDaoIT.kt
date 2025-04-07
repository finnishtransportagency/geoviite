package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TEST_USER
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.DirectChanges
import fi.fta.geoviite.infra.integration.IndirectChanges
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.SwitchChange
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.publishedVersions
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationDaoIT
@Autowired
constructor(
    val publicationDao: PublicationDao,
    val switchDao: LayoutSwitchDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val kmPostDao: LayoutKmPostDao,
    val referenceLineDao: ReferenceLineDao,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val layoutDesignDao: LayoutDesignDao,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun noPublicationCandidatesFoundWithoutDrafts() {
        assertTrue(publicationDao.fetchTrackNumberPublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchReferenceLinePublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchSwitchPublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchKmPostPublicationCandidates(PublicationInMain).isEmpty())
    }

    @Test
    fun referenceLinePublicationCandidatesAreFound() {
        val trackNumberId = insertAndCheck(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)).first.id
        val (_, line) = insertAndCheck(referenceLine(trackNumberId, draft = false))
        val (_, draft) = insertAndCheck(asMainDraft(line).copy(startAddress = TrackMeter("0123", 658.321, 3)))
        val candidates = publicationDao.fetchReferenceLinePublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(line.id, candidates.first().id)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun locationTrackPublicationCandidatesAreFound() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (_, draft) = insertAndCheck(asMainDraft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun switchPublicationCandidatesAreFound() {
        val (_, switch) = insertAndCheck(switch(draft = false))
        val (_, draft) = insertAndCheck(asMainDraft(switch).copy(name = SwitchName("${switch.name} DRAFT")))
        val candidates = publicationDao.fetchSwitchPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(switch.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun createOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = true))
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.CREATE, candidates.first().operation)
    }

    @Test
    fun modifyOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) = insertAndCheck(asMainDraft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService.getOrThrow(MainLayoutContext.official, draft.id as IntId).let { lt ->
                lt.copy(name = AlignmentName("${lt.name} TEST"))
            },
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun deleteOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) = insertAndCheck(asMainDraft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService
                .getOrThrow(MainLayoutContext.official, draft.id as IntId)
                .copy(state = LocationTrackState.DELETED),
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.DELETE, candidates.first().operation)
    }

    @Test
    fun restoreOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) =
            insertAndCheck(
                asMainDraft(track).copy(name = AlignmentName("${track.name} DRAFT"), state = LocationTrackState.DELETED)
            )
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService
                .getOrThrow(MainLayoutContext.official, draft.id as IntId)
                .copy(state = LocationTrackState.IN_USE),
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.RESTORE, candidates.first().operation)
    }

    @Test
    fun allCalculatedChangesAreRecorded() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrackId = insertAndCheck(locationTrack(trackNumberId, draft = false)).first.id
        val switchId = insertAndCheck(switch(draft = false)).first.id

        val switchJointChange =
            SwitchJointChange(
                JointNumber(1),
                false,
                TrackMeter(1234, "AA", 12.34, 3),
                Point(100.0, 200.0),
                locationTrackId,
                Oid("123.456.789"),
                trackNumberId,
                Oid("1.234.567"),
            )

        val changes =
            CalculatedChanges(
                directChanges =
                    DirectChanges(
                        trackNumberChanges =
                            listOf(
                                TrackNumberChange(
                                    trackNumberId,
                                    setOf(KmNumber(1234), KmNumber(45, "AB")),
                                    isStartChanged = true,
                                    isEndChanged = false,
                                )
                            ),
                        kmPostChanges = emptyList(),
                        referenceLineChanges = emptyList(),
                        locationTrackChanges =
                            listOf(
                                LocationTrackChange(
                                    locationTrackId,
                                    setOf(KmNumber(456)),
                                    isStartChanged = false,
                                    isEndChanged = true,
                                )
                            ),
                        switchChanges = listOf(SwitchChange(switchId, listOf(switchJointChange))),
                    ),
                indirectChanges = IndirectChanges(emptyList(), emptyList(), emptyList()),
            )
        val publicationId =
            publicationDao.createPublication(LayoutBranch.main, FreeTextWithNewLines.of(""), PublicationCause.MANUAL)
        publicationDao.insertCalculatedChanges(
            publicationId,
            changes,
            publishedVersions(
                trackNumbers = listOf(trackNumberDao.fetchVersion(MainLayoutContext.official, trackNumberId)!!),
                locationTracks = listOf(locationTrackDao.fetchVersion(MainLayoutContext.official, locationTrackId)!!),
                switches = listOf(switchDao.fetchVersion(MainLayoutContext.official, switchId)!!),
            ),
        )

        val publishedTrackNumbers = publicationDao.fetchPublishedTrackNumbers(publicationId)
        val publishedLocationTracks = publicationDao.fetchPublishedLocationTracks(publicationId)
        val publishedSwitches = publicationDao.fetchPublishedSwitches(publicationId)
        assertTrue(publishedTrackNumbers.directChanges.all { it.id == trackNumberId })
        assertEquals(
            changes.directChanges.trackNumberChanges.flatMap { it.changedKmNumbers }.sorted(),
            publishedTrackNumbers.directChanges.flatMap { it.changedKmNumbers }.sorted(),
        )

        assertTrue(publishedLocationTracks.directChanges.all { it.id == locationTrackId })
        assertEquals(
            changes.directChanges.locationTrackChanges.flatMap { it.changedKmNumbers }.sorted(),
            publishedLocationTracks.directChanges.flatMap { it.changedKmNumbers }.sorted(),
        )

        assertTrue(publishedSwitches.directChanges.all { it.id == switchId })
        assertEquals(listOf(switchJointChange), publishedSwitches.directChanges.flatMap { it.changedJoints })
    }

    @Test
    fun `Publication message is stored and fetched correctly`() {
        val message = FreeTextWithNewLines.of("Test")
        val publicationId = publicationDao.createPublication(LayoutBranch.main, message, PublicationCause.MANUAL)
        assertEquals(message, publicationDao.getPublication(publicationId).message)
    }

    @Test
    fun fetchOfficialSwitchTrackNumbers() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (_, switch) = insertAndCheck(switch(name = "Foo", draft = false))
        val switchId = switch.id as IntId
        insertAndCheck(
            locationTrack(trackNumberId, draft = false)
                .copy(topologyEndSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)))
        )
        insertAndCheck(asMainDraft(switch.copy(name = SwitchName("FooEdited"))))

        val publicationCandidates = publicationDao.fetchSwitchPublicationCandidates(PublicationInMain)
        val editedCandidate = publicationCandidates.first { s -> s.name == SwitchName("FooEdited") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    @Test
    fun fetchDraftOnlySwitchTrackNumbers() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (_, switch) = insertAndCheck(switch(name = "Foo", draft = true))
        val switchId = switch.id as IntId
        insertAndCheck(
            locationTrack(trackNumberId, draft = true)
                .copy(topologyEndSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)))
        )
        val publicationCandidates = publicationDao.fetchSwitchPublicationCandidates(PublicationInMain)
        val editedCandidate = publicationCandidates.first { s -> s.name == SwitchName("Foo") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    @Test
    fun `fetchLinkedLocationTracks does not confuse official rows with drafts`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val switch = mainOfficialContext.insert(switch()).id
        val track1Main =
            mainOfficialContext.insert(
                locationTrack(trackNumber),
                alignment(
                    segment(Point(0.0, 0.0), Point(1.0, 0.0)).copy(switchId = switch, startJointNumber = JointNumber(1))
                ),
            )
        val track2Main =
            mainOfficialContext.insert(
                locationTrack(trackNumber),
                alignment(
                    segment(Point(0.0, 0.0), Point(1.0, 0.0)).copy(switchId = switch, startJointNumber = JointNumber(1))
                ),
            )
        mainDraftContext.insert(
            locationTrackDao.fetch(track1Main),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
        )
        mainDraftContext.insert(
            locationTrackDao.fetch(track2Main),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
        )

        assertEquals(
            // null locationTrackIdsInPublicationUnit = everything in publication unit = nothing
            // linked
            mapOf(),
            publicationDao.fetchLinkedLocationTracks(ValidateTransition(PublicationInMain), listOf(switch), null),
        )
        assertEquals(
            // nothing in publication unit = everything linked
            mapOf(switch to setOf(track1Main, track2Main)),
            publicationDao.fetchLinkedLocationTracks(ValidateTransition(PublicationInMain), listOf(switch), listOf()),
        )
        assertEquals(
            // just one track in publication unit = only the track not in publication unit is linked
            mapOf(switch to setOf(track2Main)),
            publicationDao.fetchLinkedLocationTracks(
                ValidateTransition(PublicationInMain),
                listOf(switch),
                listOf(track1Main.id),
            ),
        )
    }

    @Test
    fun `fetchLinkedLocationTracks works on publication units`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchByAlignment = switchDao.save(switch(draft = false)).id
        val switchByTopo = switchDao.save(switch(draft = false)).id
        val dummyAlignment = alignmentDao.insert(alignment())
        val officialLinkedTopo =
            locationTrackDao.save(
                locationTrack(
                    trackNumberId,
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchByTopo, JointNumber(1)),
                    alignmentVersion = dummyAlignment,
                    draft = false,
                )
            )
        val draftLinkedTopo =
            locationTrackDao.save(
                locationTrack(
                    trackNumberId,
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchByTopo, JointNumber(3)),
                    alignmentVersion = dummyAlignment,
                    draft = true,
                )
            )
        val officialLinkedAlignment =
            locationTrackDao.save(
                locationTrack(
                    trackNumberId = trackNumberId,
                    alignmentVersion =
                        alignmentDao.insert(
                            alignment(
                                segment(
                                    Point(0.0, 0.0),
                                    Point(1.0, 1.0),
                                    switchId = switchByAlignment,
                                    startJointNumber = JointNumber(1),
                                )
                            )
                        ),
                    draft = false,
                )
            )
        val draftLinkedAlignment =
            locationTrackDao.save(
                locationTrack(
                    trackNumberId = trackNumberId,
                    alignmentVersion =
                        alignmentDao.insert(
                            alignment(
                                segment(
                                    Point(2.0, 2.0),
                                    Point(3.0, 3.0),
                                    switchId = switchByAlignment,
                                    startJointNumber = JointNumber(3),
                                )
                            )
                        ),
                    draft = true,
                )
            )
        val target = draftTransitionOrOfficialState(PublicationState.DRAFT, LayoutBranch.main)
        assertEquals(
            setOf(officialLinkedAlignment, draftLinkedAlignment),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByAlignment))[switchByAlignment],
        )
        assertEquals(
            setOf(officialLinkedAlignment),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByAlignment), listOf())[switchByAlignment],
        )
        assertEquals(
            setOf(officialLinkedAlignment, draftLinkedAlignment),
            publicationDao
                .fetchLinkedLocationTracks(target, listOf(switchByAlignment), listOf(draftLinkedAlignment.id))[
                    switchByAlignment],
        )
        assertEquals(
            setOf(officialLinkedTopo, draftLinkedTopo),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByTopo))[switchByTopo],
        )
        assertEquals(
            setOf(officialLinkedTopo),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByTopo), listOf())[switchByTopo],
        )
        assertEquals(
            setOf(officialLinkedTopo, draftLinkedTopo),
            publicationDao
                .fetchLinkedLocationTracks(target, listOf(switchByTopo), listOf(draftLinkedTopo.id))[switchByTopo],
        )
    }

    @Test
    fun `fetchLatestPublicationDetails lists design publications in design mode`() {
        val someDesign = DesignBranch.of(layoutDesignDao.insert(layoutDesign("one")))
        val anotherDesign = DesignBranch.of(layoutDesignDao.insert(layoutDesign("two")))
        publicationDao.createPublication(someDesign, FreeTextWithNewLines.of("in someDesign"), PublicationCause.MANUAL)
        publicationDao.createPublication(LayoutBranch.main, FreeTextWithNewLines.of("in main"), PublicationCause.MANUAL)
        publicationDao.createPublication(
            anotherDesign,
            FreeTextWithNewLines.of("in anotherDesign"),
            PublicationCause.MANUAL,
        )
        publicationDao.createPublication(
            LayoutBranch.main,
            FreeTextWithNewLines.of("again in main"),
            PublicationCause.MANUAL,
        )
        assertEquals(
            listOf("in anotherDesign", "in someDesign"),
            publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 2).map { it.message.toString() },
        )
        assertEquals(
            listOf("again in main", "in main"),
            publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, 2).map { it.message.toString() },
        )
    }

    @Test
    fun `getPreviouslyPublishedDesignVersion returns the most recent design version before the given publication`() {
        val someDesign = layoutDesignDao.insert(layoutDesign(name = "foo"))
        val pub1 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                FreeTextWithNewLines.of("pub 1"),
                PublicationCause.MANUAL,
            )
        layoutDesignDao.update(someDesign, layoutDesign(name = "bar"))
        val pub2 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                FreeTextWithNewLines.of("pub 2"),
                PublicationCause.MANUAL,
            )
        layoutDesignDao.update(someDesign, layoutDesign(name = "spam"))
        val pub3 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                FreeTextWithNewLines.of("pub 3"),
                PublicationCause.MANUAL,
            )
        val pub4 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                FreeTextWithNewLines.of("pub 4"),
                PublicationCause.MANUAL,
            )
        val pub5 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                FreeTextWithNewLines.of("pub 5"),
                PublicationCause.MANUAL,
            )
        assertEquals(null, publicationDao.getPreviouslyPublishedDesignVersion(pub1, someDesign))
        assertEquals(1, publicationDao.getPreviouslyPublishedDesignVersion(pub2, someDesign))
        assertEquals(2, publicationDao.getPreviouslyPublishedDesignVersion(pub3, someDesign))
        assertEquals(3, publicationDao.getPreviouslyPublishedDesignVersion(pub4, someDesign))
        assertEquals(3, publicationDao.getPreviouslyPublishedDesignVersion(pub5, someDesign))
    }

    @Test
    fun `Publication has a generated uuid`() {
        val publication =
            publicationDao
                .createPublication(
                    MainLayoutContext.official.branch,
                    FreeTextWithNewLines.of("test publication"),
                    PublicationCause.MANUAL,
                )
                .let(publicationDao::getPublication)

        assertTrue(publication.uuid.toString().isNotEmpty())
    }

    private fun insertAndCheck(
        trackNumber: LayoutTrackNumber
    ): Pair<LayoutRowVersion<LayoutTrackNumber>, LayoutTrackNumber> {
        val official = trackNumberDao.save(trackNumber)
        val fromDb = trackNumberDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(trackNumber, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, trackNumber.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue { fromDb.id is IntId }
        return official to fromDb
    }

    private fun insertAndCheck(switch: LayoutSwitch): Pair<LayoutRowVersion<LayoutSwitch>, LayoutSwitch> {
        val official = switchDao.save(switch)
        val fromDb = switchDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(switch, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, switch.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return official to fromDb
    }

    private fun insertAndCheck(referenceLine: ReferenceLine): Pair<LayoutRowVersion<ReferenceLine>, ReferenceLine> {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val lineWithAlignment = referenceLine.copy(alignmentVersion = dbAlignmentVersion)
        val official = referenceLineDao.save(lineWithAlignment)
        val fromDb = referenceLineDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(lineWithAlignment, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, referenceLine.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return official to fromDb
    }

    private fun insertAndCheck(locationTrack: LocationTrack): Pair<LayoutRowVersion<LocationTrack>, LocationTrack> {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val trackWithAlignment = locationTrack.copy(alignmentVersion = dbAlignmentVersion)
        val official = locationTrackDao.save(trackWithAlignment)
        val fromDb = locationTrackDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(trackWithAlignment, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, locationTrack.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return official to fromDb
    }

    private fun publishAndCheck(rowVersion: LayoutRowVersion<LocationTrack>) {
        publishAndCheck(rowVersion, locationTrackDao, locationTrackService)
    }
}
