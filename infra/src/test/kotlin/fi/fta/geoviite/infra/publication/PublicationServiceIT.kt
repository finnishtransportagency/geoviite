package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import publish
import publishRequest
import kotlin.test.*

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationServiceIT @Autowired constructor(
    val publicationService: PublicationService,
    val alignmentDao: LayoutAlignmentDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val trackNumberService: LayoutTrackNumberService,
    val referenceLineDao: ReferenceLineDao,
    val referenceLineService: ReferenceLineService,
    val kmPostDao: LayoutKmPostDao,
    val kmPostService: LayoutKmPostService,
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val switchDao: LayoutSwitchDao,
    val switchService: LayoutSwitchService,
    val calculatedChangesService: CalculatedChangesService,
): DBTestBase() {

    @BeforeEach
    fun clearDrafts() {
        val request = publicationService.collectPublishCandidates().let {
            PublishRequestIds(
                it.trackNumbers.map(TrackNumberPublishCandidate::id),
                it.locationTracks.map(LocationTrackPublishCandidate::id),
                it.referenceLines.map(ReferenceLinePublishCandidate::id),
                it.switches.map(SwitchPublishCandidate::id),
                it.kmPosts.map(KmPostPublishCandidate::id),
            )
        }
        publicationService.revertPublishCandidates(request)
    }

    @Test
    fun publicationChangeSetIsStoredAndLoadedCorrectly() {
        val trackNumbers = listOf(
            trackNumberService.saveDraft(trackNumber(getUnusedTrackNumber())),
            trackNumberService.saveDraft(trackNumber(getUnusedTrackNumber())),
        )
        val switches = listOf(
            switchService.saveDraft(switch(111)),
            switchService.saveDraft(switch(112)),
        )
        val trackNumberId = someTrackNumber()
        val referenceLines = listOf(
            referenceLineAndAlignment(trackNumberId),
            referenceLineAndAlignment(trackNumbers[0].id, segment(Point(1.0, 1.0), Point(2.0, 2.0))),
            referenceLineAndAlignment(trackNumbers[1].id, segment(Point(5.0, 5.0), Point(6.0, 6.0)))
        ).map { (line, alignment) ->
            referenceLineService.saveDraft(line.copy(alignmentVersion = alignmentDao.insert(alignment)))
        }
        val locationTracks = listOf(
            locationTrackAndAlignment(trackNumbers[0].id),
            locationTrackAndAlignment(trackNumbers[0].id),
        ).map { (track, alignment) ->
            locationTrackService.saveDraft(track.copy(alignmentVersion = alignmentDao.insert(alignment)))
        }
        val kmPosts = listOf(
            kmPostService.saveDraft(kmPost(trackNumbers[0].id, KmNumber(1))),
            kmPostService.saveDraft(kmPost(trackNumbers[0].id, KmNumber(2)))
        )

        val beforeInsert = getDbTime()
        val publishRequestIds = PublishRequestIds(
            trackNumbers.map { it.id },
            locationTracks.map { it.id },
            referenceLines.map { it.id },
            switches.map { it.id },
            kmPosts.map { it.id }
        )

        val publicationVersions = publicationService.getValidationVersions(publishRequestIds)
        val draftCalculatedChanges = getCalculatedChangesInRequest(publicationVersions)
        val publishResult = publicationService.publishChanges(publicationVersions, draftCalculatedChanges, "Test")
        val afterInsert = getDbTime()
        assertNotNull(publishResult.publishId)
        val publish = publicationService.getPublicationDetails(publishResult.publishId!!)
        assertTrue(publish.publicationTime in beforeInsert..afterInsert)
        assertEqualsCalculatedChanges(draftCalculatedChanges, publish)
    }

    @Test
    fun `Fetching all publication candidates works`() {
        val switch = switchService.saveDraft(switch(123))
        val trackNumber = insertNewTrackNumber(getUnusedTrackNumber(), true)

        val (t, a) = locationTrackAndAlignment(trackNumber.id, segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        val track1 = locationTrackService.saveDraft(t.copy(
            alignmentVersion = alignmentDao.insert(a.copy(
                segments = listOf(a.segments[0].copy(switchId = switch.id)),
            )),
        ))
        val track2 = locationTrackService.saveDraft(locationTrack(trackNumber.id, name = "TEST-1"))

        val referenceLine = referenceLineService.saveDraft(referenceLine(trackNumber.id))
        val kmPost = kmPostService.saveDraft(kmPost(trackNumber.id, KmNumber.ZERO))

        val candidates = publicationService.collectPublishCandidates()
        assertMatches(candidates.switches, switch)
        assertMatches(candidates.locationTracks, track1, track2)
        assertMatches(candidates.trackNumbers, trackNumber)
        assertMatches(candidates.referenceLines, referenceLine)
        assertMatches(candidates.kmPosts, kmPost)
    }

    private fun <T> assertMatches(candidates: List<PublishCandidate<T>>, vararg responses: DaoResponse<T>) {
        assertEquals(responses.size, candidates.size)
        responses.forEach { response ->
            val candidate = candidates.find { c -> c.id == response.id }
            assertNotNull(candidate)
            assertEquals(response.rowVersion, candidate.rowVersion)
        }
    }

    @Test
    fun fetchSwitchTrackNumberLinksFromPublication() {
        val switch = switchService.saveDraft(switch(123))
        val trackNumberIds = listOf(
            insertOfficialTrackNumber(),
            insertOfficialTrackNumber(),
        )
        val locationTracks = trackNumberIds.map { trackNumberId ->
            val (t, a) = locationTrackAndAlignment(trackNumberId, segment(Point(0.0, 0.0), Point(1.0, 1.0)))
            locationTrackService.saveDraft(t.copy(alignmentVersion =
                alignmentDao.insert(a.copy(segments = listOf(a.segments[0].copy(switchId = switch.id))))))
        }

        val publishResult = publish(
            publicationService,
            locationTracks = locationTracks.map { it.id },
            switches = listOf(switch.id),
        )
        val publish = publicationService.getPublicationDetails(publishResult.publishId!!)
        assertEquals(trackNumberIds.sortedBy { it.intValue }, publish.switches[0].trackNumberIds.sortedBy { it.intValue })
    }

    @Test
    fun publishingNewReferenceLineWorks() {
        val (line, alignment) = referenceLineAndAlignment(someTrackNumber())
        val draftId = referenceLineService.saveDraft(line, alignment).id
        assertThrows<NoSuchEntityException> { referenceLineService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, referenceLineService.getDraft(draftId).id)

        val publishRequest = publishRequest(referenceLines = listOf(draftId))
        val versions = publicationService.getValidationVersions(publishRequest)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publishResult = publicationService.publishChanges(versions, draftCalculatedChanges, "Test")
        val publication = publicationService.getPublicationDetails(publishResult.publishId!!)

        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(1, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            referenceLineService.getOfficial(draftId)!!.startAddress,
            referenceLineService.getDraft(draftId).startAddress,
        )

        assertEqualsCalculatedChanges(draftCalculatedChanges, publication)
    }

    @Test
    fun publishingNewLocationTrackWorks() {
        val (track, alignment) = locationTrackAndAlignment(
            insertOfficialTrackNumber(),
            segment(Point(0.0, 0.0), Point(1.0, 1.0))
        )
        referenceLineService.saveDraft(referenceLine(track.trackNumberId), alignment)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.getDraft(draftId).id)

        val publishResult = publish(publicationService, locationTracks = listOf(draftId))

        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(1, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            locationTrackService.getOfficial(draftId)!!.name,
            locationTrackService.getDraft(draftId).name,
        )
    }


    @Test
    fun publishingReferenceLineChangesWorks() {
        val alignmentVersion = alignmentDao.insert(alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0))))
        val line = referenceLine(someTrackNumber(), alignmentDao.fetch(alignmentVersion), startAddress = TrackMeter("0001", 10))
            .copy(alignmentVersion = alignmentVersion)
        val officialId = referenceLineDao.insert(line).id

        val (tmpLine, tmpAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId)
        referenceLineService.saveDraft(
            tmpLine.copy(startAddress = TrackMeter("0002", 20)),
            tmpAlignment.copy(
                segments = fixSegmentStarts(
                    listOf(
                        segment(Point(1.0, 1.0), Point(2.0, 2.0)),
                        segment(Point(2.0, 2.0), Point(3.0, 3.0)),
                    )
                )
            )
        )
        assertNotEquals(
            referenceLineService.getOfficial(officialId)!!.startAddress,
            referenceLineService.getDraft(officialId).startAddress,
        )


        assertEquals(1, referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        publishAndVerify(publishRequest(referenceLines = listOf(officialId)))

        assertEquals(
            referenceLineService.getOfficial(officialId)!!.startAddress,
            referenceLineService.getDraft(officialId).startAddress,
        )
        assertEquals(2, referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(
            referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments,
            referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments,
        )
    }

    @Test
    fun publishingLocationTrackChangesWorks() {
        val alignmentVersion = alignmentDao.insert(alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0))))
        val referenceAlignment = alignment(segment(Point(0.0, 0.0), Point(4.0, 4.0)))
        val track = locationTrack(insertOfficialTrackNumber(), alignmentDao.fetch(alignmentVersion), name = "test 01")
            .copy(alignmentVersion = alignmentVersion)

        val (newDraftId, newDraftVersion) = referenceLineService.saveDraft(referenceLine(track.trackNumberId), referenceAlignment)
        referenceLineService.publish(ValidationVersion(newDraftId, newDraftVersion))

        val officialId = locationTrackDao.insert(track).id

        val (tmpTrack, tmpAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId)
        locationTrackService.saveDraft(
            tmpTrack.copy(name = AlignmentName("DRAFT test 01")),
            tmpAlignment.copy(
                segments = fixSegmentStarts(
                    listOf(
                        segment(Point(1.0, 1.0), Point(2.0, 2.0)),
                        segment(Point(2.0, 2.0), Point(3.0, 3.0)),
                    )
                )
            )
        )
        assertNotEquals(
            locationTrackService.getOfficial(officialId)!!.name,
            locationTrackService.getDraft(officialId).name,
        )
        assertEquals(1, locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        publishAndVerify(publishRequest(locationTracks = listOf(officialId)))

        assertEquals(
            locationTrackService.getOfficial(officialId)!!.name,
            locationTrackService.getDraft(officialId).name,
        )
        assertEquals(2, locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(
            locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments,
            locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments,
        )
    }

    @Test
    fun publishingNewSwitchWorks() {
        val draftId = switchService.saveDraft(switch(123)).id
        assertNull(switchService.getOfficial(draftId))
        assertEquals(draftId, switchService.getDraft(draftId).id)

        val publishResult = publish(publicationService, switches = listOf(draftId))
        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(1, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            switchService.getOfficial(draftId)!!.name,
            switchService.getDraft(draftId).name,
        )
    }

    @Test
    fun publishingSwitchChangesWorks() {
        val officialId = switchDao.insert(
            switch(55).copy(
                name = SwitchName("TST 001"),
                joints = listOf(switchJoint(1), switchJoint(3)),
            )
        ).id

        switchService.saveDraft(
            switchService.getDraft(officialId).copy(
                name = SwitchName("DRAFT TST 001"),
                joints = listOf(switchJoint(2), switchJoint(3), switchJoint(4)),
            )
        )
        assertNotEquals(
            switchService.getOfficial(officialId)!!.name,
            switchService.getDraft(officialId).name,
        )
        assertEquals(2, switchService.getOfficial(officialId)!!.joints.size)
        assertEquals(3, switchService.getDraft(officialId).joints.size)

        publishAndVerify(publishRequest(switches = listOf(officialId)))

        assertEquals(
            switchService.getOfficial(officialId)!!.name,
            switchService.getDraft(officialId).name,
        )
        assertEquals(3, switchService.getOfficial(officialId)!!.joints.size)
        assertEquals(
            switchService.getOfficial(officialId)!!.joints,
            switchService.getDraft(officialId).joints,
        )
    }

    @Test
    fun publishingNewTrackNumberWorks() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val draftId = trackNumberService.saveDraft(trackNumber).id
        assertNull(trackNumberService.getOfficial(draftId))
        assertEquals(draftId, trackNumberService.getDraft(draftId).id)

        val publishResult = publish(publicationService, trackNumbers = listOf(draftId))

        assertNotNull(publishResult.publishId)
        assertEquals(1, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            trackNumberService.getOfficial(draftId)!!.number,
            trackNumberService.getDraft(draftId).number,
        )
    }

    @Test
    fun publishingTrackNumberChangesWorks() {
        val officialId = trackNumberDao.insert(
            trackNumber().copy(
                number = getUnusedTrackNumber(),
                description = FreeText("Test 1"),
            )
        ).id

        trackNumberService.saveDraft(
            trackNumberService.getDraft(officialId).copy(
                number = getUnusedTrackNumber(),
                description = FreeText("Test 2"),
            )
        )

        assertNotEquals(
            trackNumberService.getOfficial(officialId)!!.number,
            trackNumberService.getDraft(officialId).number
        )

        assertEquals(FreeText("Test 1"), trackNumberService.getOfficial(officialId)!!.description)
        assertEquals(FreeText("Test 2"), trackNumberService.getDraft(officialId).description)

        val publishResult = publish(publicationService, trackNumbers = listOf(officialId))

        assertNotNull(publishResult.publishId)
        assertEquals(1, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            trackNumberService.getOfficial(officialId)!!.number,
            trackNumberService.getDraft(officialId).number,
        )

        assertEquals(
            trackNumberService.getOfficial(officialId)!!.description,
            trackNumberService.getDraft(officialId).description,
        )
    }


    @Test
    fun fetchingPublicationListingWorks() {
        val trackNumberId = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))))
        val (track, alignment) = locationTrackAndAlignment(trackNumberId)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.getDraft(draftId).id)

        val publicationCountBeforePublishing = publicationService.fetchPublications().size

        val publishResult = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId),
            locationTracks = listOf(draftId),
        )

        val publicationCountAfterPublishing = publicationService.fetchPublications()

        assertEquals(publicationCountBeforePublishing + 1, publicationCountAfterPublishing.size)
        assertEquals(publishResult.publishId, publicationCountAfterPublishing.last().id)
    }

    @Test
    fun publishingTrackNumberWorks() {
        verifyPublishingWorks(
            trackNumberDao,
            trackNumberService,
            { trackNumber(getUnusedTrackNumber()) },
            { orig -> draft(orig.copy(description = FreeText("${orig.description}_edit"))) },
        )
    }

    @Test
    fun publishingReferenceLineWorks() {
        val tnId = insertOfficialTrackNumber()
        verifyPublishingWorks(
            referenceLineDao,
            referenceLineService,
            { referenceLine(tnId) },
            { orig -> draft(orig.copy(startAddress = TrackMeter(12, 34))) },
        )
    }

    @Test
    fun publishingKmPostWorks() {
        val tnId = insertOfficialTrackNumber()
        verifyPublishingWorks(
            kmPostDao,
            kmPostService,
            { kmPost(tnId, KmNumber(123)) },
            { orig -> draft(orig.copy(kmNumber = KmNumber(321))) },
        )
    }

    @Test
    fun publishingLocationTrackWorks() {
        val tnId = insertOfficialTrackNumber()
        verifyPublishingWorks(
            locationTrackDao,
            locationTrackService,
            { locationTrack(tnId) },
            { orig -> draft(orig.copy(description = FreeText("${orig.description}_edit"))) },
        )
    }

    @Test
    fun publishingSwitchWorks() {
        verifyPublishingWorks(
            switchDao,
            switchService,
            { switch() },
            { orig -> draft(orig.copy(name = SwitchName("${orig.name}A"))) },
        )
    }

    @Test
    fun revertingOnlyGivenChangesWorks() {
        val switch1 = switchService.saveDraft(switch(123)).id
        val switch2 = switchService.saveDraft(switch(234)).id

        val revertResult = publicationService.revertPublishCandidates(
            PublishRequestIds(listOf(), listOf(), listOf(), listOf(switch1), listOf())
        )

        assertEquals(revertResult.switches, 1)
        assertThrows<NoSuchEntityException> { switchService.getDraft(switch1) }
        assertDoesNotThrow { switchService.getDraft(switch2) }
    }

    @Test
    fun transitiveSearchForDraftSwitchAndLocationTrackChanges() {
        val trackNumber = insertOfficialTrackNumber()

        val switch1 = switchService.saveDraft(switch(123)).id
        val switch2 = switchService.saveDraft(switch(234)).id
        val switch3 = createOfficialAndDraftSwitch(345)
        val distantSwitch = createOfficialAndDraftSwitch(456)

        val track1BetweenSwitch1and2 = locationTrackAndAlignment(
            trackNumber,
            segment(Point(0.0, 0.0), Point(1.0, 1.0)).copy(
                startJointNumber = JointNumber(1), switchId = switch1
            ),
            segment(Point(1.0, 1.0), Point(2.0, 2.0)).copy(
                endJointNumber = JointNumber(1), switchId = switch2
            ),
        )
        val locationTrack1Id =
            locationTrackService.saveDraft(track1BetweenSwitch1and2.first, track1BetweenSwitch1and2.second).id

        val track2BetweenSwitch2and3 = locationTrackAndAlignment(
            trackNumber,
            segment(Point(2.0, 2.0), Point(3.0, 3.0)).copy(
                endJointNumber = JointNumber(1), switchId = switch3
            )
        )
        val locationTrack2Id = locationTrackService.saveDraft(
            track2BetweenSwitch2and3.first.copy(
                topologyStartSwitch =
                TopologyLocationTrackSwitch(switch2, JointNumber(1))
            ), track2BetweenSwitch2and3.second
        ).id

        val officialTrackBetweenSwitch3AndDistantSwitch = locationTrackAndAlignment(
            trackNumber,
            segment(Point(3.0, 3.0), Point(4.0, 4.0)).copy(
                startJointNumber = JointNumber(1), switchId = switch3
            ),
            segment(Point(4.0, 4.0), Point(5.0, 5.0)).copy(
                endJointNumber = JointNumber(1), switchId = distantSwitch
            )
        )
        locationTrackDao.insert(
            officialTrackBetweenSwitch3AndDistantSwitch.first.copy(
                alignmentVersion =
                alignmentDao.insert(officialTrackBetweenSwitch3AndDistantSwitch.second)
            )
        ).id
        val dependencies = publicationService.getRevertRequestDependencies(
            publishRequest(switches = listOf(switch1))
        )
        assertEquals(
            publishRequest(
                locationTracks = listOf(locationTrack1Id, locationTrack2Id),
                switches = listOf(switch1, switch2, switch3),
            ), dependencies
        )
    }

    @Test
    fun trackNumberAndReferenceLineChangesDependOnEachOther() {
        val trackNumber = insertDraftTrackNumber()
        val referenceLine = referenceLineService.saveDraft(referenceLine(trackNumber)).id
        val publishBoth = publishRequest(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine))
        assertEquals(
            publishBoth,
            publicationService.getRevertRequestDependencies(publishRequest(trackNumbers = listOf(trackNumber)))
        )
        assertEquals(
            publishBoth,
            publicationService.getRevertRequestDependencies(publishRequest(referenceLines = listOf(referenceLine)))
        )
    }

    @Test
    fun `should sort publications by publication time in descending order`() {
        val trackNumber1Id = insertDraftTrackNumber()
        val trackNumber2Id = insertDraftTrackNumber()
        val publish1Result = publishRequest(trackNumbers = listOf(trackNumber1Id, trackNumber2Id)).let { r ->
            val versions = publicationService.getValidationVersions(r)
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "")
        }

        assertEquals(2, publish1Result.trackNumbers)

        val trackNumber1 = trackNumberService.getOfficial(trackNumber1Id)
        val trackNumber2 = trackNumberService.getOfficial(trackNumber2Id)
        assertNotNull(trackNumber1)
        assertNotNull(trackNumber2)

        val newTrackNumber1TrackNumber = "${trackNumber1.number} ZZZ"

        trackNumberService.saveDraft(trackNumber1.copy(number = TrackNumber(newTrackNumber1TrackNumber)))
        val publish2Result = publishRequest(trackNumbers = listOf(trackNumber1Id)).let { r ->
            val versions = publicationService.getValidationVersions(r)
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "")
        }

        assertEquals(1, publish2Result.trackNumbers)
    }

    @Test
    fun `Validating official location track should work`() {
        val trackNumber = insertOfficialTrackNumber()
        val (locationTrack, alignment) = locationTrackAndAlignment(
            trackNumber, segment(Point(4.0, 4.0), Point(5.0, 5.0))
        )
        val locationTrackId = locationTrackDao.insert(locationTrack.copy( alignmentVersion =
        alignmentDao.insert(alignment)
        ))

        val validation = publicationService.validateLocationTrack(locationTrackId.id, OFFICIAL)
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official track number should work`() {
        val trackNumber = insertOfficialTrackNumber()

        val validation = publicationService.validateTrackNumberAndReferenceLine(trackNumber, OFFICIAL)
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official switch should work`() {
        val switchId = switchDao.insert(switch(123)).id

        val validation = publicationService.validateSwitch(switchId, OFFICIAL)
        assertEquals(validation.errors.size, 2)
    }

    @Test
    fun `Validating official km post should work`() {
        val kmPostId = kmPostDao.insert(kmPost(insertOfficialTrackNumber(), km = KmNumber.ZERO)).id

        val validation = publicationService.validateKmPost(kmPostId, OFFICIAL)
        assertEquals(validation.errors.size, 1)
    }

    fun createOfficialAndDraftSwitch(seed: Int): IntId<TrackLayoutSwitch> {
        val officialVersion = switchDao.insert(switch(seed)).rowVersion
        return switchService.saveDraft(switchDao.fetch(officialVersion).let { official ->
            official.copy(name = SwitchName("${official.name}_D"))
        }).id
    }

    private fun someTrackNumber() = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id

    private fun getCalculatedChangesInRequest(versions: ValidationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChanges(versions)

    private fun publishAndVerify(request: PublishRequestIds): PublishResult {
        val versions = publicationService.getValidationVersions(request)
        verifyVersions(request, versions)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publishResult = publicationService.publishChanges(versions, draftCalculatedChanges, "Test")
        val publicationDetails = publicationService.getPublicationDetails(publishResult.publishId!!)
        assertNotNull(publishResult.publishId)
        verifyPublished(versions.trackNumbers, trackNumberDao) { draft, published ->
            assertMatches(draft.copy(draft = null), published)
        }
        verifyPublished(versions.referenceLines, referenceLineDao) { draft, published ->
            assertMatches(draft.copy(draft = null), published)
        }
        verifyPublished(versions.kmPosts, kmPostDao) { draft, published ->
            assertMatches(draft.copy(draft = null), published)
        }
        verifyPublished(versions.locationTracks, locationTrackDao) { draft, published ->
            assertMatches(draft.copy(draft = null), published)
        }
        verifyPublished(versions.switches, switchDao) { draft, published ->
            assertMatches(draft.copy(draft = null), published)
        }

        assertEqualsCalculatedChanges(draftCalculatedChanges, publicationDetails)
        return publishResult
    }

    @Test
    fun `Track number diff finds all changed fields`() {
        val address = TrackMeter(0, 0)
        val trackNumber = trackNumberService.getDraft(trackNumberService.insert(
            TrackNumberSaveRequest(
                getUnusedTrackNumber(),
                FreeText("TEST"),
                LayoutState.IN_USE,
                address,
            )
        ))
        val id = trackNumberService.update(trackNumber.id as IntId, TrackNumberSaveRequest(
            number = TrackNumber(trackNumber.number.value + " T"),
            description = trackNumber.description + "_TEST",
            startAddress = TrackMeter(0,0),
            state = LayoutState.NOT_IN_USE,
        ))
        val updatedTrackNumber = trackNumberService.getDraft(id)

        val diff = publicationService.diffTrackNumber(updatedTrackNumber, trackNumber)
        assertEquals(3, diff.size)
        assertEquals("track-number", diff[0].propKey.key)
        assertEquals("state", diff[1].propKey.key)
        assertEquals("description", diff[2].propKey.key)
    }

    @Test
    fun `Diffing track number with itself returns empty list`() {
        val address = TrackMeter(0, 0)
        val trackNumber = trackNumberService.getDraft(trackNumberService.insert(
            TrackNumberSaveRequest(
                getUnusedTrackNumber(),
                FreeText("TEST"),
                LayoutState.IN_USE,
                address,
            )
        ))
        val diff = publicationService.diffTrackNumber(trackNumber, trackNumber)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `Changing specific Track Number field returns only that field`() {
        val address = TrackMeter(0, 0)
        val trackNumber = trackNumberService.getDraft(trackNumberService.insert(
            TrackNumberSaveRequest(
                getUnusedTrackNumber(),
                FreeText("TEST"),
                LayoutState.IN_USE,
                address,
            )
        ))
        val id = trackNumberService.update(trackNumber.id as IntId, TrackNumberSaveRequest(
            number = trackNumber.number,
            description = FreeText("TEST2"),
            startAddress = address,
            state = trackNumber.state,
        ))
        val updatedTrackNumber = trackNumberService.getDraft(id)

        val diff = publicationService.diffTrackNumber(updatedTrackNumber, trackNumber)
        assertEquals(1, diff.size)
        assertEquals("description", diff[0].propKey.key)
        assertEquals(trackNumber.description.toString(), diff[0].oldValue)
        assertEquals(updatedTrackNumber.description.toString(), diff[0].newValue)
    }

    @Test
    fun `Location track diff finds all changed fields`() {
        val duplicate = locationTrackService.get(
            locationTrackService.insert(
                LocationTrackSaveRequest(
                    AlignmentName("TEST duplicate"),
                    FreeText("Test"),
                    LocationTrackType.MAIN,
                    LayoutState.IN_USE,
                    getUnusedTrackNumberId(),
                    null,
                    TopologicalConnectivityType.NONE
                )
            ).rowVersion
        )

        val duplicate2 = locationTrackService.get(
            locationTrackService.insert(
                LocationTrackSaveRequest(
                    AlignmentName("TEST duplicate 2"),
                    FreeText("Test"),
                    LocationTrackType.MAIN,
                    LayoutState.IN_USE,
                    getUnusedTrackNumberId(),
                    null,
                    TopologicalConnectivityType.NONE
                )
            ).rowVersion
        )

        val locationTrack = locationTrackService.get(
            locationTrackService.insert(
                LocationTrackSaveRequest(
                    AlignmentName("TEST"),
                    FreeText("Test"),
                    LocationTrackType.MAIN,
                    LayoutState.IN_USE,
                    getUnusedTrackNumberId(),
                    duplicate.id as IntId<LocationTrack>,
                    TopologicalConnectivityType.NONE
                )
            ).rowVersion
        )

        val updatedLocationTrack = locationTrackService.get(
            locationTrackService.update(
                locationTrack.id as IntId, LocationTrackSaveRequest(
                    name = AlignmentName("TEST2"),
                    description = FreeText("Test2"),
                    type = LocationTrackType.SIDE,
                    state = LayoutState.NOT_IN_USE,
                    trackNumberId = locationTrack.trackNumberId,
                    duplicate2.id as IntId<LocationTrack>,
                    topologicalConnectivity = TopologicalConnectivityType.START_AND_END
                )
            ).rowVersion
        )
        publish(publicationService, locationTracks = listOf(locationTrack.id as IntId<LocationTrack>, updatedLocationTrack.id as IntId<LocationTrack>, duplicate.id as IntId<LocationTrack>, duplicate2.id as IntId<LocationTrack>))

        val diff = publicationService.diffLocationTrack(updatedLocationTrack, locationTrack, emptySet())
        assertEquals(5, diff.size)
        assertEquals("location-track", diff[0].propKey.key)
        assertEquals("state", diff[1].propKey.key)
        assertEquals("location-track-type", diff[2].propKey.key)
        assertEquals("description", diff[3].propKey.key)
        assertEquals("duplicate-of", diff[4].propKey.key)
    }

    @Test
    fun `Diffing location track with itself returns empty list`() {
        val locationTrack = locationTrackService.get(
            locationTrackService.insert(
                LocationTrackSaveRequest(
                    AlignmentName("TEST"),
                    FreeText("Test"),
                    LocationTrackType.MAIN,
                    LayoutState.IN_USE,
                    getUnusedTrackNumberId(),
                    null,
                    TopologicalConnectivityType.NONE
                )
            ).rowVersion
        )

        val diff = publicationService.diffLocationTrack(locationTrack, locationTrack, emptySet())
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `Changing specific Location Track field returns only that field`() {
        val saveReq = LocationTrackSaveRequest(
            AlignmentName("TEST"),
            FreeText("Test"),
            LocationTrackType.MAIN,
            LayoutState.IN_USE,
            getUnusedTrackNumberId(),
            null,
            TopologicalConnectivityType.NONE
        )

        val locationTrack = locationTrackService.get(
            locationTrackService.insert(saveReq).rowVersion
        )

        val updatedLocationTrack = locationTrackService.get(
            locationTrackService.update(locationTrack.id as IntId,
                saveReq.copy(description = FreeText("TEST2"))
            ).rowVersion
        )

        val diff = publicationService.diffLocationTrack(updatedLocationTrack, locationTrack, emptySet())
        assertEquals(1, diff.size)
        assertEquals("description", diff[0].propKey.key)
        assertEquals(locationTrack.description.toString(), diff[0].oldValue)
        assertEquals(updatedLocationTrack.description.toString(), diff[0].newValue)
    }

    @Test
    fun `KM Post diff finds all changed fields`() {
        val trackNumberSaveReq = TrackNumberSaveRequest(
            getUnusedTrackNumber(),
            FreeText("TEST"),
            LayoutState.IN_USE,
            TrackMeter(0, 0),
        )
        val trackNumber = trackNumberService.getDraft(
            trackNumberService.insert(
                trackNumberSaveReq
            )
        )
        val trackNumber2 = trackNumberService.getDraft(
            trackNumberService.insert(
                trackNumberSaveReq.copy(getUnusedTrackNumber(), FreeText("TEST 2"))
            )
        )

        val kmPost = kmPostService.getDraft(
            kmPostService.insertKmPost(
                TrackLayoutKmPostSaveRequest(
                    KmNumber(0),
                    LayoutState.IN_USE,
                    trackNumber.id as IntId,
                )
            )
        )
        val updatedKmPost = kmPostService.getDraft(
            kmPostService.updateKmPost(
                kmPost.id as IntId,
                TrackLayoutKmPostSaveRequest(
                    KmNumber(1),
                    LayoutState.NOT_IN_USE,
                    trackNumber2.id as IntId,
                )
            )
        )

        val diff = publicationService.diffKmPost(updatedKmPost, kmPost)
        assertEquals(2, diff.size)
        // assertEquals("track-number", diff[0].propKey) TODO Enable when track number switching works
        assertEquals("km-post", diff[0].propKey.key)
        assertEquals("state", diff[1].propKey.key)
    }

    @Test
    fun `Diffing km post with itself returns empty list`() {
        val kmPost = kmPostService.getDraft(
            kmPostService.insertKmPost(
                TrackLayoutKmPostSaveRequest(
                    KmNumber(0),
                    LayoutState.IN_USE,
                    insertOfficialTrackNumber(),
                )
            )
        )

        val diff = publicationService.diffKmPost(kmPost, kmPost)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `Changing specific KM Post field returns only that field`() {
        val saveReq = TrackLayoutKmPostSaveRequest(
            KmNumber(0),
            LayoutState.IN_USE,
            insertOfficialTrackNumber(),
        )

        val kmPost = kmPostService.getDraft(
            kmPostService.insertKmPost(saveReq)
        )
        val updatedKmPost = kmPostService.getDraft(
            kmPostService.updateKmPost(
                kmPost.id as IntId,
                    saveReq.copy(kmNumber = KmNumber(1))
                )
        )

        val diff = publicationService.diffKmPost(updatedKmPost, kmPost)
        assertEquals(1, diff.size)
        assertEquals("km-post", diff[0].propKey.key)
    }

    @Test
    fun `Switch diff finds all changed fields`() {
        val trackNumberSaveReq = TrackNumberSaveRequest(
            getUnusedTrackNumber(),
            FreeText("TEST"),
            LayoutState.IN_USE,
            TrackMeter(0, 0),
        )
        trackNumberService.insert(trackNumberSaveReq)
        trackNumberService.insert(
            trackNumberSaveReq.copy(getUnusedTrackNumber(), FreeText("TEST 2"))
        )

        val switch = switchService.getDraft(
            switchService.insertSwitch(
                TrackLayoutSwitchSaveRequest(
                    SwitchName("TEST"),
                    IntId(1),
                    LayoutStateCategory.EXISTING,
                    IntId(1),
                    false,
                )
            )
        )
        val updatedSwitch = switchService.getDraft(
            switchService.updateSwitch(
                switch.id as IntId,
                TrackLayoutSwitchSaveRequest(
                    SwitchName("TEST 2"),
                    IntId(2),
                    LayoutStateCategory.FUTURE_EXISTING,
                    IntId(2),
                    true,
                )
            )
        )

        val diff = publicationService.diffSwitch(updatedSwitch, switch)
        assertEquals(5, diff.size)
        assertEquals("switch", diff[0].propKey.key)
        assertEquals("state-category", diff[1].propKey.key)
        assertEquals("switch-type", diff[2].propKey.key)
        assertEquals("trap-point", diff[3].propKey.key)
        assertEquals("owner", diff[4].propKey.key)
    }

    @Test
    fun `Diffing switch with itself returns empty list`() {
        val switch = switchService.getDraft(
            switchService.insertSwitch(
                TrackLayoutSwitchSaveRequest(
                    SwitchName("TEST"),
                    IntId(1),
                    LayoutStateCategory.EXISTING,
                    IntId(1),
                    false,
                )
            )
        )

        val diff = publicationService.diffSwitch(switch, switch)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `Changing specific switch field returns only that field`() {
        val saveReq = TrackLayoutSwitchSaveRequest(
            SwitchName("TEST"),
            IntId(1),
            LayoutStateCategory.EXISTING,
            IntId(1),
            false,
        )

        val switch = switchService.getDraft(
            switchService.insertSwitch(saveReq)
        )
        val updatedSwitch = switchService.getDraft(
            switchService.updateSwitch(
                switch.id as IntId,
                saveReq.copy(name = SwitchName("TEST 2"))
            )
        )

        val diff = publicationService.diffSwitch(updatedSwitch, switch)
        assertEquals(1, diff.size)
        assertEquals("switch", diff[0].propKey.key)
    }
}

private fun assertEqualsCalculatedChanges(
    calculatedChanges: CalculatedChanges,
    publicationDetails: PublicationDetails,
) {
    fun locationTrackEquals(
        calculatedLocationTracks: List<LocationTrackChange>,
        publishedLocationTracks: List<PublishedLocationTrack>,
    ) {
        calculatedLocationTracks.forEach { calculatedTrack ->
            val locationTrack = publishedLocationTracks.find { it.version.id == calculatedTrack.locationTrackId }
            assertNotNull(locationTrack)
            assertEquals(locationTrack.changedKmNumbers, calculatedTrack.changedKmNumbers)
        }
    }

    fun trackNumberEquals(
        calculatedTrackNumbers: List<TrackNumberChange>,
        publishedTrackNumbers: List<PublishedTrackNumber>,
    ) {
        calculatedTrackNumbers.forEach { calculatedTrackNumber ->
            val trackNumber = publishedTrackNumbers.find { it.version.id == calculatedTrackNumber.trackNumberId }
            assertNotNull(trackNumber)
            assertEquals(trackNumber.changedKmNumbers, calculatedTrackNumber.changedKmNumbers)
        }
    }

    calculatedChanges.directChanges.kmPostChanges.forEach { calculatedKmPostId ->
        assertTrue(publicationDetails.kmPosts.any { it.version.id == calculatedKmPostId })
    }

    calculatedChanges.directChanges.referenceLineChanges.forEach { calculatedReferenceLineId ->
        assertTrue(publicationDetails.referenceLines.any { it.version.id == calculatedReferenceLineId })
    }


    trackNumberEquals(calculatedChanges.directChanges.trackNumberChanges, publicationDetails.trackNumbers)
    locationTrackEquals(calculatedChanges.directChanges.locationTrackChanges, publicationDetails.locationTracks)

    calculatedChanges.directChanges.switchChanges.forEach { calculatedSwitch ->
        val switch = publicationDetails.switches.find { it.version.id == calculatedSwitch.switchId }
        assertNotNull(switch)
        assertEquals(switch.changedJoints, calculatedSwitch.changedJoints)
    }


    trackNumberEquals(
        calculatedChanges.indirectChanges.trackNumberChanges,
        publicationDetails.indirectChanges.trackNumbers
    )

    locationTrackEquals(
        calculatedChanges.indirectChanges.locationTrackChanges,
        publicationDetails.indirectChanges.locationTracks
    )

    calculatedChanges.indirectChanges.switchChanges.forEach { calculatedSwitch ->
        val switch = publicationDetails.indirectChanges.switches.find { s ->
            s.version.id == calculatedSwitch.switchId
        }
        assertNotNull(switch)
        assertEquals(switch.changedJoints, calculatedSwitch.changedJoints)
    }
}

private fun verifyVersions(publishRequestIds: PublishRequestIds, validationVersions: ValidationVersions) {
    verifyVersions(publishRequestIds.trackNumbers, validationVersions.trackNumbers)
    verifyVersions(publishRequestIds.referenceLines, validationVersions.referenceLines)
    verifyVersions(publishRequestIds.kmPosts, validationVersions.kmPosts)
    verifyVersions(publishRequestIds.locationTracks, validationVersions.locationTracks)
    verifyVersions(publishRequestIds.switches, validationVersions.switches)
}

private fun <T : Draftable<T>> verifyVersions(ids: List<IntId<T>>, versions: List<ValidationVersion<T>>) {
    assertEquals(ids.size, versions.size)
    ids.forEach { id -> assertTrue(versions.any { v -> v.officialId == id }) }
}


private fun <T : Draftable<T>, S : DraftableDaoBase<T>> verifyPublishingWorks(
    dao: S,
    service: DraftableObjectService<T, S>,
    create: () -> T,
    mutate: (orig: T) -> T,
) {
    // First id remains official
    val (officialId, draftVersion1) = service.saveDraft(create())
    assertEquals(1, draftVersion1.version)

    val officialVersion1 = publishAndCheck(draftVersion1, dao, service).first
    assertEquals(officialId, officialVersion1.id)
    assertEquals(2, officialVersion1.version)

    val (draftOfficialId2, draftVersion2) = service.saveDraft(mutate(dao.fetch(officialVersion1)))
    assertEquals(officialId, draftOfficialId2)
    assertNotEquals(officialId, draftVersion2.id)
    assertEquals(1, draftVersion2.version)

    val officialVersion2 = publishAndCheck(draftVersion2, dao, service).first
    assertEquals(officialId, officialVersion2.id)
    assertEquals(3, officialVersion2.version)
}

fun <T : Draftable<T>, S : DraftableDaoBase<T>> publishAndCheck(
    rowVersion: RowVersion<T>,
    dao: S,
    service: DraftableObjectService<T, S>,
): Pair<RowVersion<T>, T> {
    val draft = dao.fetch(rowVersion)
    val id = draft.id

    assertTrue(id is IntId)
    assertNotEquals(rowVersion, dao.fetchOfficialVersion(id))
    assertEquals(rowVersion, dao.fetchDraftVersion(id))
    assertNotNull(draft.draft)
    assertEquals(DataType.STORED, draft.dataType)

    val (publishedId, publishedVersion) = service.publish(ValidationVersion(id, rowVersion))
    assertEquals(id, publishedId)
    assertEquals(publishedVersion, dao.fetchOfficialVersionOrThrow(id))
    assertEquals(publishedVersion, dao.fetchDraftVersion(id))
    assertEquals(VersionPair(publishedVersion, null), dao.fetchVersionPair(id))

    val publishedItem = dao.fetch(publishedVersion)
    assertNull(publishedItem.draft)
    assertEquals(id, publishedVersion.id)

    return publishedVersion to publishedItem
}

fun <T : Draftable<T>, S : DraftableDaoBase<T>> verifyPublished(
    validationVersions: List<ValidationVersion<T>>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) = validationVersions.forEach { v -> verifyPublished(v, dao, checkMatch) }

fun <T : Draftable<T>, S : DraftableDaoBase<T>> verifyPublished(
    validationVersion: ValidationVersion<T>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) {
    val currentOfficialVersion = dao.fetchOfficialVersionOrThrow(validationVersion.officialId)
    val currentDraftVersion = dao.fetchDraftVersionOrThrow(validationVersion.officialId)
    assertEquals(currentDraftVersion.id, currentOfficialVersion.id)
    assertEquals(currentOfficialVersion, currentDraftVersion)
    checkMatch(dao.fetch(validationVersion.validatedAssetVersion), dao.fetch(currentOfficialVersion))
}
