package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.error.DuplicateLocationTrackNameInPublicationException
import fi.fta.geoviite.infra.error.DuplicateNameInPublicationException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.*
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.LocalizationKey
import fi.fta.geoviite.infra.util.SortOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import publicationRequest
import publish
import java.math.BigDecimal
import kotlin.math.absoluteValue
import kotlin.test.*

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationServiceIT @Autowired constructor(
    val publicationService: PublicationService,
    val publicationDao: PublicationDao,
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
    val localizationService: LocalizationService,
    val switchStructureDao: SwitchStructureDao,
    val splitDao: SplitDao,
    val splitService: SplitService,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        deleteFromTables("publication", "location_track", "location_track_geometry_change_summary")
        deleteFromTables("layout", "switch_joint", "switch", "location_track", "track_number", "reference_line")
        val request = publicationService.collectPublicationCandidates().let {
            PublicationRequestIds(
                it.trackNumbers.map(TrackNumberPublicationCandidate::id),
                it.locationTracks.map(LocationTrackPublicationCandidate::id),
                it.referenceLines.map(ReferenceLinePublicationCandidate::id),
                it.switches.map(SwitchPublicationCandidate::id),
                it.kmPosts.map(KmPostPublicationCandidate::id),
            )
        }
        publicationService.revertPublicationCandidates(request)
    }

    fun clearPublicationTables() {
        deleteFromTables(
            "publication",
            "km_post",
            "location_track",
            "location_track_km",
            "publication",
            "reference_line",
            "switch",
            "switch_joint",
            "switch_location_tracks",
            "track_number",
            "track_number_km"
        )
    }

    @Test
    fun publicationChangeSetIsStoredAndLoadedCorrectly() {
        val trackNumbers = listOf(
            trackNumberService.saveDraft(trackNumber(getUnusedTrackNumber(), draft = true)),
            trackNumberService.saveDraft(trackNumber(getUnusedTrackNumber(), draft = true)),
        )
        val switches = listOf(
            switchService.saveDraft(switch(111, draft = true)),
            switchService.saveDraft(switch(112, draft = true)),
        )
        val trackNumberId = getUnusedTrackNumberId()
        val referenceLines = listOf(
            referenceLineAndAlignment(trackNumberId, draft = true),
            referenceLineAndAlignment(trackNumbers[0].id, segment(Point(1.0, 1.0), Point(2.0, 2.0)), draft = true),
            referenceLineAndAlignment(trackNumbers[1].id, segment(Point(5.0, 5.0), Point(6.0, 6.0)), draft = true),
        ).map { (line, alignment) ->
            referenceLineService.saveDraft(line.copy(alignmentVersion = alignmentDao.insert(alignment)))
        }
        val locationTracks = listOf(
            locationTrackAndAlignment(trackNumbers[0].id, draft = true),
            locationTrackAndAlignment(trackNumbers[0].id, draft = true),
        ).map { (track, alignment) ->
            locationTrackService.saveDraft(track.copy(alignmentVersion = alignmentDao.insert(alignment)))
        }
        val kmPosts = listOf(
            kmPostService.saveDraft(kmPost(trackNumbers[0].id, KmNumber(1), draft = true)),
            kmPostService.saveDraft(kmPost(trackNumbers[0].id, KmNumber(2), draft = true)),
        )

        val beforeInsert = getDbTime()
        val publicationRequestIds = PublicationRequestIds(
            trackNumbers.map { it.id },
            locationTracks.map { it.id },
            referenceLines.map { it.id },
            switches.map { it.id },
            kmPosts.map { it.id },
        )

        val publicationVersions = publicationService.getValidationVersions(publicationRequestIds)
        val draftCalculatedChanges = getCalculatedChangesInRequest(publicationVersions)
        val publicationResult = publicationService.publishChanges(publicationVersions, draftCalculatedChanges, "Test")
        val afterInsert = getDbTime()
        assertNotNull(publicationResult.publicationId)
        val publish = publicationService.getPublicationDetails(publicationResult.publicationId!!)
        assertTrue(publish.publicationTime in beforeInsert..afterInsert)
        assertEqualsCalculatedChanges(draftCalculatedChanges, publish)
    }

    @Test
    fun `Fetching all publication candidates works`() {
        val switch = switchService.saveDraft(switch(123, draft = true))
        val trackNumber = insertNewTrackNumber(getUnusedTrackNumber(), true)

        val (t, a) = locationTrackAndAlignment(trackNumber.id, segment(Point(0.0, 0.0), Point(1.0, 1.0)), draft = true)
        val track1 = locationTrackService.saveDraft(
            t.copy(
                alignmentVersion = alignmentDao.insert(
                    a.copy(
                        segments = listOf(a.segments[0].copy(switchId = switch.id)),
                    )
                ),
            )
        )
        val track2 = locationTrackService.saveDraft(locationTrack(trackNumber.id, name = "TEST-1", draft = true))

        val referenceLine = referenceLineService.saveDraft(referenceLine(trackNumber.id, draft = true))
        val kmPost = kmPostService.saveDraft(kmPost(trackNumber.id, KmNumber.ZERO, draft = true))

        val candidates = publicationService.collectPublicationCandidates()
        assertMatches(candidates.switches, switch)
        assertMatches(candidates.locationTracks, track1, track2)
        assertMatches(candidates.trackNumbers, trackNumber)
        assertMatches(candidates.referenceLines, referenceLine)
        assertMatches(candidates.kmPosts, kmPost)
    }

    private fun <T> assertMatches(candidates: List<PublicationCandidate<T>>, vararg responses: DaoResponse<T>) {
        assertEquals(responses.size, candidates.size)
        responses.forEach { response ->
            val candidate = candidates.find { c -> c.id == response.id }
            assertNotNull(candidate)
            assertEquals(response.rowVersion, candidate.rowVersion)
        }
    }

    @Test
    fun fetchSwitchTrackNumberLinksFromPublication() {
        val switch = switchService.saveDraft(switch(123, draft = true))
        val trackNumberIds = listOf(
            insertOfficialTrackNumber(),
            insertOfficialTrackNumber(),
        )
        val locationTracks = trackNumberIds.map { trackNumberId ->
            val (t, a) = locationTrackAndAlignment(
                trackNumberId,
                segment(Point(0.0, 0.0), Point(1.0, 1.0)),
                draft = true,
            )
            locationTrackService.saveDraft(
                t.copy(
                    alignmentVersion = alignmentDao.insert(
                        a.copy(segments = listOf(a.segments[0].copy(switchId = switch.id))),
                    ),
                ),
            )
        }

        val publicationResult = publish(
            publicationService,
            locationTracks = locationTracks.map { it.id },
            switches = listOf(switch.id),
        )
        val publish = publicationService.getPublicationDetails(publicationResult.publicationId!!)
        assertEquals(
            trackNumberIds.sortedBy { it.intValue },
            publish.switches[0].trackNumberIds.sortedBy { it.intValue },
        )
    }

    @Test
    fun publishingNewReferenceLineWorks() {
        val (line, alignment) = referenceLineAndAlignment(getUnusedTrackNumberId(), draft = true)
        val draftId = referenceLineService.saveDraft(line, alignment).id
        assertThrows<NoSuchEntityException> { referenceLineService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, referenceLineService.getOrThrow(DRAFT, draftId).id)

        val publicationRequest = publicationRequest(referenceLines = listOf(draftId))
        val versions = publicationService.getValidationVersions(publicationRequest)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publicationResult = publicationService.publishChanges(versions, draftCalculatedChanges, "Test")
        val publication = publicationService.getPublicationDetails(publicationResult.publicationId!!)

        assertNotNull(publicationResult.publicationId)
        assertEquals(0, publicationResult.trackNumbers)
        assertEquals(1, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            referenceLineService.get(OFFICIAL, draftId)!!.startAddress,
            referenceLineService.get(DRAFT, draftId)!!.startAddress,
        )

        assertEqualsCalculatedChanges(draftCalculatedChanges, publication)
    }

    @Test
    fun `Publishing reference line change without track number figures out the operation correctly`() {
        val (line, alignment) = referenceLineAndAlignment(getUnusedTrackNumberId(), draft = true)
        val draftId = referenceLineService.saveDraft(line, alignment).id
        assertThrows<NoSuchEntityException> { referenceLineService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, referenceLineService.get(DRAFT, draftId)!!.id)

        val publicationRequest = publicationRequest(referenceLines = listOf(draftId))
        val versions = publicationService.getValidationVersions(publicationRequest)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publication = publicationService.publishChanges(versions, draftCalculatedChanges, "Test")
        val publicationDetails = publicationService.getPublicationDetails(publication.publicationId!!)
        assertEquals(1, publicationDetails.referenceLines.size)
        assertEquals(Operation.CREATE, publicationDetails.referenceLines[0].operation)
        val publishedReferenceLine = referenceLineService.get(OFFICIAL, draftId)!!

        val updateResponse = referenceLineService.updateTrackNumberReferenceLine(
            publishedReferenceLine.trackNumberId,
            publishedReferenceLine.startAddress.copy(
                meters = publishedReferenceLine.startAddress.meters.add(BigDecimal.ONE),
            ),
        )
        val pubReq2 = publicationRequest(referenceLines = listOf(updateResponse!!.id))
        val versions2 = publicationService.getValidationVersions(pubReq2)
        val draftCalculatedChanges2 = getCalculatedChangesInRequest(versions2)
        val publication2 = publicationService.publishChanges(versions2, draftCalculatedChanges2, "Test 2")
        val publicationDetails2 = publicationService.getPublicationDetails(publication2.publicationId!!)
        assertEquals(1, publicationDetails2.referenceLines.size)
        assertEquals(Operation.MODIFY, publicationDetails2.referenceLines[0].operation)
    }

    @Test
    fun publishingNewLocationTrackWorks() {
        val (track, alignment) = locationTrackAndAlignment(
            insertOfficialTrackNumber(),
            segment(Point(0.0, 0.0), Point(1.0, 1.0)),
            draft = true,
        )
        referenceLineService.saveDraft(referenceLine(track.trackNumberId, draft = true), alignment)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.get(DRAFT, draftId)!!.id)

        val publicationResult = publish(publicationService, locationTracks = listOf(draftId))

        assertNotNull(publicationResult.publicationId)
        assertEquals(0, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(1, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            locationTrackService.get(OFFICIAL, draftId)!!.name,
            locationTrackService.get(DRAFT, draftId)!!.name,
        )
    }

    @Test
    fun publishingReferenceLineChangesWorks() {
        val alignmentVersion = alignmentDao.insert(alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0))))
        val line = referenceLine(
            getUnusedTrackNumberId(),
            alignmentDao.fetch(alignmentVersion),
            startAddress = TrackMeter("0001", 10),
            draft = false,
        ).copy(alignmentVersion = alignmentVersion)
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
                ),
            ),
        )
        assertNotEquals(
            referenceLineService.get(OFFICIAL, officialId)!!.startAddress,
            referenceLineService.get(DRAFT, officialId)!!.startAddress,
        )

        assertEquals(1, referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        publishAndVerify(publicationRequest(referenceLines = listOf(officialId)))

        assertEquals(
            referenceLineService.get(OFFICIAL, officialId)!!.startAddress,
            referenceLineService.get(DRAFT, officialId)!!.startAddress,
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
        val track = locationTrack(
            trackNumberId = insertOfficialTrackNumber(),
            alignmentDao.fetch(alignmentVersion),
            name = "test 01",
            alignmentVersion = alignmentVersion,
            draft = false,
        )

        val (newDraftId, newDraftVersion) = referenceLineService.saveDraft(
            referenceLine(track.trackNumberId, draft = true),
            referenceAlignment,
        )
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
                ),
            ),
        )
        assertNotEquals(
            locationTrackService.get(OFFICIAL, officialId)!!.name,
            locationTrackService.get(DRAFT, officialId)!!.name,
        )
        assertEquals(1, locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        publishAndVerify(publicationRequest(locationTracks = listOf(officialId)))

        assertEquals(
            locationTrackService.get(OFFICIAL, officialId)!!.name,
            locationTrackService.get(DRAFT, officialId)!!.name,
        )
        assertEquals(2, locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(
            locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments,
            locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments,
        )
    }

    @Test
    fun publishingNewSwitchWorks() {
        val draftId = switchService.saveDraft(switch(123, draft = true)).id
        assertNull(switchService.get(OFFICIAL, draftId))
        assertEquals(draftId, switchService.get(DRAFT, draftId)!!.id)

        val publicationResult = publish(publicationService, switches = listOf(draftId))
        assertNotNull(publicationResult.publicationId)
        assertEquals(0, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(1, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            switchService.get(OFFICIAL, draftId)!!.name,
            switchService.get(DRAFT, draftId)!!.name,
        )
    }

    @Test
    fun publishingSwitchChangesWorks() {
        val officialId = switchDao.insert(
            switch(55, draft = false).copy(
                name = SwitchName("TST 001"),
                joints = listOf(switchJoint(1), switchJoint(3)),
            )
        ).id

        switchService.saveDraft(
            switchService.get(DRAFT, officialId)!!.copy(
                name = SwitchName("DRAFT TST 001"),
                joints = listOf(switchJoint(2), switchJoint(3), switchJoint(4)),
            )
        )
        assertNotEquals(
            switchService.get(OFFICIAL, officialId)!!.name,
            switchService.get(DRAFT, officialId)!!.name,
        )
        assertEquals(2, switchService.get(OFFICIAL, officialId)!!.joints.size)
        assertEquals(3, switchService.get(DRAFT, officialId)!!.joints.size)

        publishAndVerify(publicationRequest(switches = listOf(officialId)))

        assertEquals(
            switchService.get(OFFICIAL, officialId)!!.name,
            switchService.get(DRAFT, officialId)!!.name,
        )
        assertEquals(3, switchService.get(OFFICIAL, officialId)!!.joints.size)
        assertEquals(
            switchService.get(OFFICIAL, officialId)!!.joints,
            switchService.get(DRAFT, officialId)!!.joints,
        )
    }

    @Test
    fun publishingNewTrackNumberWorks() {
        val trackNumber = trackNumber(getUnusedTrackNumber(), draft = true)
        val draftId = trackNumberService.saveDraft(trackNumber).id
        assertNull(trackNumberService.get(OFFICIAL, draftId))
        assertEquals(draftId, trackNumberService.get(DRAFT, draftId)!!.id)

        val publicationResult = publish(publicationService, trackNumbers = listOf(draftId))

        assertNotNull(publicationResult.publicationId)
        assertEquals(1, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            trackNumberService.get(OFFICIAL, draftId)!!.number,
            trackNumberService.get(DRAFT, draftId)!!.number,
        )
    }

    @Test
    fun publishingTrackNumberChangesWorks() {
        val officialId = trackNumberDao.insert(
            trackNumber(draft = false).copy(
                number = getUnusedTrackNumber(),
                description = FreeText("Test 1"),
            )
        ).id

        trackNumberService.saveDraft(
            trackNumberService.get(DRAFT, officialId)!!.copy(
                number = getUnusedTrackNumber(),
                description = FreeText("Test 2"),
            )
        )

        assertNotEquals(
            trackNumberService.get(OFFICIAL, officialId)!!.number,
            trackNumberService.get(DRAFT, officialId)!!.number,
        )

        assertEquals(FreeText("Test 1"), trackNumberService.get(OFFICIAL, officialId)!!.description)
        assertEquals(FreeText("Test 2"), trackNumberService.get(DRAFT, officialId)!!.description)

        val publicationResult = publish(publicationService, trackNumbers = listOf(officialId))

        assertNotNull(publicationResult.publicationId)
        assertEquals(1, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            trackNumberService.get(OFFICIAL, officialId)!!.number,
            trackNumberService.get(DRAFT, officialId)!!.number,
        )

        assertEquals(
            trackNumberService.get(OFFICIAL, officialId)!!.description,
            trackNumberService.get(DRAFT, officialId)!!.description,
        )
    }

    @Test
    fun fetchingPublicationListingWorks() {
        val trackNumberId = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )
        val (track, alignment) = locationTrackAndAlignment(trackNumberId, draft = true)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.get(DRAFT, draftId)!!.id)

        val publicationCountBeforePublishing = publicationService.fetchPublications().size

        val publicationResult = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId),
            locationTracks = listOf(draftId),
        )

        val publicationCountAfterPublishing = publicationService.fetchPublications()

        assertEquals(publicationCountBeforePublishing + 1, publicationCountAfterPublishing.size)
        assertEquals(publicationResult.publicationId, publicationCountAfterPublishing.last().id)
    }

    @Test
    fun publishingTrackNumberWorks() {
        verifyPublishingWorks(
            trackNumberDao,
            trackNumberService,
            { trackNumber(getUnusedTrackNumber(), draft = true) },
            { orig -> asMainDraft(orig.copy(description = FreeText("${orig.description}_edit"))) },
        )
    }

    @Test
    fun publishingReferenceLineWorks() {
        val tnId = insertOfficialTrackNumber()
        verifyPublishingWorks(
            referenceLineDao,
            referenceLineService,
            { referenceLine(tnId, draft = true) },
            { orig -> asMainDraft(orig.copy(startAddress = TrackMeter(12, 34))) },
        )
    }

    @Test
    fun publishingKmPostWorks() {
        val tnId = insertOfficialTrackNumber()
        verifyPublishingWorks(
            kmPostDao,
            kmPostService,
            { kmPost(tnId, KmNumber(123), draft = true) },
            { orig -> asMainDraft(orig.copy(kmNumber = KmNumber(321))) },
        )
    }

    @Test
    fun publishingLocationTrackWorks() {
        val tnId = insertOfficialTrackNumber()
        verifyPublishingWorks(
            locationTrackDao,
            locationTrackService,
            { locationTrack(tnId, draft = true) },
            { orig -> asMainDraft(orig.copy(descriptionBase = FreeText("${orig.descriptionBase}_edit"))) },
        )
    }

    @Test
    fun publishingSwitchWorks() {
        verifyPublishingWorks(
            switchDao,
            switchService,
            { switch(draft = true) },
            { orig -> asMainDraft(orig.copy(name = SwitchName("${orig.name}A"))) },
        )
    }

    @Test
    fun revertingOnlyGivenChangesWorks() {
        val switch1 = switchService.saveDraft(switch(123, draft = true)).id
        val switch2 = switchService.saveDraft(switch(234, draft = true)).id

        val revertResult = publicationService.revertPublicationCandidates(
            PublicationRequestIds(listOf(), listOf(), listOf(), listOf(switch1), listOf())
        )

        assertEquals(revertResult.switches, 1)
        assertNull(switchService.get(DRAFT, switch1))
        assertDoesNotThrow { switchService.get(DRAFT, switch2) }
    }

    @Test
    fun `reverting split source track will remove the whole split`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        assertTrue {
            splitDao.fetchUnfinishedSplits().any { split -> split.locationTrackId == sourceTrack.id }
        }

        publicationService.revertPublicationCandidates(publicationRequest(locationTracks = listOf(sourceTrack.id)))

        assertTrue {
            splitDao.fetchUnfinishedSplits().none { split -> split.locationTrackId == sourceTrack.id }
        }
    }

    @Test
    fun `reverting one of the split target tracks will remove the whole split`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        assertTrue {
            splitDao.fetchUnfinishedSplits().any { split -> split.containsLocationTrack(endTargetTrack.id) }
        }

        publicationService.revertPublicationCandidates(publicationRequest(locationTracks = listOf(startTargetTrack.id)))

        assertTrue {
            splitDao.fetchUnfinishedSplits().none { split -> split.containsLocationTrack(endTargetTrack.id) }
        }
    }

    @Test
    fun `publication id should be added to splits that have location tracks published`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val splitBeforePublish = splitDao.fetchUnfinishedSplits().first { split ->
            split.locationTrackId == sourceTrack.id
        }

        assertNull(splitBeforePublish.publicationId)

        val publicationId = publicationService.getValidationVersions(
            publicationRequest(locationTracks = listOf(sourceTrack.id, startTargetTrack.id, endTargetTrack.id))
        ).let { versions ->
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "").publicationId
        }

        assertEquals(publicationId, splitDao.getOrThrow(splitBeforePublish.id).publicationId)
    }

    @Test
    fun `split source and target location tracks depend on each other`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val sourceDependencies = publicationService.getRevertRequestDependencies(
            publicationRequest(locationTracks = listOf(sourceTrack.id))
        )

        val startDependencies = publicationService.getRevertRequestDependencies(
            publicationRequest(locationTracks = listOf(startTargetTrack.id))
        )

        assertContains(sourceDependencies.locationTracks, sourceTrack.id)
        assertContains(sourceDependencies.locationTracks, startTargetTrack.id)
        assertContains(sourceDependencies.locationTracks, endTargetTrack.id)

        assertContains(startDependencies.locationTracks, sourceTrack.id)
        assertContains(startDependencies.locationTracks, startTargetTrack.id)
        assertContains(startDependencies.locationTracks, endTargetTrack.id)
    }

    @Test
    fun trackNumberAndReferenceLineChangesDependOnEachOther() {
        val trackNumber = insertDraftTrackNumber()
        val referenceLine = referenceLineService.saveDraft(referenceLine(trackNumber, draft = true)).id
        val publishBoth = publicationRequest(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine))
        assertEquals(
            publishBoth,
            publicationService.getRevertRequestDependencies(publicationRequest(trackNumbers = listOf(trackNumber)))
        )
        assertEquals(
            publishBoth,
            publicationService.getRevertRequestDependencies(publicationRequest(referenceLines = listOf(referenceLine)))
        )
    }

    @Test
    fun `Assets on draft only track number depend on its reference line`() {
        val trackNumber = insertDraftTrackNumber()
        val referenceLine = referenceLineService.saveDraft(referenceLine(trackNumber, draft = true)).id
        val kmPost = kmPostService.saveDraft(kmPost(trackNumber, KmNumber(0), draft = true)).id
        val locationTrack = locationTrackService.saveDraft(locationTrack(trackNumber, draft = true)).id
        val publishAll = publicationRequest(
            trackNumbers = listOf(trackNumber),
            referenceLines = listOf(referenceLine),
            kmPosts = listOf(kmPost),
            locationTracks = listOf(locationTrack)
        )
        assertEquals(
            publishAll,
            publicationService.getRevertRequestDependencies(publicationRequest(referenceLines = listOf(referenceLine)))
        )
    }

    @Test
    fun kmPostsAndLocationTracksDependOnTheirTrackNumber() {
        val trackNumber = insertDraftTrackNumber()
        val locationTrack = locationTrackService.saveDraft(locationTrack(trackNumber, draft = true)).id
        val kmPost = kmPostService.saveDraft(kmPost(trackNumber, KmNumber(0), draft = true)).id
        val all = publicationRequest(
            trackNumbers = listOf(trackNumber),
            locationTracks = listOf(locationTrack),
            kmPosts = listOf(kmPost),
        )
        assertEquals(
            all,
            publicationService.getRevertRequestDependencies(publicationRequest(trackNumbers = listOf(trackNumber))),
        )
    }

    @Test
    fun `should sort publications by publication time in descending order`() {
        val trackNumber1Id = insertDraftTrackNumber()
        val trackNumber2Id = insertDraftTrackNumber()
        val publish1Result = publicationRequest(trackNumbers = listOf(trackNumber1Id, trackNumber2Id)).let { r ->
            val versions = publicationService.getValidationVersions(r)
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "")
        }

        assertEquals(2, publish1Result.trackNumbers)

        val trackNumber1 = trackNumberService.get(OFFICIAL, trackNumber1Id)
        val trackNumber2 = trackNumberService.get(OFFICIAL, trackNumber2Id)
        assertNotNull(trackNumber1)
        assertNotNull(trackNumber2)

        val newTrackNumber1TrackNumber = "${trackNumber1.number} ZZZ"

        trackNumberService.saveDraft(trackNumber1.copy(number = TrackNumber(newTrackNumber1TrackNumber)))
        val publish2Result = publicationRequest(trackNumbers = listOf(trackNumber1Id)).let { r ->
            val versions = publicationService.getValidationVersions(r)
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "")
        }

        assertEquals(1, publish2Result.trackNumbers)
    }

    @Test
    fun `Validating official location track should work`() {
        val trackNumber = insertOfficialTrackNumber()
        val (locationTrack, alignment) = locationTrackAndAlignment(
            trackNumber,
            segment(Point(4.0, 4.0), Point(5.0, 5.0)),
            draft = false,
        )
        val locationTrackId = locationTrackDao.insert(
            locationTrack.copy(
                alignmentVersion = alignmentDao.insert(alignment)
            )
        )

        val validation = publicationService.validateLocationTracks(listOf(locationTrackId.id), OFFICIAL).first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official track number should work`() {
        val trackNumber = insertOfficialTrackNumber()

        val validation = publicationService.validateTrackNumbersAndReferenceLines(listOf(trackNumber), OFFICIAL).first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official switch should work`() {
        val switchId = switchDao.insert(switch(123, draft = false)).id

        val validation = publicationService.validateSwitches(listOf(switchId), OFFICIAL)
        assertEquals(1, validation.size)
        assertEquals(3, validation[0].errors.size)
    }

    @Test
    fun `Validating multiple switches should work`() {
        val switchId = switchDao.insert(switch(123, draft = false)).id
        val switchId2 = switchDao.insert(switch(234, draft = false)).id
        val switchId3 = switchDao.insert(switch(456, draft = false)).id

        val validationIds =
            publicationService.validateSwitches(listOf(switchId, switchId2, switchId3), OFFICIAL).map { it.id }
        assertEquals(3, validationIds.size)
        assertContains(validationIds, switchId)
        assertContains(validationIds, switchId2)
        assertContains(validationIds, switchId3)
    }

    @Test
    fun `Validating official km post should work`() {
        val kmPostId = kmPostDao.insert(kmPost(insertOfficialTrackNumber(), km = KmNumber.ZERO, draft = false)).id

        val validation = publicationService.validateKmPosts(listOf(kmPostId), OFFICIAL).first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Publication validation identifies duplicate names`() {
        trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = false))
        val draftTrackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = true)).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLineId = referenceLineDao.insert(
            referenceLine(draftTrackNumberId, alignmentVersion = someAlignment, draft = true)
        ).id
        locationTrackDao.insert(
            locationTrack(draftTrackNumberId, name = "LT", alignmentVersion = someAlignment, draft = false)
        )
        // one new draft location track trying to use an official one's name
        val draftLocationTrackId = locationTrackDao.insert(
            locationTrack(draftTrackNumberId, name = "LT", alignmentVersion = someAlignment, draft = true)
        ).id

        // two new location tracks stepping over each other's names
        val newLt = locationTrack(
            draftTrackNumberId,
            name = "NLT",
            alignmentVersion = someAlignment,
            externalId = null,
            draft = true,
        )
        val newLocationTrack1 = locationTrackDao.insert(newLt).id
        val newLocationTrack2 = locationTrackDao.insert(newLt).id

        switchDao.insert(switch(123, name = "SW", stateCategory = LayoutStateCategory.EXISTING, draft = false))
        // one new switch trying to use an official one's name
        val draftSwitchId = switchDao.insert(
            switch(123, name = "SW", stateCategory = LayoutStateCategory.EXISTING, draft = true)
        ).id

        // two new switches both trying to use the same name
        val newSwitch = switch(124, name = "NSW", stateCategory = LayoutStateCategory.EXISTING, draft = true)
        val newSwitch1 = switchDao.insert(newSwitch).id
        val newSwitch2 = switchDao.insert(newSwitch).id

        val validation = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            PublicationRequestIds(
                trackNumbers = listOf(draftTrackNumberId),
                locationTracks = listOf(draftLocationTrackId, newLocationTrack1, newLocationTrack2),
                kmPosts = listOf(),
                referenceLines = listOf(referenceLineId),
                switches = listOf(draftSwitchId, newSwitch1, newSwitch2)
            ),
        )

        assertEquals(
            listOf(
                PublicationValidationError(
                    PublicationValidationErrorType.ERROR,
                    "validation.layout.location-track.duplicate-name-official",
                    mapOf("locationTrack" to AlignmentName("LT"), "trackNumber" to TrackNumber("TN"))
                )
            ),
            validation.validatedAsPublicationUnit.locationTracks.find { lt -> lt.id == draftLocationTrackId }?.errors,
        )

        assertEquals(
            List(2) {
                PublicationValidationError(
                    PublicationValidationErrorType.ERROR,
                    "validation.layout.location-track.duplicate-name-draft",
                    mapOf("locationTrack" to AlignmentName("NLT"), "trackNumber" to TrackNumber("TN"))
                )
            },
            validation.validatedAsPublicationUnit.locationTracks
                .filter { lt -> lt.name == AlignmentName("NLT") }
                .flatMap { it.errors },
        )

        assertEquals(
            listOf(
                PublicationValidationError(
                    PublicationValidationErrorType.ERROR,
                    "validation.layout.switch.duplicate-name-official",
                    mapOf("switch" to SwitchName("SW"))
                )
            ),
            validation.validatedAsPublicationUnit.switches
                .find { it.name == SwitchName("SW") }
                ?.errors
                ?.filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-official" },
        )

        assertEquals(
            List(2) {
                PublicationValidationError(
                    PublicationValidationErrorType.ERROR,
                    "validation.layout.switch.duplicate-name-draft",
                    mapOf("switch" to SwitchName("NSW"))
                )
            },
            validation.validatedAsPublicationUnit.switches
                .filter { it.name == SwitchName("NSW") }
                .flatMap { it.errors }
                .filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-draft" },
        )

        assertEquals(
            listOf(
                PublicationValidationError(
                    PublicationValidationErrorType.ERROR,
                    "validation.layout.track-number.duplicate-name-official",
                    mapOf("trackNumber" to TrackNumber("TN"))
                )
            ),
            validation.validatedAsPublicationUnit.trackNumbers[0].errors,
        )
    }

    @Test
    fun `Publication rejects duplicate track number names`() {
        trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = false))
        val draftTrackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = true)).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.insert(referenceLine(draftTrackNumberId, alignmentVersion = someAlignment, draft = true)).id
        val exception = assertThrows<DuplicateNameInPublicationException> {
            publish(publicationService, trackNumbers = listOf(draftTrackNumberId))
        }
        assertEquals("error.publication.duplicate-name-on.track-number", exception.localizationKey.toString())
        assertEquals(mapOf("name" to "TN"), exception.localizationParams.params)
    }

    @Test
    fun `Publication rejects duplicate location track names`() {
        val trackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = false)).id
        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = someAlignment, draft = true)).id

        locationTrackDao.insert(
            locationTrack(trackNumberId, name = "LT", alignmentVersion = someAlignment, draft = false)
        )
        val draftLt = locationTrack(trackNumberId, name = "LT", alignmentVersion = someAlignment, draft = true)
        val draftLocationTrackId = locationTrackDao.insert(draftLt).id
        val exception = assertThrows<DuplicateLocationTrackNameInPublicationException> {
            publish(publicationService, locationTracks = listOf(draftLocationTrackId))
        }
        assertEquals("error.publication.duplicate-name-on.location-track", exception.localizationKey.toString())
        assertEquals(
            mapOf("locationTrack" to "LT", "trackNumber" to "TN"),
            exception.localizationParams.params,
        )
    }

    @Test
    fun `Location tracks can be renamed over each other`() {
        val trackNumberId = insertOfficialTrackNumber()
        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = someAlignment, draft = true)).id

        val lt1 = locationTrack(
            trackNumberId = trackNumberId,
            name = "LT1",
            alignmentVersion = someAlignment,
            externalId = null,
            draft = false,
        )
        val lt1OriginalVersion = locationTrackDao.insert(lt1).rowVersion
        val lt1RenamedDraft = locationTrackDao.insert(
            asMainDraft(locationTrackDao.fetch(lt1OriginalVersion).copy(name = AlignmentName("LT2")))
        )

        val lt2 = locationTrack(
            trackNumberId = trackNumberId,
            name = "LT2",
            alignmentVersion = someAlignment,
            externalId = null,
            draft = false,
        )
        val lt2OriginalVersion = locationTrackDao.insert(lt2).rowVersion
        val lt2RenamedDraft = locationTrackDao.insert(
            asMainDraft(locationTrackDao.fetch(lt2OriginalVersion).copy(name = AlignmentName("LT1")))
        )

        publish(publicationService, locationTracks = listOf(lt1RenamedDraft.id, lt2RenamedDraft.id))
    }

    @Test
    fun `Publication rejects duplicate switch names`() {
        switchDao.insert(switch(123, name = "SW123", draft = false))
        val draftSwitchId = switchDao.insert(switch(123, name = "SW123", draft = true)).id
        val exception = assertThrows<DuplicateNameInPublicationException> {
            publish(publicationService, switches = listOf(draftSwitchId))
        }
        assertEquals("error.publication.duplicate-name-on.switch", exception.localizationKey.toString())
        assertEquals(mapOf("name" to "SW123"), exception.localizationParams.params)
    }

    @Test
    fun `Publication validation rejects duplication by another referencing track`() {
        val trackNumberId = insertOfficialTrackNumber()
        val dummyAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))))
        // Initial state, all official: Small duplicates middle, middle and big don't duplicate anything
        val middleTrack = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                name = "middle track",
                alignmentVersion = dummyAlignment,
                draft = false,
            )
        )
        val smallTrack = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                name = "small track",
                duplicateOf = middleTrack.id,
                alignmentVersion = dummyAlignment,
                draft = false,
            )
        )
        val bigTrack = locationTrackDao.insert(
            locationTrack(trackNumberId, name = "big track", alignmentVersion = dummyAlignment, draft = false)
        )

        // In new draft, middle wants to duplicate big (leading to: small->middle->big)
        locationTrackService.saveDraft(locationTrackDao.fetch(middleTrack.rowVersion).copy(duplicateOf = bigTrack.id))

        fun getPublishingDuplicateWhileDuplicatedValidationError(
            vararg publishableTracks: IntId<LocationTrack>,
        ): PublicationValidationError? {
            val validation = publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(),
                PublicationRequestIds(
                    trackNumbers = listOf(),
                    locationTracks = listOf(*publishableTracks),
                    kmPosts = listOf(),
                    referenceLines = listOf(),
                    switches = listOf()
                ),
            )
            val trackErrors = validation.validatedAsPublicationUnit.locationTracks[0].errors
            return trackErrors.find { error ->
                error.localizationKey == LocalizationKey(
                    "validation.layout.location-track.duplicate-of.publishing-duplicate-while-duplicated"
                )
            }
        }

        // if we're only trying to publish the middle track, but the small is still duplicating it, we pop
        val duplicateError = getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id)
        assertNotNull(duplicateError, "small track duplicates to-be-published middle track which duplicates big track")
        assertEquals("small track", duplicateError.params.get("otherDuplicates"))
        assertEquals("big track", duplicateError.params.get("duplicateTrack"))

        // if we have a draft of the small track that is not a duplicate of the middle track, but we're not publishing
        // it in this unit, that doesn't fix the issue yet
        locationTrackService.saveDraft(locationTrackDao.fetch(smallTrack.rowVersion).copy(duplicateOf = null))
        assertNotNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id),
            "only saving a draft of small track",
        )

        // but if we have the new non-duplicating small track in the same publication unit, it's fine
        assertNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id),
            "publishing new small track",
        )

        // finally, if we have a track whose official version doesn't duplicate the middle track, but the draft does,
        // it's only bad if the draft is in the publication unit
        val otherSmallTrack = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                name = "other small track",
                alignmentVersion = dummyAlignment,
                draft = false,
            )
        )
        locationTrackService.saveDraft(
            locationTrackDao.fetch(otherSmallTrack.rowVersion).copy(duplicateOf = middleTrack.id)
        )
        assertNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id),
            "publishing new small track with other small track added"
        )
        assertNotNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id, otherSmallTrack.id),
            "publishing new small track with other small track added and in publication unit"
        )
    }

    private fun getCalculatedChangesInRequest(versions: ValidationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChanges(versions)

    private fun publishAndVerify(request: PublicationRequestIds): PublicationResult {
        val versions = publicationService.getValidationVersions(request)
        verifyVersions(request, versions)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publicationResult = publicationService.publishChanges(versions, draftCalculatedChanges, "Test")
        val publicationDetails = publicationService.getPublicationDetails(publicationResult.publicationId!!)
        assertNotNull(publicationResult.publicationId)
        verifyPublished(versions.trackNumbers, trackNumberDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(versions.referenceLines, referenceLineDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(versions.kmPosts, kmPostDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(versions.locationTracks, locationTrackDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(versions.switches, switchDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }

        assertEqualsCalculatedChanges(draftCalculatedChanges, publicationDetails)
        return publicationResult
    }

    @Test
    fun `Track number diff finds all changed fields`() {
        val address = TrackMeter(0, 0)
        val trackNumber = trackNumberService.get(
            DRAFT,
            trackNumberService.insert(
                TrackNumberSaveRequest(
                    getUnusedTrackNumber(),
                    FreeText("TEST"),
                    LayoutState.IN_USE,
                    address,
                )
            ),
        )
        val rl = referenceLineService.getByTrackNumber(DRAFT, trackNumber!!.id as IntId)!!
        publishAndVerify(
            publicationRequest(
                trackNumbers = listOf(trackNumber.id as IntId),
                referenceLines = listOf(rl.id as IntId),
            )
        )
        trackNumberService.update(
            trackNumber.id as IntId,
            TrackNumberSaveRequest(
                number = TrackNumber(trackNumber.number.value + " T"),
                description = trackNumber.description + "_TEST",
                startAddress = TrackMeter(0, 0),
                state = LayoutState.NOT_IN_USE,
            ),
        )
        publishAndVerify(publicationRequest(trackNumbers = listOf(trackNumber.id as IntId)))
        val thisAndPreviousPublication = publicationService.fetchLatestPublicationDetails(2)
        val changes = publicationDao.fetchPublicationTrackNumberChanges(
            thisAndPreviousPublication.first().id,
            thisAndPreviousPublication.last().publicationTime,
        )

        val diff = publicationService.diffTrackNumber(
            localizationService.getLocalization("fi"),
            changes.getValue(trackNumber.id as IntId),
            thisAndPreviousPublication.first().publicationTime,
            thisAndPreviousPublication.last().publicationTime,
        ) { _, _ -> null }
        assertEquals(3, diff.size)
        assertEquals("track-number", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
        assertEquals("description", diff[2].propKey.key.toString())
    }

    @Test
    fun `Changing specific Track Number field returns only that field`() {
        val address = TrackMeter(0, 0)
        val trackNumber = trackNumberService.getOrThrow(
            DRAFT,
            trackNumberService.insert(
                TrackNumberSaveRequest(
                    getUnusedTrackNumber(),
                    FreeText("TEST"),
                    LayoutState.IN_USE,
                    address,
                )
            ),
        )
        val rl = referenceLineService.getByTrackNumber(DRAFT, trackNumber.id as IntId)!!
        publishAndVerify(
            publicationRequest(
                trackNumbers = listOf(trackNumber.id as IntId),
                referenceLines = listOf(rl.id as IntId),
            )
        )

        val idOfUpdated = trackNumberService.update(
            trackNumber.id as IntId,
            TrackNumberSaveRequest(
                number = trackNumber.number,
                description = FreeText("TEST2"),
                startAddress = address,
                state = trackNumber.state,
            ),
        )
        publishAndVerify(publicationRequest(trackNumbers = listOf(trackNumber.id as IntId)))
        val thisAndPreviousPublication = publicationService.fetchLatestPublicationDetails(2)
        val changes = publicationDao.fetchPublicationTrackNumberChanges(
            thisAndPreviousPublication.first().id,
            thisAndPreviousPublication.last().publicationTime,
        )
        val updatedTrackNumber = trackNumberService.getOrThrow(OFFICIAL, idOfUpdated)

        val diff = publicationService.diffTrackNumber(
            localizationService.getLocalization("fi"),
            changes.getValue(trackNumber.id as IntId),
            thisAndPreviousPublication.first().publicationTime,
            thisAndPreviousPublication.last().publicationTime,
        ) { _, _ -> null }
        assertEquals(1, diff.size)
        assertEquals("description", diff[0].propKey.key.toString())
        assertEquals(trackNumber.description, diff[0].value.oldValue)
        assertEquals(updatedTrackNumber.description, diff[0].value.newValue)
    }

    @Test
    fun `Location track diff finds all changed fields`() {
        val duplicate = locationTrackDao.fetch(
            locationTrackService.insert(
                LocationTrackSaveRequest(
                    AlignmentName("TEST duplicate"),
                    FreeText("Test"),
                    DescriptionSuffixType.NONE,
                    LocationTrackType.MAIN,
                    LayoutState.IN_USE,
                    getUnusedTrackNumberId(),
                    null,
                    TopologicalConnectivityType.NONE,
                    IntId(1),
                )
            ).rowVersion
        )

        val duplicate2 = locationTrackDao.fetch(
            locationTrackService.insert(
                LocationTrackSaveRequest(
                    AlignmentName("TEST duplicate 2"),
                    FreeText("Test"),
                    DescriptionSuffixType.NONE,
                    LocationTrackType.MAIN,
                    LayoutState.IN_USE,
                    getUnusedTrackNumberId(),
                    null,
                    TopologicalConnectivityType.NONE,
                    IntId(1),
                )
            ).rowVersion
        )

        val locationTrack = locationTrackDao.fetch(
            locationTrackService.insert(
                LocationTrackSaveRequest(
                    AlignmentName("TEST"),
                    FreeText("Test"),
                    DescriptionSuffixType.NONE,
                    LocationTrackType.MAIN,
                    LayoutState.IN_USE,
                    getUnusedTrackNumberId(),
                    duplicate.id as IntId<LocationTrack>,
                    TopologicalConnectivityType.NONE,
                    IntId(1),
                )
            ).rowVersion
        )
        publishAndVerify(
            publicationRequest(
                locationTracks = listOf(
                    locationTrack.id as IntId<LocationTrack>,
                    duplicate.id as IntId<LocationTrack>,
                    duplicate2.id as IntId<LocationTrack>,
                )
            )
        )

        val updatedLocationTrack = locationTrackDao.fetch(
            locationTrackService.update(
                locationTrack.id as IntId,
                LocationTrackSaveRequest(
                    name = AlignmentName("TEST2"),
                    descriptionBase = FreeText("Test2"),
                    descriptionSuffix = DescriptionSuffixType.SWITCH_TO_BUFFER,
                    type = LocationTrackType.SIDE,
                    state = LayoutState.NOT_IN_USE,
                    trackNumberId = locationTrack.trackNumberId,
                    duplicate2.id as IntId<LocationTrack>,
                    topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                    IntId(1),
                ),
            ).rowVersion
        )
        publish(publicationService, locationTracks = listOf(updatedLocationTrack.id as IntId<LocationTrack>))
        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff = publicationService.diffLocationTrack(
            localizationService.getLocalization("fi"),
            changes.getValue(locationTrack.id as IntId<LocationTrack>),
            null,
            latestPub.publicationTime,
            previousPub.publicationTime,
            trackNumberDao.fetchTrackNumberNames(),
            emptySet(),
        ) { _, _ -> null }
        assertEquals(6, diff.size)
        assertEquals("location-track", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
        assertEquals("location-track-type", diff[2].propKey.key.toString())
        assertEquals("description-base", diff[3].propKey.key.toString())
        assertEquals("description-suffix", diff[4].propKey.key.toString())
        assertEquals("duplicate-of", diff[5].propKey.key.toString())
    }

    @Test
    fun `Don't allow publishing a track that is a duplicate of an unpublished draft-only one`() {
        val trackNumberId = getUnusedTrackNumberId()
        val (draftOnlyTrack, draftOnlyAlignment) = locationTrackAndAlignment(
            trackNumberId = trackNumberId,
            segments = listOf(someSegment()),
            duplicateOf = null,
            draft = true,
        )
        val draftOnlyId = locationTrackService.saveDraft(draftOnlyTrack, draftOnlyAlignment).id

        val (duplicateTrack, duplicateAlignment) = locationTrackAndAlignment(
            trackNumberId = trackNumberId,
            segments = listOf(someSegment()),
            duplicateOf = draftOnlyId,
            draft = true,
        )
        val duplicateId = locationTrackService.saveDraft(duplicateTrack, duplicateAlignment).id

        // Both tracks in validation set: this is fine
        assertFalse(
            containsDuplicateOfNotPublishedError(
                validateLocationTrack(toValidate = duplicateId, duplicateId, draftOnlyId)
            )
        )
        // Only the target (main) track in set: this is also fine
        assertFalse(
            containsDuplicateOfNotPublishedError(
                validateLocationTrack(toValidate = draftOnlyId, draftOnlyId)
            )
        )
        // Only the duplicate track in set: this would result in official referring to draft through duplicateOf
        assertTrue(
            containsDuplicateOfNotPublishedError(
                validateLocationTrack(toValidate = duplicateId, duplicateId)
            )
        )
    }

    private fun containsDuplicateOfNotPublishedError(errors: List<PublicationValidationError>) =
        containsError(errors, "validation.layout.location-track.duplicate-of.not-published")

    private fun containsError(errors: List<PublicationValidationError>, key: String) =
        errors.any { e -> e.localizationKey.toString() == key }

    private fun validateLocationTrack(
        toValidate: IntId<LocationTrack>,
        vararg publicationSet: IntId<LocationTrack>,
    ): List<PublicationValidationError> {
        val candidates = publicationService
            .collectPublicationCandidates()
            .filter(publicationRequest(locationTracks = publicationSet.toList()))
        return publicationService
            .validateAsPublicationUnit(candidates, false)
            .locationTracks.find { c -> c.id == toValidate }!!
            .errors
    }

    @Test
    fun `Changing specific Location Track field returns only that field`() {
        val saveReq = LocationTrackSaveRequest(
            AlignmentName("TEST"),
            FreeText("Test"),
            DescriptionSuffixType.NONE,
            LocationTrackType.MAIN,
            LayoutState.IN_USE,
            getUnusedTrackNumberId(),
            null,
            TopologicalConnectivityType.NONE,
            IntId(1)
        )

        val locationTrack = locationTrackDao.fetch(
            locationTrackService.insert(saveReq).rowVersion
        )
        publish(publicationService, locationTracks = listOf(locationTrack.id as IntId<LocationTrack>))

        val updatedLocationTrack = locationTrackDao.fetch(
            locationTrackService.update(
                locationTrack.id as IntId, saveReq.copy(descriptionBase = FreeText("TEST2"))
            ).rowVersion
        )
        publish(publicationService, locationTracks = listOf(updatedLocationTrack.id as IntId<LocationTrack>))
        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff = publicationService.diffLocationTrack(
            localizationService.getLocalization("fi"),
            changes.getValue(locationTrack.id as IntId<LocationTrack>),
            null,
            latestPub.publicationTime,
            previousPub.publicationTime,
            trackNumberDao.fetchTrackNumberNames(),
            emptySet()
        ) { _, _ -> null }
        assertEquals(1, diff.size)
        assertEquals("description-base", diff[0].propKey.key.toString())
        assertEquals(locationTrack.descriptionBase, diff[0].value.oldValue)
        assertEquals(updatedLocationTrack.descriptionBase, diff[0].value.newValue)
    }

    @Test
    fun `KM Post diff finds all changed fields`() {
        val trackNumberSaveReq = TrackNumberSaveRequest(
            getUnusedTrackNumber(),
            FreeText("TEST"),
            LayoutState.IN_USE,
            TrackMeter(0, 0),
        )
        val trackNumber = trackNumberService.getOrThrow(
            DRAFT,
            trackNumberService.insert(trackNumberSaveReq),
        )
        val trackNumber2 = trackNumberService.getOrThrow(
            DRAFT,
            trackNumberService.insert(trackNumberSaveReq.copy(getUnusedTrackNumber(), FreeText("TEST 2"))),
        )

        val kmPost = kmPostService.getOrThrow(
            DRAFT,
            kmPostService.insertKmPost(
                TrackLayoutKmPostSaveRequest(
                    KmNumber(0),
                    LayoutState.IN_USE,
                    trackNumber.id as IntId,
                )
            ),
        )
        publish(
            publicationService,
            kmPosts = listOf(kmPost.id as IntId),
            trackNumbers = listOf(trackNumber.id as IntId, trackNumber2.id as IntId)
        )
        val updatedKmPost = kmPostService.getOrThrow(
            DRAFT,
            kmPostService.updateKmPost(
                kmPost.id as IntId,
                TrackLayoutKmPostSaveRequest(
                    KmNumber(1),
                    LayoutState.NOT_IN_USE,
                    trackNumber2.id as IntId,
                ),
            ),
        )
        publish(publicationService, kmPosts = listOf(updatedKmPost.id as IntId))

        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)

        val diff = publicationService.diffKmPost(
            localizationService.getLocalization("fi"),
            changes.getValue(kmPost.id as IntId),
            latestPub.publicationTime,
            latestPub.publicationTime.minusMillis(1),
            trackNumberDao.fetchTrackNumberNames(),
        ) { _, _ -> null }
        assertEquals(2, diff.size)
        // assertEquals("track-number", diff[0].propKey) TODO Enable when track number switching works
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
    }

    @Test
    fun `Changing specific KM Post field returns only that field`() {
        val saveReq = TrackLayoutKmPostSaveRequest(
            KmNumber(0),
            LayoutState.IN_USE,
            insertOfficialTrackNumber(),
        )

        val kmPost = kmPostService.getOrThrow(
            DRAFT,
            kmPostService.insertKmPost(saveReq),
        )
        publish(publicationService, kmPosts = listOf(kmPost.id as IntId))
        val updatedKmPost = kmPostService.getOrThrow(
            DRAFT,
            kmPostService.updateKmPost(kmPost.id as IntId, saveReq.copy(kmNumber = KmNumber(1))),
        )
        publish(publicationService, kmPosts = listOf(updatedKmPost.id as IntId))
        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)

        val diff = publicationService.diffKmPost(
            localizationService.getLocalization("fi"),
            changes.getValue(kmPost.id as IntId),
            latestPub.publicationTime,
            latestPub.publicationTime.minusMillis(1),
            trackNumberDao.fetchTrackNumberNames(),
        ) { _, _ -> null }
        assertEquals(1, diff.size)
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals(kmPost.kmNumber, diff[0].value.oldValue)
        assertEquals(updatedKmPost.kmNumber, diff[0].value.newValue)
    }

    @Test
    fun `Switch diff finds all changed fields`() {
        val trackNumberSaveReq = TrackNumberSaveRequest(
            getUnusedTrackNumber(),
            FreeText("TEST"),
            LayoutState.IN_USE,
            TrackMeter(0, 0),
        )
        val tn1 = trackNumberService.insert(trackNumberSaveReq)
        val tn2 = trackNumberService.insert(
            trackNumberSaveReq.copy(getUnusedTrackNumber(), FreeText("TEST 2"))
        )

        val switch = switchService.getOrThrow(
            DRAFT,
            switchService.insertSwitch(
                TrackLayoutSwitchSaveRequest(
                    SwitchName("TEST"),
                    IntId(1),
                    LayoutStateCategory.EXISTING,
                    IntId(1),
                    false,
                )
            ),
        )
        publish(publicationService, switches = listOf(switch.id as IntId), trackNumbers = listOf(tn1, tn2))
        val updatedSwitch = switchService.getOrThrow(
            DRAFT,
            switchService.updateSwitch(
                switch.id as IntId,
                TrackLayoutSwitchSaveRequest(
                    SwitchName("TEST 2"),
                    IntId(2),
                    LayoutStateCategory.FUTURE_EXISTING,
                    IntId(2),
                    true,
                ),
            ),
        )
        publish(publicationService, switches = listOf(updatedSwitch.id as IntId))

        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff = publicationService.diffSwitch(
            localizationService.getLocalization("fi"),
            changes.getValue(switch.id as IntId),
            latestPub.publicationTime,
            previousPub.publicationTime,
            Operation.MODIFY,
            trackNumberDao.fetchTrackNumberNames()
        ) { _, _ -> null }
        assertEquals(5, diff.size)
        assertEquals("switch", diff[0].propKey.key.toString())
        assertEquals("state-category", diff[1].propKey.key.toString())
        assertEquals("switch-type", diff[2].propKey.key.toString())
        assertEquals("trap-point", diff[3].propKey.key.toString())
        assertEquals("owner", diff[4].propKey.key.toString())
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

        val switch = switchService.getOrThrow(
            DRAFT,
            switchService.insertSwitch(saveReq),
        )
        publish(publicationService, switches = listOf(switch.id as IntId))
        val updatedSwitch = switchService.getOrThrow(
            DRAFT,
            switchService.updateSwitch(switch.id as IntId, saveReq.copy(name = SwitchName("TEST 2"))),
        )
        publish(publicationService, switches = listOf(updatedSwitch.id as IntId))

        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff = publicationService.diffSwitch(
            localizationService.getLocalization("fi"),
            changes.getValue(switch.id as IntId),
            latestPub.publicationTime,
            previousPub.publicationTime,
            Operation.MODIFY,
            trackNumberDao.fetchTrackNumberNames()
        ) { _, _ -> null }
        assertEquals(1, diff.size)
        assertEquals("switch", diff[0].propKey.key.toString())
        assertEquals(switch.name, diff[0].value.oldValue)
        assertEquals(updatedSwitch.name, diff[0].value.newValue)
    }

    private fun alignmentWithSwitchLinks(vararg switchIds: IntId<TrackLayoutSwitch>?): LayoutAlignment =
        alignment(switchIds.mapIndexed { index, switchId ->
            segment(Point(0.0, index * 1.0), Point(0.0, index * 1.0 + 1.0)).let { segment ->
                if (switchId == null) {
                    segment
                } else {
                    segment.copy(switchId = switchId, startJointNumber = JointNumber(1))
                }
            }
        })

    @Test
    fun `Location track switch link changes are reported`() {
        val switchUnlinkedFromTopology = switchDao.insert(
            switch(name = "sw-unlinked-from-topology", externalId = "1.1.1.1.1", draft = false)
        )
        val switchUnlinkedFromAlignment = switchDao.insert(
            switch(name = "sw-unlinked-from-alignment", externalId = "1.1.1.1.2", draft = false)
        )
        val switchAddedToTopologyStart = switchDao.insert(
            switch(name = "sw-added-to-topo-start", externalId = "1.1.1.1.3", draft = false)
        )
        val switchAddedToTopologyEnd = switchDao.insert(
            switch(name = "sw-added-to-topo-end", externalId = "1.1.1.1.4", draft = false)
        )
        val switchAddedToAlignment = switchDao.insert(
            switch(name = "sw-added-to-alignment", externalId = "1.1.1.1.5", draft = false)
        )
        val switchDeleted = switchDao.insert(
            switch(name = "sw-deleted", externalId = "1.1.1.1.6", draft = false)
        )
        val switchMerelyRenamed = switchDao.insert(
            switch(name = "sw-merely-renamed", externalId = "1.1.1.1.7", draft = false)
        )
        val originalSwitchReplacedWithNewSameName = switchDao.insert(
            switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.8", draft = false)
        )

        val trackNumberId = getUnusedTrackNumberId()

        val originalLocationTrack = locationTrackService.saveDraft(
            locationTrack(
                trackNumberId,
                topologyStartSwitch = TopologyLocationTrackSwitch(switchUnlinkedFromTopology.id, JointNumber(1)),
                draft = true,
            ),
            alignmentWithSwitchLinks(
                switchUnlinkedFromAlignment.id,
                switchDeleted.id,
                switchMerelyRenamed.id,
                originalSwitchReplacedWithNewSameName.id
            ),
        )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))
        switchService.saveDraft(
            switchDao.fetch(switchDeleted.rowVersion).copy(stateCategory = LayoutStateCategory.NOT_EXISTING)
        )
        switchService.saveDraft(
            switchDao
                .fetch(originalSwitchReplacedWithNewSameName.rowVersion)
                .copy(stateCategory = LayoutStateCategory.NOT_EXISTING)
        )
        switchService.saveDraft(
            switchDao.fetch(switchMerelyRenamed.rowVersion).copy(name = SwitchName("sw-with-new-name"))
        )
        val newSwitchReplacingOldWithSameName = switchService.saveDraft(
            switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.9", draft = true)
        )

        locationTrackService.saveDraft(
            locationTrackDao.fetch(locationTrackDao.fetchVersion(originalLocationTrack.id, OFFICIAL)!!).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyStart.id, JointNumber(1)),
                topologyEndSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyEnd.id, JointNumber(1))
            ),
            alignmentWithSwitchLinks(
                switchAddedToAlignment.id,
                switchMerelyRenamed.id,
                newSwitchReplacingOldWithSameName.id,
                null,
            ),
        )
        publish(
            publicationService,
            locationTracks = listOf(originalLocationTrack.id),
            switches = listOf(
                switchDeleted.id,
                switchMerelyRenamed.id,
                originalSwitchReplacedWithNewSameName.id,
                newSwitchReplacingOldWithSameName.id,
            ),
        )
        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs[0]
        val previousPub = latestPubs[1]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff = publicationService.diffLocationTrack(
            localizationService.getLocalization("fi"),
            changes.getValue(originalLocationTrack.id),
            publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(latestPub.id)[originalLocationTrack.id],
            latestPub.publicationTime,
            previousPub.publicationTime,
            trackNumberDao.fetchTrackNumberNames(),
            setOf(),
        ) { _, _ -> null }
        assertEquals(1, diff.size)
        assertEquals("linked-switches", diff[0].propKey.key.toString())
        assertEquals(
            """
                Vaihteiden sw-deleted, sw-replaced-with-new-same-name (1.1.1.1.8), sw-unlinked-from-alignment,
                sw-unlinked-from-topology linkitys purettu. Vaihteet sw-added-to-alignment, sw-added-to-topo-end,
                sw-added-to-topo-start, sw-replaced-with-new-same-name (1.1.1.1.9) linkitetty.
            """.trimIndent().replace("\n", " "),
            diff[0].remark,
        )
    }

    @Test
    fun `Location track geometry changes are reported`() {
        val trackNumberId = insertOfficialTrackNumber()
        fun segmentWithCurveToMaxY(maxY: Double) = segment(
            *(0..10)
                .map { x -> Point(x.toDouble(), (5.0 - (x.toDouble() - 5.0).absoluteValue) / 10.0 * maxY) }
                .toTypedArray(),
        )

        val referenceLineAlignment = alignmentDao.insert(alignment(segmentWithCurveToMaxY(0.0)))
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = referenceLineAlignment, draft = false))

        // track that had bump to y=-10 goes to having a bump to y=10, meaning the length and ends stay the same,
        // but the geometry changes
        val originalAlignment = alignment(segmentWithCurveToMaxY(-10.0))
        val newAlignment = alignment(segmentWithCurveToMaxY(10.0))
        val originalLocationTrack = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(originalAlignment),
                draft = false,
            )
        )
        locationTrackService.saveDraft(
            asMainDraft(locationTrackDao.fetch(originalLocationTrack.rowVersion)),
            newAlignment,
        )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))
        val latestPub = publicationService.fetchLatestPublicationDetails(1)[0]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff = publicationService.diffLocationTrack(
            localizationService.getLocalization("fi"),
            changes.getValue(originalLocationTrack.id),
            publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(latestPub.id)[originalLocationTrack.id],
            latestPub.publicationTime,
            latestPub.publicationTime,
            trackNumberDao.fetchTrackNumberNames(),
            setOf(KmNumber(0)),
        ) { _, _ -> null }
        assertEquals(1, diff.size)
        assertEquals("Muutos välillä 0000+0001-0000+0009, sivusuuntainen muutos 10.0 m", diff[0].remark)
    }

    @Test
    fun `should filter publication details by dates`() {
        clearPublicationTables()

        val trackNumberId1 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 = locationTrackAndAlignment(trackNumberId1, draft = true).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish1 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId1),
            locationTracks = listOf(locationTrack1),
        )

        val trackNumberId2 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 = locationTrackAndAlignment(trackNumberId2, draft = true).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish2 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId2),
            locationTracks = listOf(locationTrack2),
        )

        val publication1 = publicationDao.getPublication(publish1.publicationId!!)
        val publication2 = publicationDao.getPublication(publish2.publicationId!!)

        assertTrue {
            publicationService.fetchPublicationDetailsBetweenInstants(to = publication1.publicationTime).isEmpty()
        }

        assertTrue {
            publicationService.fetchPublicationDetailsBetweenInstants(
                from = publication2.publicationTime.plusMillis(1)
            ).isEmpty()
        }

        assertEquals(
            2,
            publicationService.fetchPublicationDetailsBetweenInstants(
                from = publication1.publicationTime,
                to = publication2.publicationTime.plusMillis(1),
            ).size,
        )
    }

    @Test
    fun `should fetch latest publications`() {
        clearPublicationTables()

        val trackNumberId1 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 = locationTrackAndAlignment(trackNumberId1, draft = true).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish1 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId1),
            locationTracks = listOf(locationTrack1),
        )

        val trackNumberId2 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 = locationTrackAndAlignment(trackNumberId2, draft = true).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish2 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId2),
            locationTracks = listOf(locationTrack2),
        )

        assertEquals(2, publicationService.fetchPublications().size)

        assertEquals(1, publicationService.fetchLatestPublicationDetails(1).size)
        assertEquals(publish2.publicationId, publicationService.fetchLatestPublicationDetails(1)[0].id)

        assertEquals(2, publicationService.fetchLatestPublicationDetails(2).size)
        assertEquals(publish1.publicationId, publicationService.fetchLatestPublicationDetails(10)[1].id)

        assertTrue { publicationService.fetchLatestPublicationDetails(0).isEmpty() }
    }

    @Test
    fun `should sort publications by header column`() {
        clearPublicationTables()

        val trackNumberId1 = insertNewTrackNumber(TrackNumber("1234"), true).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId1))

        val trackNumberId2 = insertNewTrackNumber(TrackNumber("4321"), true).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId2))

        val rows1 = publicationService.fetchPublicationDetails(
            sortBy = PublicationTableColumn.NAME,
            translation = localizationService.getLocalization("fi"),
        )

        assertEquals(2, rows1.size)
        assertTrue { rows1[0].name.contains("1234") }

        val rows2 = publicationService.fetchPublicationDetails(
            sortBy = PublicationTableColumn.NAME,
            order = SortOrder.DESCENDING,
            translation = localizationService.getLocalization("fi")
        )

        assertEquals(2, rows2.size)
        assertTrue { rows2[0].name.contains("4321") }

        val rows3 = publicationService.fetchPublicationDetails(
            sortBy = PublicationTableColumn.PUBLICATION_TIME,
            order = SortOrder.ASCENDING,
            translation = localizationService.getLocalization("fi")
        )

        assertEquals(2, rows3.size)
        assertTrue { rows3[0].name.contains("1234") }
    }

    @Test
    fun `switch diff consistently uses segment point for joint location`() {
        clearPublicationTables()

        val trackNumberId = insertNewTrackNumber(TrackNumber("1234"), false).id
        referenceLineDao.insert(
            referenceLine(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(11.0, 0.0)))),
                draft = false,
            )
        )
        val switch = switchDao.insert(
            switch(
                seed = 123,
                joints = listOf(
                    TrackLayoutSwitchJoint(JointNumber(1), Point(4.2, 0.1), null)
                ),
                draft = false,
            )
        )
        val originalAlignment = alignment(
            segment(Point(0.0, 0.0), Point(4.0, 0.0)),
            segment(Point(4.0, 0.0), Point(10.0, 0.0)).copy(
                switchId = switch.id,
                startJointNumber = JointNumber(1),
            ),
        )
        val locationTrack = locationTrackDao.insert(
            locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment), draft = false)
        )
        switchService.saveDraft(
            switchDao.fetch(switch.rowVersion).copy(
                joints = listOf(TrackLayoutSwitchJoint(JointNumber(1), Point(4.1, 0.2), null)),
            ),
        )
        val updatedAlignment = alignment(
            segment(Point(0.1, 0.0), Point(4.1, 0.0)),
            segment(Point(4.1, 0.0), Point(10.1, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1))
        )
        locationTrackService.saveDraft(locationTrackDao.fetch(locationTrack.rowVersion), updatedAlignment)

        publish(publicationService, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))

        val latestPubs = publicationService.fetchLatestPublicationDetails(2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff = publicationService.diffSwitch(
            localizationService.getLocalization("fi"),
            changes.getValue(switch.id),
            latestPub.publicationTime,
            previousPub.publicationTime,
            Operation.MODIFY,
            trackNumberDao.fetchTrackNumberNames()
        ) { _, _ -> null }
        assertEquals(2, diff.size)
        assertEquals(
            listOf("switch-joint-location", "switch-track-address").sorted(),
            diff.map { it.propKey.key.toString() }.sorted()
        )
        val jointLocationDiff = diff.find { it.propKey.key.toString() == "switch-joint-location" }!!
        assertEquals("4.000 E, 0.000 N", jointLocationDiff.value.oldValue)
        assertEquals("4.100 E, 0.000 N", jointLocationDiff.value.newValue)
        val trackAddressDiff = diff.find { it.propKey.key.toString() == "switch-track-address" }!!
        assertEquals("0000+0004.100", trackAddressDiff.value.newValue)
    }

    private data class TopologicalSwitchConnectionTestData(
        val locationTracksUnderTest: List<Pair<IntId<LocationTrack>, LocationTrack>>,
        val switchIdsUnderTest: List<IntId<TrackLayoutSwitch>>,
    )

    private fun getTopologicalSwitchConnectionTestData(): TopologicalSwitchConnectionTestData {
        val topologyStartSwitch = createSwitchWithJoints(
            name = "Topological switch connection test start switch",
            jointPositions = listOf(
                JointNumber(1) to Point(0.0, 0.0),
                JointNumber(3) to Point(1.0, 0.0),
            ),
            draft = true,
        )

        val topologyEndSwitch = createSwitchWithJoints(
            name = "Topological switch connection test end switch",
            jointPositions = listOf(
                JointNumber(1) to Point(2.0, 0.0),
                JointNumber(3) to Point(3.0, 0.0),
            ),
            draft = true,
        )

        val topologyStartSwitchId = switchDao.insert(topologyStartSwitch).id
        val topologyEndSwitchId = switchDao.insert(topologyEndSwitch).id

        val locationTrackAlignment = alignment(segment(Point(1.0, 0.0), Point(2.0, 0.0)))
        val locationTracksUnderTest = getTopologicalSwitchConnectionTestCases(
            ::getUnusedTrackNumberId,
            TopologyLocationTrackSwitch(topologyStartSwitchId, JointNumber(1)),
            TopologyLocationTrackSwitch(topologyEndSwitchId, JointNumber(3)),
        )

        val locationTrackIdsUnderTest = locationTracksUnderTest.map { locationTrack ->
            locationTrack.copy(alignmentVersion = alignmentDao.insert(locationTrackAlignment))
        }.map { locationTrack ->
            locationTrackDao.insert(asMainDraft(locationTrack)).id to locationTrack
        }

        return TopologicalSwitchConnectionTestData(
            locationTracksUnderTest = locationTrackIdsUnderTest,
            switchIdsUnderTest = listOf(topologyStartSwitchId, topologyEndSwitchId),
        )
    }

    private fun getLocationTrackValidationResult(
        locationTrackId: IntId<LocationTrack>,
        stagedSwitches: List<IntId<TrackLayoutSwitch>> = listOf(),
    ): LocationTrackPublicationCandidate {
        val publicationRequestIds = PublicationRequestIds(
            trackNumbers = listOf(),
            locationTracks = listOf(locationTrackId),
            referenceLines = listOf(),
            switches = stagedSwitches,
            kmPosts = listOf(),
        )

        val validationResult = publicationService.validateAsPublicationUnit(
            publicationService.collectPublicationCandidates().filter(publicationRequestIds),
            allowMultipleSplits = false,
        )

        return validationResult.locationTracks.find { lt -> lt.id == locationTrackId }!!
    }

    private fun switchAlignmentNotConnectedTrackValidationError(locationTrackNames: String, switchName: String) =
        PublicationValidationError(
            PublicationValidationErrorType.WARNING,
            "validation.layout.location-track.switch-linkage.switch-alignment-not-connected",
            mapOf("locationTracks" to locationTrackNames, "switch" to switchName)
        )

    private fun switchNotPublishedError(switchName: String) = PublicationValidationError(
        PublicationValidationErrorType.ERROR,
        "validation.layout.location-track.switch.not-published",
        mapOf("switch" to switchName)
    )

    private fun switchFrontJointNotConnectedError(switchName: String) = PublicationValidationError(
        PublicationValidationErrorType.WARNING,
        "validation.layout.location-track.switch-linkage.front-joint-not-connected",
        mapOf("switch" to switchName)
    )

    private fun assertValidationErrorsForEach(
        expecteds: List<List<PublicationValidationError>>,
        actuals: List<List<PublicationValidationError>>,
    ) {
        assertEquals(expecteds.size, actuals.size, "size equals")
        expecteds.forEachIndexed { i, expected ->
            assertValidationErrorContentEquals(expected, actuals[i], i)
        }
    }

    private fun assertValidationErrorContentEquals(
        expected: List<PublicationValidationError>,
        actual: List<PublicationValidationError>,
        index: Int,
    ) {
        val allKeys = expected.map { it.localizationKey.toString() } + actual.map { it.localizationKey.toString() }
        val commonPrefix = allKeys.reduce { acc, next ->
            acc.take(acc.zip(next) { a, b -> a == b }.takeWhile { it }.count())
        }

        fun cleanupKey(key: LocalizationKey) = key.toString().let { k ->
            if (commonPrefix.length > 3) "...$k" else k
        }

        assertEquals(
            expected.map { cleanupKey(it.localizationKey) }.sorted(),
            actual.map { cleanupKey(it.localizationKey) }.sorted(),
            "same errors by localization key, index $index, ",
        )

        val expectedByKey = expected.sortedBy { it.toString() }.groupBy { it.localizationKey }
        val actualByKey = actual.sortedBy { it.toString() }.groupBy { it.localizationKey }
        expectedByKey.keys.forEach { key ->
            assertEquals(
                expectedByKey[key]!!.map { it.params },
                actualByKey[key]!!.map { it.params },
                "params for key $key at index $index, ",
            )
            assertEquals(
                expectedByKey[key]!!.map { it.type },
                actualByKey[key]!!.map { it.type },
                "level for key $key at index $index, ",
            )
        }
    }

    private val topoTestDataContextOnLocationTrackValidationError = listOf(
        validationError("validation.layout.location-track.no-context"),
    )
    private val topoTestDataStartSwitchNotPublishedError = switchNotPublishedError(
        "Topological switch connection test start switch"
    )
    private val topoTestDataStartSwitchJointsNotConnectedError = switchAlignmentNotConnectedTrackValidationError(
        "1-5-2, 1-3",
        "Topological switch connection test start switch",
    )
    private val topoTestDataEndSwitchNotPublishedError =
        switchNotPublishedError("Topological switch connection test end switch")
    private val topoTestDataEndSwitchJointsNotConnectedError = switchAlignmentNotConnectedTrackValidationError(
        "1-5-2, 1-3",
        "Topological switch connection test end switch",
    )
    private val topoTestDataEndSwitchFrontJointNotConnectedError = switchFrontJointNotConnectedError(
        "Topological switch connection test end switch"
    )

    @Test
    fun `Location track validation should fail for unofficial and unstaged topologically linked switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()
        val noStart = listOf(
            topoTestDataStartSwitchNotPublishedError,
            // no error about no track continuing from the front joint, because a track in fact does continue from it
        )
        val noEnd = listOf(
            topoTestDataEndSwitchNotPublishedError,
        )
        val expected = listOf(
            topoTestDataContextOnLocationTrackValidationError,
            topoTestDataContextOnLocationTrackValidationError + noStart,
            topoTestDataContextOnLocationTrackValidationError + noEnd,
            topoTestDataContextOnLocationTrackValidationError + noStart + noEnd
        )
        val actual = topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
            getLocationTrackValidationResult(locationTrackId).errors
        }
        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Location track validation should succeed for unofficial, but staged topologically linked switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()
        val noStart = listOf(
            topoTestDataStartSwitchJointsNotConnectedError,
            // no error about no track continuing from the front joint, because a track in fact does continue from it
        )
        val noEnd = listOf(
            topoTestDataEndSwitchJointsNotConnectedError,
            topoTestDataEndSwitchFrontJointNotConnectedError,
        )
        val expected = listOf(
            topoTestDataContextOnLocationTrackValidationError,
            topoTestDataContextOnLocationTrackValidationError + noStart,
            topoTestDataContextOnLocationTrackValidationError + noEnd,
            topoTestDataContextOnLocationTrackValidationError + noStart + noEnd
        )
        val actual = topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
            getLocationTrackValidationResult(locationTrackId, topologyTestData.switchIdsUnderTest).errors
        }

        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Location track validation should succeed for topologically linked official switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()

        val noStart = listOf(
            topoTestDataStartSwitchJointsNotConnectedError,
            // no error about no track continuing from the front joint, because a track in fact does continue from it
        )
        val noEnd = listOf(
            topoTestDataEndSwitchJointsNotConnectedError,
            topoTestDataEndSwitchFrontJointNotConnectedError,
        )
        val expected = listOf(
            topoTestDataContextOnLocationTrackValidationError,
            topoTestDataContextOnLocationTrackValidationError + noStart,
            topoTestDataContextOnLocationTrackValidationError + noEnd,
            topoTestDataContextOnLocationTrackValidationError + noStart + noEnd
        )

        publish(publicationService, switches = topologyTestData.switchIdsUnderTest)
        val actual = topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
            getLocationTrackValidationResult(locationTrackId).errors
        }
        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Switch validation checks duplicate tracks through non-math joints`() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = switchService.saveDraft(
            switch(
                seed = 123,
                structureId = switchStructureDao
                    .fetchSwitchStructures()
                    .find { ss -> ss.type.typeName == "KRV43-233-1:9" }!!.id as IntId,
                stateCategory = LayoutStateCategory.EXISTING,
                draft = true,
            )
        ).id
        val locationTrack1 = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(
                segment(Point(0.0, 0.0), Point(2.0, 2.0)),
                segment(Point(2.0, 2.0), Point(5.0, 5.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(5),
                ),
                segment(Point(5.0, 5.0), Point(8.0, 8.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(5),
                    endJointNumber = JointNumber(2),
                ),
                segment(Point(8.0, 8.0), Point(10.0, 10.0)),
            ),
        )

        fun otherAlignment() = alignment(
            segment(Point(10.0, 0.0), Point(8.0, 2.0)),
            segment(Point(8.0, 2.0), Point(5.0, 5.0)).copy(
                switchId = switchId,
                startJointNumber = JointNumber(4),
                endJointNumber = JointNumber(5),
            ),
            segment(Point(5.0, 5.0), Point(2.0, 8.0)).copy(
                switchId = switchId,
                startJointNumber = JointNumber(5),
                endJointNumber = JointNumber(3),
            ),
            segment(Point(2.0, 8.0), Point(0.0, 10.0)),
        )

        val locationTrack2 = locationTrack(trackNumberId, draft = true)
        val locationTrack3 = locationTrack(trackNumberId, draft = true)
        val locationTrack2Id = locationTrackService.saveDraft(locationTrack2, otherAlignment())
        val locationTrack3Id = locationTrackService.saveDraft(locationTrack3, otherAlignment())

        val validated = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            publicationRequestIds(
                locationTracks = listOf(locationTrack1.id, locationTrack2Id.id, locationTrack3Id.id),
                switches = listOf(switchId),
            ),
        )
        val switchValidation = validated.validatedAsPublicationUnit.switches[0].errors
        assertContains(
            switchValidation,
            PublicationValidationError(
                PublicationValidationErrorType.WARNING,
                "validation.layout.switch.track-linkage.multiple-tracks-through-joint",
                mapOf(
                    "locationTracks" to "3 (${locationTrack2.name}, ${locationTrack3.name}), 4 (${locationTrack2.name}, ${locationTrack3.name})",
                    "switch" to "TV123",
                ),
            ),
        )
    }

    @Test
    fun `Switch validation requires a track to continue from the front joint`() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = switchService.saveDraft(
            switch(
                seed = 123,
                structureId = switchStructureYV60_300_1_9().id as IntId,
                stateCategory = LayoutStateCategory.EXISTING,
                draft = true,
            )
        ).id
        val trackOn152Alignment = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(
                segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(5),
                ),
                segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(5),
                    endJointNumber = JointNumber(2),
                ),
            ),
        ).id
        val trackOn13Alignment = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(
                segment(Point(0.0, 0.0), Point(10.0, 2.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(5),
                    endJointNumber = JointNumber(3),
                ),
            ),
        ).id

        fun errorsWhenValidatingSwitchWithTracks(vararg locationTracks: IntId<LocationTrack>) =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(),
                publicationRequestIds(
                    locationTracks = locationTracks.toList(),
                    switches = listOf(switchId),
                ),
            ).validatedAsPublicationUnit.switches[0].errors

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment),
            PublicationValidationError(
                PublicationValidationErrorType.WARNING,
                LocalizationKey("validation.layout.switch.track-linkage.front-joint-not-connected"),
                LocalizationParams(mapOf("switch" to "TV123")),
            ),
        )

        val topoTrackMarkedAsDuplicate = locationTrackService.saveDraft(
            locationTrack(
                trackNumberId = trackNumberId,
                topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)),
                duplicateOf = trackOn13Alignment,
                draft = true,
            )
        ).id

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment, topoTrackMarkedAsDuplicate),
            PublicationValidationError(
                PublicationValidationErrorType.WARNING,
                "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected",
                mapOf("switch" to "TV123"),
            ),
        )

        val goodTopoTrack = locationTrackService.saveDraft(
            locationTrack(
                trackNumberId,
                topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)),
                draft = true,
            )
        ).id

        val errors = errorsWhenValidatingSwitchWithTracks(
            trackOn152Alignment,
            trackOn13Alignment,
            topoTrackMarkedAsDuplicate,
            goodTopoTrack,
        )
        assertFalse(
            errors.any { e ->
                e.localizationKey.contains(
                    "validation.layout.switch.track-linkage.front-joint-not-connected"
                ) || e.localizationKey.contains(
                    "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected"
                )
            },
        )
    }

    @Test
    fun `split target location track validation should not fail when the split is still pending`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split target location track validation should fail when the split is still in progress`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.IN_PROGRESS)
        }

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `split target location track validation should fail on failed split`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()

        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.FAILED)
        }

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `split target location track validation should not fail on finished split`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()

        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.DONE)
        }

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split source location track validation should not fail when the split is still pending`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split source location track validation should fail when the split is still in progress`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()

        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.IN_PROGRESS)
        }

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `split source location track validation should fail on failed split`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()

        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.FAILED)
        }

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `split source location track validation should not fail on finished split`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()

        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.DONE)
        }

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split source location track validation should fail if source location track isn't deleted`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup(LayoutState.IN_USE)

        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also(splitDao::get)

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertContains(
            errors,
            PublicationValidationError(
                PublicationValidationErrorType.ERROR,
                LocalizationKey("validation.layout.split.source-not-deleted"),
                LocalizationParams.empty,
            ),
        )
    }

    @Test
    fun `split location track validation should fail if a target is on a different track number`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val startTarget = locationTrackDao.fetch(startTargetTrack.rowVersion)
        locationTrackDao.update(
            startTarget.copy(trackNumberId = getUnusedTrackNumberId())
        )

        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).also(splitDao::get)

        val errors = validateLocationTracks(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)
        assertContains(
            errors,
            PublicationValidationError(
                PublicationValidationErrorType.ERROR,
                LocalizationKey("validation.layout.split.source-and-target-track-numbers-are-different"),
                LocalizationParams(mapOf("trackName" to startTarget.name.toString())),
            ),
        )
    }

    @Test
    fun `km post split validation should fail on unfinished split`() {
        val trackNumberId = insertOfficialTrackNumber()
        val kmPostId = kmPostDao.insert(kmPost(trackNumberId = trackNumberId, km = KmNumber.ZERO, draft = true)).id
        val locationTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false),
            alignment(),
        ).id

        saveSplit(locationTrackId)

        val validation = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            publicationRequestIds(kmPosts = listOf(kmPostId)),
        )

        val errors = validation.validatedAsPublicationUnit.kmPosts.flatMap { it.errors }

        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `reference line split validation should fail on unfinished split`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val referenceLineVersion = insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment,
        ).rowVersion

        referenceLineDao.fetch(referenceLineVersion).also(referenceLineService::saveDraft)

        val locationTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment,
        ).id

        saveSplit(locationTrackId)

        val validation = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            publicationRequestIds(referenceLines = listOf(referenceLineVersion.id))
        )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.errors }

        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `reference line split validation should fail on failed splitting`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val referenceLineVersion = insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment,
        ).rowVersion
        val locationTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment,
        ).id

        referenceLineDao.fetch(referenceLineVersion).also(referenceLineService::saveDraft)

        saveSplit(locationTrackId).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.FAILED)
        }

        val validation = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            publicationRequestIds(referenceLines = listOf(referenceLineVersion.id))
        )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.errors }

        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `reference line split validation should not fail on finished splitting`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val referenceLineVersion = insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment,
        ).rowVersion
        val locationTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment,
        ).id

        referenceLineDao.fetch(referenceLineVersion).also(referenceLineService::saveDraft)

        saveSplit(locationTrackId).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.DONE)
        }

        val validation = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            publicationRequestIds(referenceLines = listOf(referenceLineVersion.id))
        )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.errors }

        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split geometry validation should fail on geometry changes in source track`() {
        val trackNumberId = insertOfficialTrackNumber()

        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrackVersion = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        ).rowVersion

        alignmentDao
            .insert(alignment(segment(Point(0.0, 0.0), Point(5.0, 5.0), Point(10.0, 0.0))))
            .also { newAlignment ->
                val lt = locationTrackDao.fetch(sourceTrackVersion).copy(
                    state = LayoutState.DELETED,
                    alignmentVersion = newAlignment,
                )

                locationTrackService.saveDraft(lt)
            }

        val startTargetTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
        ).id

        val endTargetTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
        ).id

        saveSplit(sourceTrackVersion.id, startTargetTrackId, endTargetTrackId)

        val errors = validateLocationTracks(sourceTrackVersion.id, startTargetTrackId, endTargetTrackId)

        assertTrue {
            errors.any {
                it.localizationKey == LocalizationKey("validation.layout.split.geometry-changed")
            }
        }
    }

    @Test
    fun `split geometry validation should fail on geometry changes in target track`() {
        val trackNumberId = insertOfficialTrackNumber()

        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrackVersion = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        ).rowVersion.also { version ->
            val lt = locationTrackDao.fetch(version).copy(
                state = LayoutState.DELETED
            )

            locationTrackService.saveDraft(lt)
        }

        val startTargetTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 10.0))),
        ).id

        val endTargetTrackId = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
        ).id

        saveSplit(sourceTrackVersion.id, startTargetTrackId, endTargetTrackId)

        val errors = validateLocationTracks(sourceTrackVersion.id, startTargetTrackId, endTargetTrackId)
        assertTrue {
            errors.any {
                it.localizationKey == LocalizationKey("validation.layout.split.geometry-changed")
            }
        }
    }

    @Test
    fun `split validation should fail if the publication unit does not contain source track`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val errors = validateLocationTracks(startTargetTrack.id, endTargetTrack.id)
        assertContains(errors, validationError("validation.layout.split.split-missing-location-tracks"))
    }

    @Test
    fun `split validation should fail if the publication unit does not contain target track`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val errors = validateLocationTracks(startTargetTrack.id, endTargetTrack.id)
        assertContains(errors, validationError("validation.layout.split.split-missing-location-tracks"))
    }

    @Test
    fun `Split validation should respect allowMultipleSplits`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val split = saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id).let(splitDao::getOrThrow)

        val (sourceTrack2, startTargetTrack2, endTargetTrack2) = simpleSplitSetup()
        val split2 = saveSplit(sourceTrack2.id, startTargetTrack2.id, endTargetTrack2.id).let(splitDao::getOrThrow)

        val trackValidationVersions = listOf(
            ValidationVersion(sourceTrack.id, sourceTrack.rowVersion),
            ValidationVersion(sourceTrack2.id, sourceTrack2.rowVersion),
            ValidationVersion(startTargetTrack.id, startTargetTrack.rowVersion),
            ValidationVersion(startTargetTrack2.id, startTargetTrack2.rowVersion),
            ValidationVersion(endTargetTrack.id, endTargetTrack.rowVersion),
            ValidationVersion(endTargetTrack2.id, endTargetTrack2.rowVersion)
        )

        assertContains(
            validateSplitContent(trackValidationVersions, emptyList(), listOf(split, split2), false).map { it.second },
            validationError("validation.layout.split.multiple-splits-not-allowed"),
        )
        assertTrue(
            validateSplitContent(trackValidationVersions, emptyList(), listOf(split, split2), true)
                .map { it.second }
                .none { error -> error == validationError("validation.layout.split.multiple-splits-not-allowed") },
        )
    }

    @Test
    fun `Split validation should fail if switches are missing`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val switch = insertSwitch(switch(name = getUnusedSwitchName().toString(), draft = false))
        val split = saveSplit(
            sourceTrackId = sourceTrack.id,
            targetTrackIds = listOf(startTargetTrack.id, endTargetTrack.id),
            switches = listOf(switch.id),
        ).let(splitDao::getOrThrow)

        val locationTrackValidationVersions = listOf(
            ValidationVersion(sourceTrack.id, sourceTrack.rowVersion),
            ValidationVersion(startTargetTrack.id, startTargetTrack.rowVersion),
            ValidationVersion(endTargetTrack.id, endTargetTrack.rowVersion),
        )

        assertContains(
            validateSplitContent(
                locationTrackValidationVersions,
                emptyList(),
                listOf(split),
                false,
            ).map { it.second },
            validationError("validation.layout.split.split-missing-switches"),
        )
    }

    @Test
    fun `Split validation should fail if only switches are staged`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val switch = insertSwitch(switch(name = getUnusedSwitchName().toString(), draft = false))
        val split = saveSplit(
            sourceTrackId = sourceTrack.id,
            targetTrackIds = listOf(startTargetTrack.id, endTargetTrack.id),
            switches = listOf(switch.id),
        ).let(splitDao::getOrThrow)

        assertContains(
            validateSplitContent(
                emptyList(),
                listOf(ValidationVersion(switch.id, switch.rowVersion)),
                listOf(split),
                false,
            ).map { it.second },
            validationError("validation.layout.split.split-missing-location-tracks"),
        )
    }

    @Test
    fun `Split validation should not fail if switches and location tracks are staged`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val switch = insertSwitch(switch(name = getUnusedSwitchName().toString(), draft = false))
        val split = saveSplit(
            sourceTrackId = sourceTrack.id,
            targetTrackIds = listOf(startTargetTrack.id, endTargetTrack.id),
            switches = listOf(switch.id),
        ).let(splitDao::getOrThrow)

        val locationTrackValidationVersions = listOf(
            ValidationVersion(sourceTrack.id, sourceTrack.rowVersion),
            ValidationVersion(startTargetTrack.id, startTargetTrack.rowVersion),
            ValidationVersion(endTargetTrack.id, endTargetTrack.rowVersion),
        )

        assertEquals(
            0,
            validateSplitContent(
                locationTrackValidationVersions,
                listOf(ValidationVersion(switch.id, switch.rowVersion)),
                listOf(split),
                false,
            ).size,
        )
    }

    private fun validateLocationTracks(vararg locationTracks: IntId<LocationTrack>): List<PublicationValidationError> {
        val publicationRequest = publicationRequestIds(locationTracks = locationTracks.asList())
        val validation = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            publicationRequest,
        )

        return validation.validatedAsPublicationUnit.locationTracks.flatMap { it.errors }
    }

    private fun saveSplit(
        sourceTrackId: IntId<LocationTrack>,
        vararg targetTrackIds: IntId<LocationTrack>,
    ): IntId<Split> {
        return splitDao.saveSplit(
            sourceTrackId,
            targetTrackIds.map { id -> SplitTarget(id, 0..0) },
            listOf(),
            updatedDuplicates = emptyList(),
        )
    }

    private fun saveSplit(
        sourceTrackId: IntId<LocationTrack>,
        targetTrackIds: List<IntId<LocationTrack>>,
        switches: List<IntId<TrackLayoutSwitch>>,
    ): IntId<Split> {
        return splitDao.saveSplit(
            sourceTrackId,
            targetTrackIds.map { id -> SplitTarget(id, 0..0) },
            switches,
            updatedDuplicates = emptyList(),
        )
    }

    private fun simpleSplitSetup(
        sourceLocationTrackState: LayoutState = LayoutState.DELETED,
    ): Triple<DaoResponse<LocationTrack>, DaoResponse<LocationTrack>, DaoResponse<LocationTrack>> {
        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        locationTrackDao
            .fetch(sourceTrack.rowVersion)
            .copy(state = sourceLocationTrackState)
            .also(locationTrackService::saveDraft)

        val startTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0)))
        )

        val endTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0)))
        )

        return Triple(sourceTrack, startTrack, endTrack)
    }

    @Test
    fun `Location track validation catches only switch topology errors related to its own changes`() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = switchDao.insert(
            switch(
                seed = 123,
                structureId = switchStructureYV60_300_1_9().id as IntId,
                draft = false,
            ).copy(stateCategory = LayoutStateCategory.EXISTING)
        ).id
        val officialTrackOn152 = locationTrackDao.insert(
            locationTrack(
                trackNumberId = trackNumberId,
                alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(Point(0.0, 5.0), Point(0.0, 0.0)),
                        segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                            switchId = switchId,
                            startJointNumber = JointNumber(1),
                            endJointNumber = JointNumber(5),
                        ),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                            switchId = switchId,
                            startJointNumber = JointNumber(5),
                            endJointNumber = JointNumber(2),
                        ),
                    )
                ),
                draft = false,
            )
        )
        val officialTrackOn13 = locationTrackDao.insert(
            locationTrack(
                trackNumberId = trackNumberId,
                alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(Point(0.0, 0.0), Point(10.0, 2.0)).copy(
                            switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(3)
                        ),
                    )
                ),
                draft = false,
            )
        )
        locationTrackService.saveDraft(
            locationTrackDao.fetch(officialTrackOn152.rowVersion).copy(state = LayoutState.DELETED)
        )
        locationTrackService.saveDraft(
            locationTrackDao.fetch(officialTrackOn13.rowVersion).copy(state = LayoutState.DELETED)
        )

        val errorsWhenDeletingStraightTrack = getLocationTrackValidationResult(officialTrackOn152.id).errors
        assertTrue(errorsWhenDeletingStraightTrack.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("locationTracks") == "1-5-2" && error.params.get("switch") == "TV123"
        })
        assertTrue(errorsWhenDeletingStraightTrack.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.front-joint-not-connected") &&
                    error.params.get("switch") == "TV123"
        })

        val errorsWhenDeletingBranchingTrack = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(), publicationRequestIds(
                locationTracks = listOf(officialTrackOn13.id)
            )
        ).validatedAsPublicationUnit.locationTracks[0].errors
        assertTrue(errorsWhenDeletingBranchingTrack.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("locationTracks") == "1-3" && error.params.get("switch") == "TV123"
        })
        assertFalse(errorsWhenDeletingBranchingTrack.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.front-joint-not-connected") })
    }

    @Test
    fun `Location track validation catches track removal causing switches to go unlinked`() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = switchDao.insert(
            switch(
                seed = 123,
                structureId = switchStructureYV60_300_1_9().id as IntId,
                stateCategory = LayoutStateCategory.EXISTING,
                draft = false
            )
        ).id
        val officialTrackOn152 = locationTrackDao.insert(
            locationTrack(
                trackNumberId = trackNumberId,
                alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                            switchId = switchId,
                            startJointNumber = JointNumber(1),
                            endJointNumber = JointNumber(5),
                        ),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                            switchId = switchId,
                            startJointNumber = JointNumber(5),
                            endJointNumber = JointNumber(2),
                        ),
                    )
                ),
                draft = false,
            )
        )
        locationTrackService.saveDraft(
            locationTrackDao.fetch(officialTrackOn152.rowVersion).copy(state = LayoutState.DELETED)
        )
        locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(Point(0.0, 0.0), Point(10.0, 2.0)).copy(
                            switchId = switchId,
                            startJointNumber = JointNumber(1),
                            endJointNumber = JointNumber(3),
                        ),
                    )
                ),
                draft = false,
            )
        )

        val locationTrackDeletionErrors = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(), publicationRequestIds(
                locationTracks = listOf(officialTrackOn152.id)
            )
        ).validatedAsPublicationUnit.locationTracks[0].errors
        assertTrue(locationTrackDeletionErrors.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("locationTracks") == "1-5-2" && error.params.get("switch") == "TV123"
        })
        // but it's OK if we link a replacement track
        val replacementTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(
                segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(5),
                ),
                segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(5),
                    endJointNumber = JointNumber(2),
                ),
            ),
        )
        val errorsWithReplacementTrackLinked = publicationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(),
            publicationRequestIds(locationTracks = listOf(officialTrackOn152.id, replacementTrack.id)),
        ).validatedAsPublicationUnit.locationTracks[0].errors
        assertFalse(errorsWithReplacementTrackLinked.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected")
        })
    }

    @Test
    fun `Should fetch split details correctly`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val publicationId = publicationService.getValidationVersions(
            publicationRequest(locationTracks = listOf(sourceTrack.id, startTargetTrack.id, endTargetTrack.id))
        ).let { versions ->
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "").publicationId
        }

        val splitInPublication = publicationService.getSplitInPublication(publicationId!!)
        assertNotNull(splitInPublication)
        assertEquals(sourceTrack.id, splitInPublication.locationTrack.id)
        assertEquals(2, splitInPublication.targetLocationTracks.size)
        assertEquals(startTargetTrack.id, splitInPublication.targetLocationTracks[0].id)
        assertEquals(true, splitInPublication.targetLocationTracks[0].newlyCreated)
        assertEquals(endTargetTrack.id, splitInPublication.targetLocationTracks[1].id)
        assertEquals(true, splitInPublication.targetLocationTracks[1].newlyCreated)
    }

    @Test
    fun `Publication group should not be set for assets unrelated to a split`() {
        // Add some additional assets as "noise", other assets should not have publication
        // groups even if there are unpublished splits.
        insertPublicationGroupTestData()

        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val someTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val someDuplicateTrack = insertLocationTrack(
            locationTrack(
                trackNumberId = trackNumberId,
                duplicateOf = someTrack.id,
                draft = true,
            ),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val someTrackIds = listOf(
            someTrack.id,
            someDuplicateTrack.id,
        )

        val someSwitchId = insertUniqueDraftSwitch().id

        val publicationCandidates = publicationService.collectPublicationCandidates()

        publicationCandidates.locationTracks
            .filter { candidate -> candidate.id in someTrackIds }
            .also { filteredCandidates -> assertEquals(2, filteredCandidates.size) }
            .forEach { candidate -> assertEquals(null, candidate.publicationGroup) }

        publicationCandidates.switches
            .filter { candidate -> candidate.id == someSwitchId }
            .also { filteredCandidates -> assertEquals(1, filteredCandidates.size) }
            .forEach { candidate -> assertEquals(null, candidate.publicationGroup) }
    }

    @Test
    fun `Publication group should be set for assets related to a split`() {
        (1..4).forEach { testIndex ->
            val testData = insertPublicationGroupTestData()
            val splits = splitService.findUnfinishedSplitsForLocationTracks(
                listOf(testData.sourceLocationTrackId)
            )

            assertEquals(1, splits.size)
            val splitId = splits[0].id

            val publicationCandidates = publicationService.collectPublicationCandidates()

            val amountOfNonDuplicatesInCurrentTest = 3
            val amountOfDuplicatesInCurrentTest = 5
            val expectedTotalUnpublishedLocationTrackAmount =
                testIndex * (amountOfNonDuplicatesInCurrentTest + amountOfDuplicatesInCurrentTest)

            assertEquals(expectedTotalUnpublishedLocationTrackAmount, publicationCandidates.locationTracks.size)

            publicationCandidates.locationTracks
                .filter { candidate -> candidate.id in testData.allLocationTrackIds }
                .also { filteredCandidates ->
                    assertEquals(
                        amountOfNonDuplicatesInCurrentTest + amountOfDuplicatesInCurrentTest,
                        filteredCandidates.size,
                    )
                }
                .forEach { candidate -> assertEquals(splitId, candidate.publicationGroup?.id) }

            val amountOfSwitchesInCurrentTest = 6
            val expectedTotalUnpublishedSwitchAmount = testIndex * amountOfSwitchesInCurrentTest
            assertEquals(expectedTotalUnpublishedSwitchAmount, publicationCandidates.switches.size)

            publicationCandidates.switches
                .filter { candidate -> candidate.id in testData.switchIds }
                .also { filteredCandidates ->
                    assertEquals(amountOfSwitchesInCurrentTest, filteredCandidates.size)
                }
                .forEach { candidate -> assertEquals(splitId, candidate.publicationGroup?.id) }
        }
    }

    @Test
    fun `Published split should not be deleted when a target location track is later modified and reverted`() {
        val (
            sourceTrack,
            startTargetTrack,
            endTargetTrack
        ) = simpleSplitSetup()

        val splitId = saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        splitDao.get(splitId).let { split ->
            assertNotNull(split)
            assertEquals(null, split.publicationId)
        }

        val publicationId = publicationService.getValidationVersions(
            publicationRequest(locationTracks = listOf(sourceTrack.id, startTargetTrack.id, endTargetTrack.id))
        ).let { versions ->
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "").publicationId
        }

        splitDao.get(splitId).let { split ->
            assertNotNull(split)
            assertEquals(publicationId, split.publicationId)
        }

        val (targetTrackToModify, targetAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, startTargetTrack.id)
        locationTrackService.saveDraft(
            draft = targetTrackToModify.copy(name = AlignmentName("Some other draft name")),
            alignment = targetAlignment,
        )

        publicationService.revertPublicationCandidates(
            publicationRequestIds(locationTracks = listOf(targetTrackToModify.id as IntId)),
        )

        // Split should be found and not be deleted even after reverting the draft change to the modified locationTrack.
        assertNotNull(splitDao.get(splitId))
    }

    @Test
    fun `Published split should not be deleted when a relinked switch is later modified and reverted`() {
        val (
            sourceTrack,
            startTargetTrack,
            endTargetTrack
        ) = simpleSplitSetup()

        val someSwitch = insertUniqueDraftSwitch()

        val splitId = saveSplit(
            sourceTrackId = sourceTrack.id,
            targetTrackIds = listOf(startTargetTrack.id, endTargetTrack.id),
            switches = listOf(someSwitch.id),
        )

        publicationService.getValidationVersions(
            publicationRequest(
                locationTracks = listOf(sourceTrack.id, startTargetTrack.id, endTargetTrack.id),
                switches = listOf(someSwitch.id),
            )
        ).let { versions ->
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "").publicationId
        }

        switchService.get(DRAFT, someSwitch.id).let { publishedSwitch ->
            assertNotNull(publishedSwitch)
            assertEquals(true, publishedSwitch.isOfficial)

            switchService.saveDraft(
                draft = publishedSwitch.copy(
                    name = SwitchName("some other switch name"),
                )
            )
        }

        publicationService.revertPublicationCandidates(
            publicationRequestIds(
                switches = listOf(someSwitch.id)
            )
        )

        // Split should be found and not be deleted even after reverting the draft change to the modified switch.
        assertNotNull(splitDao.get(splitId))
    }

    data class PublicationGroupTestData(
        val sourceLocationTrackId: IntId<LocationTrack>,
        val allLocationTrackIds: List<IntId<LocationTrack>>,
        val duplicateLocationTrackIds: List<IntId<LocationTrack>>,
        val switchIds: List<IntId<TrackLayoutSwitch>>,
    )

    private fun insertPublicationGroupTestData(): PublicationGroupTestData {
        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        // Due to using splitDao.saveSplit and not actually running a split,
        // the sourceTrack is created as a draft as well.
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val middleTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(2.0, 0.0), Point(3.0, 0.0))),
        )

        val endTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
        )

        val someSwitches = (0..5).map {
            insertUniqueDraftSwitch().id
        }

        val someDuplicates = (0..4).map { i ->
            insertLocationTrack(
                locationTrack(
                    trackNumberId = trackNumberId,
                    duplicateOf = sourceTrack.id,
                    draft = true,
                ),
                alignment(segment(Point(i.toDouble(), 0.0), Point(i + 0.75, 0.0))),
            ).id
        }

        splitDao.saveSplit(
            sourceTrack.id,
            listOf(
                SplitTarget(middleTrack.id, 0..0),
                SplitTarget(endTrack.id, 0..0),
            ),
            relinkedSwitches = someSwitches,
            updatedDuplicates = someDuplicates,
        )

        return PublicationGroupTestData(
            sourceLocationTrackId = sourceTrack.id,
            allLocationTrackIds = listOf(
                listOf(
                    sourceTrack.id,
                    middleTrack.id,
                    endTrack.id,
                ),
                someDuplicates,
            ).flatten(),
            switchIds = someSwitches,
            duplicateLocationTrackIds = someDuplicates,
        )
    }
}

private fun publicationRequestIds(
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
): PublicationRequestIds = PublicationRequestIds(trackNumbers, locationTracks, referenceLines, switches, kmPosts)

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
        publicationDetails.indirectChanges.trackNumbers,
    )

    locationTrackEquals(
        calculatedChanges.indirectChanges.locationTrackChanges,
        publicationDetails.indirectChanges.locationTracks,
    )

    calculatedChanges.indirectChanges.switchChanges.forEach { calculatedSwitch ->
        val switch = publicationDetails.indirectChanges.switches.find { s ->
            s.version.id == calculatedSwitch.switchId
        }
        assertNotNull(switch)
        assertEquals(switch.changedJoints, calculatedSwitch.changedJoints)
    }
}

private fun verifyVersions(publicationRequestIds: PublicationRequestIds, validationVersions: ValidationVersions) {
    verifyVersions(publicationRequestIds.trackNumbers, validationVersions.trackNumbers)
    verifyVersions(publicationRequestIds.referenceLines, validationVersions.referenceLines)
    verifyVersions(publicationRequestIds.kmPosts, validationVersions.kmPosts)
    verifyVersions(publicationRequestIds.locationTracks, validationVersions.locationTracks)
    verifyVersions(publicationRequestIds.switches, validationVersions.switches)
}

private fun <T : LayoutAsset<T>> verifyVersions(ids: List<IntId<T>>, versions: List<ValidationVersion<T>>) {
    assertEquals(ids.size, versions.size)
    ids.forEach { id -> assertTrue(versions.any { v -> v.officialId == id }) }
}

private fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublishingWorks(
    dao: S,
    service: LayoutAssetService<T, S>,
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

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> publishAndCheck(
    rowVersion: RowVersion<T>,
    dao: S,
    service: LayoutAssetService<T, S>,
): Pair<RowVersion<T>, T> {
    val draft = dao.fetch(rowVersion)
    val id = draft.id

    assertTrue(id is IntId)
    assertNotEquals(rowVersion, dao.fetchOfficialVersion(id))
    assertEquals(rowVersion, dao.fetchDraftVersion(id))
    assertTrue(draft.isDraft)
    assertEquals(DataType.STORED, draft.dataType)
    assertEquals(
        MainDraftContextData(
            rowId = rowVersion.id,
            officialRowId = draft.contextData.officialRowId,
            designRowId = null,
            dataType = DataType.STORED,
        ),
        draft.contextData,
    )

    val (publishedId, publishedVersion) = service.publish(ValidationVersion(id, rowVersion))
    assertEquals(id, publishedId)
    assertEquals(publishedVersion, dao.fetchOfficialVersionOrThrow(id))
    assertEquals(publishedVersion, dao.fetchDraftVersion(id))
    assertEquals(VersionPair(publishedVersion, null), dao.fetchVersionPair(id))

    val publishedItem = dao.fetch(publishedVersion)
    assertFalse(publishedItem.isDraft)
    assertEquals(id, publishedVersion.id)
    assertEquals(
        MainOfficialContextData(rowId = publishedVersion.id, dataType = DataType.STORED),
        publishedItem.contextData,
    )

    return publishedVersion to publishedItem
}

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublished(
    validationVersions: List<ValidationVersion<T>>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) = validationVersions.forEach { v -> verifyPublished(v, dao, checkMatch) }

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublished(
    validationVersion: ValidationVersion<T>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) {
    val currentOfficialVersion = dao.fetchOfficialVersionOrThrow(validationVersion.officialId)
    val currentDraftVersion = dao.fetchDraftVersionOrThrow(validationVersion.officialId)
    assertEquals(currentDraftVersion.id, currentOfficialVersion.id)
    assertEquals(currentOfficialVersion, currentDraftVersion)
    val draft = dao.fetch(validationVersion.validatedAssetVersion)
    assertTrue(draft.isDraft)
    val official = dao.fetch(currentOfficialVersion)
    assertTrue(official.isOfficial)
    checkMatch(draft, official)
}

private fun getTopologicalSwitchConnectionTestCases(
    trackNumberGenerator: () -> IntId<TrackLayoutTrackNumber>,
    topologyStartSwitch: TopologyLocationTrackSwitch,
    topologyEndSwitch: TopologyLocationTrackSwitch,
): List<LocationTrack> {
    return listOf(
        locationTrack(
            trackNumberId = trackNumberGenerator(),
            draft = true,
        ),

        locationTrack(
            trackNumberId = trackNumberGenerator(),
            topologicalConnectivity = TopologicalConnectivityType.START,
            topologyStartSwitch = topologyStartSwitch,
            draft = true,
        ),

        locationTrack(
            trackNumberId = trackNumberGenerator(),
            topologicalConnectivity = TopologicalConnectivityType.END,
            topologyEndSwitch = topologyEndSwitch,
            draft = true,
        ),

        locationTrack(
            trackNumberId = trackNumberGenerator(),
            topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
            topologyStartSwitch = topologyStartSwitch,
            topologyEndSwitch = topologyEndSwitch,
            draft = true,
        )
    )
}

private fun createSwitchWithJoints(
    name: String,
    jointPositions: List<Pair<JointNumber, Point>>,
    draft: Boolean,
): TrackLayoutSwitch {
    return switch(
        name = name,
        joints = jointPositions.map { (jointNumber, position) ->
            TrackLayoutSwitchJoint(
                number = jointNumber,
                location = position,
                locationAccuracy = null,
            )
        },
        draft = draft,
    )
}
