package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
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
import publish
import publishRequest
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
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        deleteFromTables("publication", "location_track", "location_track_geometry_change_summary")
        deleteFromTables("layout", "switch_joint", "switch", "location_track", "track_number", "reference_line")
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
            referenceLineAndAlignment(trackNumbers[1].id, segment(Point(5.0, 5.0), Point(6.0, 6.0))),
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
            kmPostService.saveDraft(kmPost(trackNumbers[0].id, KmNumber(2))),
        )

        val beforeInsert = getDbTime()
        val publishRequestIds = PublishRequestIds(
            trackNumbers.map { it.id },
            locationTracks.map { it.id },
            referenceLines.map { it.id },
            switches.map { it.id },
            kmPosts.map { it.id },
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
        val track1 = locationTrackService.saveDraft(
            t.copy(
                alignmentVersion = alignmentDao.insert(
                    a.copy(
                        segments = listOf(a.segments[0].copy(switchId = switch.id)),
                    )
                ),
            )
        )
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
            locationTrackService.saveDraft(
                t.copy(
                    alignmentVersion = alignmentDao.insert(a.copy(segments = listOf(a.segments[0].copy(switchId = switch.id))))
                )
            )
        }

        val publishResult = publish(
            publicationService,
            locationTracks = locationTracks.map { it.id },
            switches = listOf(switch.id),
        )
        val publish = publicationService.getPublicationDetails(publishResult.publishId!!)
        assertEquals(trackNumberIds.sortedBy { it.intValue },
            publish.switches[0].trackNumberIds.sortedBy { it.intValue })
    }

    @Test
    fun publishingNewReferenceLineWorks() {
        val (line, alignment) = referenceLineAndAlignment(someTrackNumber())
        val draftId = referenceLineService.saveDraft(line, alignment).id
        assertThrows<NoSuchEntityException> { referenceLineService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, referenceLineService.getOrThrow(DRAFT, draftId).id)

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
            referenceLineService.get(OFFICIAL, draftId)!!.startAddress,
            referenceLineService.get(DRAFT, draftId)!!.startAddress,
        )

        assertEqualsCalculatedChanges(draftCalculatedChanges, publication)
    }

    @Test
    fun `Publishing reference line change without track number figures out the operation correctly`() {
        val (line, alignment) = referenceLineAndAlignment(someTrackNumber())
        val draftId = referenceLineService.saveDraft(line, alignment).id
        assertThrows<NoSuchEntityException> { referenceLineService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, referenceLineService.get(DRAFT, draftId)!!.id)

        val publishRequest = publishRequest(referenceLines = listOf(draftId))
        val versions = publicationService.getValidationVersions(publishRequest)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publication = publicationService.publishChanges(versions, draftCalculatedChanges, "Test")
        val publicationDetails = publicationService.getPublicationDetails(publication.publishId!!)
        assertEquals(1, publicationDetails.referenceLines.size)
        assertEquals(Operation.CREATE, publicationDetails.referenceLines[0].operation)
        val publishedReferenceLine = referenceLineService.get(OFFICIAL, draftId)!!

        val updateResponse = referenceLineService.updateTrackNumberReferenceLine(
            publishedReferenceLine.trackNumberId, publishedReferenceLine.startAddress.copy(
                meters = publishedReferenceLine.startAddress.meters.add(
                    BigDecimal.ONE
                )
            )
        )
        val pubReq2 = publishRequest(referenceLines = listOf(updateResponse!!.id))
        val versions2 = publicationService.getValidationVersions(pubReq2)
        val draftCalculatedChanges2 = getCalculatedChangesInRequest(versions2)
        val publication2 = publicationService.publishChanges(versions2, draftCalculatedChanges2, "Test 2")
        val publicationDetails2 = publicationService.getPublicationDetails(publication2.publishId!!)
        assertEquals(1, publicationDetails2.referenceLines.size)
        assertEquals(Operation.MODIFY, publicationDetails2.referenceLines[0].operation)
    }

    @Test
    fun publishingNewLocationTrackWorks() {
        val (track, alignment) = locationTrackAndAlignment(
            insertOfficialTrackNumber(), segment(Point(0.0, 0.0), Point(1.0, 1.0))
        )
        referenceLineService.saveDraft(referenceLine(track.trackNumberId), alignment)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.get(DRAFT, draftId)!!.id)

        val publishResult = publish(publicationService, locationTracks = listOf(draftId))

        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(1, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            locationTrackService.get(OFFICIAL, draftId)!!.name,
            locationTrackService.get(DRAFT, draftId)!!.name,
        )
    }

    @Test
    fun publishingReferenceLineChangesWorks() {
        val alignmentVersion = alignmentDao.insert(alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0))))
        val line = referenceLine(
            someTrackNumber(), alignmentDao.fetch(alignmentVersion), startAddress = TrackMeter("0001", 10)
        ).copy(alignmentVersion = alignmentVersion)
        val officialId = referenceLineDao.insert(line).id

        val (tmpLine, tmpAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId)
        referenceLineService.saveDraft(
            tmpLine.copy(startAddress = TrackMeter("0002", 20)), tmpAlignment.copy(
                segments = fixSegmentStarts(
                    listOf(
                        segment(Point(1.0, 1.0), Point(2.0, 2.0)),
                        segment(Point(2.0, 2.0), Point(3.0, 3.0)),
                    )
                )
            )
        )
        assertNotEquals(
            referenceLineService.get(OFFICIAL, officialId)!!.startAddress,
            referenceLineService.get(DRAFT, officialId)!!.startAddress,
        )

        assertEquals(1, referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        publishAndVerify(publishRequest(referenceLines = listOf(officialId)))

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
        val track =
            locationTrack(insertOfficialTrackNumber(), alignmentDao.fetch(alignmentVersion), name = "test 01").copy(
                alignmentVersion = alignmentVersion
            )

        val (newDraftId, newDraftVersion) = referenceLineService.saveDraft(
            referenceLine(track.trackNumberId), referenceAlignment
        )
        referenceLineService.publish(ValidationVersion(newDraftId, newDraftVersion))

        val officialId = locationTrackDao.insert(track).id

        val (tmpTrack, tmpAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId)
        locationTrackService.saveDraft(
            tmpTrack.copy(name = AlignmentName("DRAFT test 01")), tmpAlignment.copy(
                segments = fixSegmentStarts(
                    listOf(
                        segment(Point(1.0, 1.0), Point(2.0, 2.0)),
                        segment(Point(2.0, 2.0), Point(3.0, 3.0)),
                    )
                )
            )
        )
        assertNotEquals(
            locationTrackService.get(OFFICIAL, officialId)!!.name,
            locationTrackService.get(DRAFT, officialId)!!.name,
        )
        assertEquals(1, locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        publishAndVerify(publishRequest(locationTracks = listOf(officialId)))

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
        val draftId = switchService.saveDraft(switch(123)).id
        assertNull(switchService.get(OFFICIAL, draftId))
        assertEquals(draftId, switchService.get(DRAFT, draftId)!!.id)

        val publishResult = publish(publicationService, switches = listOf(draftId))
        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(1, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            switchService.get(OFFICIAL, draftId)!!.name,
            switchService.get(DRAFT, draftId)!!.name,
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

        publishAndVerify(publishRequest(switches = listOf(officialId)))

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
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val draftId = trackNumberService.saveDraft(trackNumber).id
        assertNull(trackNumberService.get(OFFICIAL, draftId))
        assertEquals(draftId, trackNumberService.get(DRAFT, draftId)!!.id)

        val publishResult = publish(publicationService, trackNumbers = listOf(draftId))

        assertNotNull(publishResult.publishId)
        assertEquals(1, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            trackNumberService.get(OFFICIAL, draftId)!!.number,
            trackNumberService.get(DRAFT, draftId)!!.number,
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
            trackNumberService.get(DRAFT, officialId)!!.copy(
                number = getUnusedTrackNumber(),
                description = FreeText("Test 2"),
            )
        )

        assertNotEquals(
            trackNumberService.get(OFFICIAL, officialId)!!.number, trackNumberService.get(DRAFT, officialId)!!.number
        )

        assertEquals(FreeText("Test 1"), trackNumberService.get(OFFICIAL, officialId)!!.description)
        assertEquals(FreeText("Test 2"), trackNumberService.get(DRAFT, officialId)!!.description)

        val publishResult = publish(publicationService, trackNumbers = listOf(officialId))

        assertNotNull(publishResult.publishId)
        assertEquals(1, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

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
            referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        )
        val (track, alignment) = locationTrackAndAlignment(trackNumberId)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.get(DRAFT, draftId)!!.id)

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
            { orig -> draft(orig.copy(descriptionBase = FreeText("${orig.descriptionBase}_edit"))) },
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

        publicationService.revertPublishCandidates(publishRequest(locationTracks = listOf(sourceTrack.id)))

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

        publicationService.revertPublishCandidates(publishRequest(locationTracks = listOf(startTargetTrack.id)))

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

        val publishId = publicationService.getValidationVersions(
            publishRequest(locationTracks = listOf(sourceTrack.id, startTargetTrack.id, endTargetTrack.id))
        ).let { versions ->
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "").publishId
        }

        assertEquals(publishId, splitDao.getOrThrow(splitBeforePublish.id).publicationId)
    }


    @Test
    fun `split source and target location tracks depend on each other`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val sourceDependencies = publicationService.getRevertRequestDependencies(
            publishRequest(locationTracks = listOf(sourceTrack.id))
        )

        val startDependencies = publicationService.getRevertRequestDependencies(
            publishRequest(locationTracks = listOf(startTargetTrack.id))
        )

        assertContains(
            sourceDependencies.locationTracks,
            sourceTrack.id
        )

        assertContains(
            sourceDependencies.locationTracks,
            startTargetTrack.id
        )

        assertContains(
            sourceDependencies.locationTracks,
            endTargetTrack.id
        )

        assertContains(
            startDependencies.locationTracks,
            sourceTrack.id
        )

        assertContains(
            startDependencies.locationTracks,
            startTargetTrack.id
        )

        assertContains(
            startDependencies.locationTracks,
            endTargetTrack.id
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
    fun `Assets on draft only track number depend on its reference line`() {
        val trackNumber = insertDraftTrackNumber()
        val referenceLine = referenceLineService.saveDraft(referenceLine(trackNumber)).id
        val kmPost = kmPostService.saveDraft(kmPost(trackNumber, KmNumber(0))).id
        val locationTrack = locationTrackService.saveDraft(locationTrack(trackNumber)).id
        val publishAll = publishRequest(
            trackNumbers = listOf(trackNumber),
            referenceLines = listOf(referenceLine),
            kmPosts = listOf(kmPost),
            locationTracks = listOf(locationTrack)
        )
        assertEquals(
            publishAll,
            publicationService.getRevertRequestDependencies(publishRequest(referenceLines = listOf(referenceLine)))
        )
    }

    @Test
    fun kmPostsAndLocationTracksDependOnTheirTrackNumber() {
        val trackNumber = insertDraftTrackNumber()
        val locationTrack = locationTrackService.saveDraft(locationTrack(trackNumber)).id
        val kmPost = kmPostService.saveDraft(kmPost(trackNumber, KmNumber(0))).id
        val all = publishRequest(
            trackNumbers = listOf(trackNumber), locationTracks = listOf(locationTrack), kmPosts = listOf(kmPost)
        )
        assertEquals(
            all, publicationService.getRevertRequestDependencies(publishRequest(trackNumbers = listOf(trackNumber)))
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

        val trackNumber1 = trackNumberService.get(OFFICIAL, trackNumber1Id)
        val trackNumber2 = trackNumberService.get(OFFICIAL, trackNumber2Id)
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
        val switchId = switchDao.insert(switch(123)).id

        val validation = publicationService.validateSwitches(listOf(switchId), OFFICIAL)
        assertEquals(1, validation.size)
        assertEquals(3, validation[0].errors.size)
    }

    @Test
    fun `Validating multiple switches should work`() {
        val switchId = switchDao.insert(switch(123)).id
        val switchId2 = switchDao.insert(switch(234)).id
        val switchId3 = switchDao.insert(switch(456)).id

        val validationIds =
            publicationService.validateSwitches(listOf(switchId, switchId2, switchId3), OFFICIAL).map { it.id }
        assertEquals(3, validationIds.size)
        assertContains(validationIds, switchId)
        assertContains(validationIds, switchId2)
        assertContains(validationIds, switchId3)
    }

    @Test
    fun `Validating official km post should work`() {
        val kmPostId = kmPostDao.insert(kmPost(insertOfficialTrackNumber(), km = KmNumber.ZERO)).id

        val validation = publicationService.validateKmPosts(listOf(kmPostId), OFFICIAL).first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Publication validation identifies duplicate names`() {
        trackNumberDao.insert(trackNumber(number = TrackNumber("TN")))
        val draftTrackNumberId = trackNumberDao.insert(draft(trackNumber(number = TrackNumber("TN")))).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLineId =
            referenceLineDao.insert(draft(referenceLine(draftTrackNumberId, alignmentVersion = someAlignment))).id
        locationTrackDao.insert(locationTrack(draftTrackNumberId, name = "LT", alignmentVersion = someAlignment))
        // one new draft location track trying to use an official one's name
        val draftLocationTrackId = locationTrackDao.insert(
            draft(
                locationTrack(
                    draftTrackNumberId, name = "LT", alignmentVersion = someAlignment
                )
            )
        ).id

        // two new location tracks stepping over each other's names
        val newLt =
            draft(locationTrack(draftTrackNumberId, name = "NLT", alignmentVersion = someAlignment, externalId = null))
        val newLocationTrack1 = locationTrackDao.insert(newLt).id
        val newLocationTrack2 = locationTrackDao.insert(newLt).id

        switchDao.insert(switch(123, name = "SW").copy(stateCategory = LayoutStateCategory.EXISTING))
        // one new switch trying to use an official one's name
        val draftSwitchId =
            switchDao.insert(draft(switch(123, name = "SW").copy(stateCategory = LayoutStateCategory.EXISTING))).id

        // two new switches both trying to use the same name
        val newSwitch = draft(switch(124, name = "NSW").copy(stateCategory = LayoutStateCategory.EXISTING))
        val newSwitch1 = switchDao.insert(newSwitch).id
        val newSwitch2 = switchDao.insert(newSwitch).id

        val validation = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(), PublishRequestIds(
                trackNumbers = listOf(draftTrackNumberId),
                locationTracks = listOf(draftLocationTrackId, newLocationTrack1, newLocationTrack2),
                kmPosts = listOf(),
                referenceLines = listOf(referenceLineId),
                switches = listOf(draftSwitchId, newSwitch1, newSwitch2)
            )
        )

        assertEquals(
            listOf(
                PublishValidationError(
                    PublishValidationErrorType.ERROR,
                    "validation.layout.location-track.duplicate-name-official",
                    mapOf("locationTrack" to AlignmentName("LT"), "trackNumber" to TrackNumber("TN"))
                )
            ), validation.validatedAsPublicationUnit.locationTracks.find { lt -> lt.id == draftLocationTrackId }?.errors
        )

        assertEquals(List(2) {
            PublishValidationError(
                PublishValidationErrorType.ERROR,
                "validation.layout.location-track.duplicate-name-draft",
                mapOf("locationTrack" to AlignmentName("NLT"), "trackNumber" to TrackNumber("TN"))
            )
        },
            validation.validatedAsPublicationUnit.locationTracks
                .filter { lt -> lt.name == AlignmentName("NLT") }
                .flatMap { it.errors })

        assertEquals(listOf(
            PublishValidationError(
                PublishValidationErrorType.ERROR,
                "validation.layout.switch.duplicate-name-official",
                mapOf("switch" to SwitchName("SW"))
            )
        ),
            validation.validatedAsPublicationUnit.switches.find { it.name == SwitchName("SW") }?.errors?.filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-official" })

        assertEquals(List(2) {
            PublishValidationError(
                PublishValidationErrorType.ERROR,
                "validation.layout.switch.duplicate-name-draft",
                mapOf("switch" to SwitchName("NSW"))
            )
        },
            validation.validatedAsPublicationUnit.switches
                .filter { it.name == SwitchName("NSW") }
                .flatMap { it.errors }
                .filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-draft" })

        assertEquals(
            listOf(
                PublishValidationError(
                    PublishValidationErrorType.ERROR,
                    "validation.layout.track-number.duplicate-name-official",
                    mapOf("trackNumber" to TrackNumber("TN"))
                )
            ), validation.validatedAsPublicationUnit.trackNumbers[0].errors
        )
    }

    @Test
    fun `Publication rejects duplicate track number names`() {
        trackNumberDao.insert(trackNumber(number = TrackNumber("TN")))
        val draftTrackNumberId = trackNumberDao.insert(draft(trackNumber(number = TrackNumber("TN")))).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.insert(draft(referenceLine(draftTrackNumberId, alignmentVersion = someAlignment))).id
        val exception = assertThrows<DuplicateNameInPublicationException> {
            publish(
                publicationService, trackNumbers = listOf(draftTrackNumberId)
            )
        }
        assertEquals("error.publication.duplicate-name-on.track-number", exception.localizationKey.toString())
        assertEquals(mapOf("name" to "TN"), exception.localizationParams.params)
    }

    @Test
    fun `Publication rejects duplicate location track names`() {
        val trackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"))).id
        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.insert(draft(referenceLine(trackNumberId, alignmentVersion = someAlignment))).id

        val lt = locationTrack(trackNumberId, name = "LT", alignmentVersion = someAlignment, externalId = null)
        locationTrackDao.insert(lt)
        val draftLocationTrackId = locationTrackDao.insert(draft(lt)).id
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
        val trackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"))).id
        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.insert(draft(referenceLine(trackNumberId, alignmentVersion = someAlignment))).id

        val lt1 = locationTrack(trackNumberId, name = "LT1", alignmentVersion = someAlignment, externalId = null)
        val lt1OriginalVersion = locationTrackDao.insert(lt1).rowVersion
        val lt1RenamedDraft =
            locationTrackDao.insert(draft(locationTrackDao.fetch(lt1OriginalVersion).copy(name = AlignmentName("LT2"))))

        val lt2 = locationTrack(trackNumberId, name = "LT2", alignmentVersion = someAlignment, externalId = null)
        val lt2OriginalVersion = locationTrackDao.insert(lt2).rowVersion
        val lt2RenamedDraft =
            locationTrackDao.insert(draft(locationTrackDao.fetch(lt2OriginalVersion).copy(name = AlignmentName("LT1"))))

        publish(publicationService, locationTracks = listOf(lt1RenamedDraft.id, lt2RenamedDraft.id))
    }

    @Test
    fun `Publication rejects duplicate switch names`() {
        switchDao.insert(switch(123, name = "SW123"))
        val draftSwitchId = switchDao.insert(draft(switch(123, name = "SW123"))).id
        val exception = assertThrows<DuplicateNameInPublicationException> {
            publish(publicationService, switches = listOf(draftSwitchId))
        }
        assertEquals("error.publication.duplicate-name-on.switch", exception.localizationKey.toString())
        assertEquals(mapOf("name" to "SW123"), exception.localizationParams.params)
    }

    @Test
    fun `Publication validation rejects duplication by another referencing track`() {
        val trackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"))).id
        val dummyAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))))
        // Initial state, all official: Small duplicates middle, middle and big don't duplicate anything
        val middleTrack = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                name = "middle track",
                alignmentVersion = dummyAlignment,
            )
        )
        val smallTrack = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                name = "small track",
                duplicateOf = middleTrack.id,
                alignmentVersion = dummyAlignment,
            )
        )
        val bigTrack =
            locationTrackDao.insert(locationTrack(trackNumberId, name = "big track", alignmentVersion = dummyAlignment))

        // In new draft, middle wants to duplicate big (leading to: small->middle->big)
        locationTrackService.saveDraft(locationTrackDao.fetch(middleTrack.rowVersion).copy(duplicateOf = bigTrack.id))

        fun getPublishingDuplicateWhileDuplicatedValidationError(vararg publishableTracks: IntId<LocationTrack>): PublishValidationError? {
            val validation = publicationService.validatePublishCandidates(
                publicationService.collectPublishCandidates(),
                PublishRequestIds(
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
        val trackNumber = trackNumberService.get(
            DRAFT, trackNumberService.insert(
                TrackNumberSaveRequest(
                    getUnusedTrackNumber(),
                    FreeText("TEST"),
                    LayoutState.IN_USE,
                    address,
                )
            )
        )
        val rl = referenceLineService.getByTrackNumber(DRAFT, trackNumber!!.id as IntId)!!
        publishAndVerify(
            publishRequest(
                trackNumbers = listOf(trackNumber.id as IntId), referenceLines = listOf(rl.id as IntId)
            )
        )
        trackNumberService.update(
            trackNumber.id as IntId, TrackNumberSaveRequest(
                number = TrackNumber(trackNumber.number.value + " T"),
                description = trackNumber.description + "_TEST",
                startAddress = TrackMeter(0, 0),
                state = LayoutState.NOT_IN_USE,
            )
        )
        publishAndVerify(publishRequest(trackNumbers = listOf(trackNumber.id as IntId)))
        val thisAndPreviousPublication = publicationService.fetchLatestPublicationDetails(2)
        val changes = publicationDao.fetchPublicationTrackNumberChanges(
            thisAndPreviousPublication.first().id, thisAndPreviousPublication.last().publicationTime
        )

        val diff = publicationService.diffTrackNumber(
            localizationService.getLocalization("fi"),
            changes.getValue(trackNumber.id as IntId),
            thisAndPreviousPublication.first().publicationTime,
            thisAndPreviousPublication.last().publicationTime
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
            DRAFT, trackNumberService.insert(
                TrackNumberSaveRequest(
                    getUnusedTrackNumber(),
                    FreeText("TEST"),
                    LayoutState.IN_USE,
                    address,
                )
            )
        )
        val rl = referenceLineService.getByTrackNumber(DRAFT, trackNumber.id as IntId)!!
        publishAndVerify(
            publishRequest(
                trackNumbers = listOf(trackNumber.id as IntId),
                referenceLines = listOf(rl.id as IntId),
            )
        )

        val idOfUpdated = trackNumberService.update(
            trackNumber.id as IntId, TrackNumberSaveRequest(
                number = trackNumber.number,
                description = FreeText("TEST2"),
                startAddress = address,
                state = trackNumber.state,
            )
        )
        publishAndVerify(publishRequest(trackNumbers = listOf(trackNumber.id as IntId)))
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
                    IntId(1)
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
                    IntId(1)
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
                    IntId(1)
                )
            ).rowVersion
        )
        publishAndVerify(
            publishRequest(
                locationTracks = listOf(
                    locationTrack.id as IntId<LocationTrack>,
                    duplicate.id as IntId<LocationTrack>,
                    duplicate2.id as IntId<LocationTrack>
                )
            )
        )

        val updatedLocationTrack = locationTrackDao.fetch(
            locationTrackService.update(
                locationTrack.id as IntId, LocationTrackSaveRequest(
                    name = AlignmentName("TEST2"),
                    descriptionBase = FreeText("Test2"),
                    descriptionSuffix = DescriptionSuffixType.SWITCH_TO_BUFFER,
                    type = LocationTrackType.SIDE,
                    state = LayoutState.NOT_IN_USE,
                    trackNumberId = locationTrack.trackNumberId,
                    duplicate2.id as IntId<LocationTrack>,
                    topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                    IntId(1)
                )
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
        )
        val draftOnlyId = locationTrackService.saveDraft(draft(draftOnlyTrack), draftOnlyAlignment).id

        val (duplicateTrack, duplicateAlignment) = locationTrackAndAlignment(
            trackNumberId = trackNumberId,
            segments = listOf(someSegment()),
            duplicateOf = draftOnlyId,
        )
        val duplicateId = locationTrackService.saveDraft(draft(duplicateTrack), duplicateAlignment).id

        // Both tracks in validation set: this is fine
        assertFalse(containsDuplicateOfNotPublishedError(
            validateLocationTrack(toValidate = duplicateId, duplicateId, draftOnlyId)
        ))
        // Only the target (main) track in set: this is also fine
        assertFalse(containsDuplicateOfNotPublishedError(
            validateLocationTrack(toValidate = draftOnlyId, draftOnlyId)
        ))
        // Only the duplicate track in set: this would result in official referring to draft through duplicateOf
        assertTrue(containsDuplicateOfNotPublishedError(
            validateLocationTrack(toValidate = duplicateId, duplicateId)
        ))
    }

    private fun containsDuplicateOfNotPublishedError(errors: List<PublishValidationError>) =
        containsError(errors, "validation.layout.location-track.duplicate-of.not-published")

    private fun containsError(errors: List<PublishValidationError>, key: String) =
        errors.any { e -> e.localizationKey.toString() == key }

    private fun validateLocationTrack(
        toValidate: IntId<LocationTrack>,
        vararg publicationSet: IntId<LocationTrack>,
    ): List<PublishValidationError> {
        val candidates = publicationService
            .collectPublishCandidates()
            .filter(publishRequest(locationTracks = publicationSet.toList()))
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
            DRAFT, trackNumberService.insert(
                trackNumberSaveReq
            )
        )
        val trackNumber2 = trackNumberService.getOrThrow(
            DRAFT, trackNumberService.insert(
                trackNumberSaveReq.copy(getUnusedTrackNumber(), FreeText("TEST 2"))
            )
        )

        val kmPost = kmPostService.getOrThrow(
            DRAFT, kmPostService.insertKmPost(
                TrackLayoutKmPostSaveRequest(
                    KmNumber(0),
                    LayoutState.IN_USE,
                    trackNumber.id as IntId,
                )
            )
        )
        publish(
            publicationService,
            kmPosts = listOf(kmPost.id as IntId),
            trackNumbers = listOf(trackNumber.id as IntId, trackNumber2.id as IntId)
        )
        val updatedKmPost = kmPostService.getOrThrow(
            DRAFT, kmPostService.updateKmPost(
                kmPost.id as IntId, TrackLayoutKmPostSaveRequest(
                    KmNumber(1),
                    LayoutState.NOT_IN_USE,
                    trackNumber2.id as IntId,
                )
            )
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
            DRAFT, kmPostService.insertKmPost(saveReq)
        )
        publish(publicationService, kmPosts = listOf(kmPost.id as IntId))
        val updatedKmPost = kmPostService.getOrThrow(
            DRAFT, kmPostService.updateKmPost(
                kmPost.id as IntId, saveReq.copy(kmNumber = KmNumber(1))
            )
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
            DRAFT, switchService.insertSwitch(
                TrackLayoutSwitchSaveRequest(
                    SwitchName("TEST"),
                    IntId(1),
                    LayoutStateCategory.EXISTING,
                    IntId(1),
                    false,
                )
            )
        )
        publish(publicationService, switches = listOf(switch.id as IntId), trackNumbers = listOf(tn1, tn2))
        val updatedSwitch = switchService.getOrThrow(
            DRAFT, switchService.updateSwitch(
                switch.id as IntId, TrackLayoutSwitchSaveRequest(
                    SwitchName("TEST 2"),
                    IntId(2),
                    LayoutStateCategory.FUTURE_EXISTING,
                    IntId(2),
                    true,
                )
            )
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
            DRAFT, switchService.insertSwitch(saveReq)
        )
        publish(publicationService, switches = listOf(switch.id as IntId))
        val updatedSwitch = switchService.getOrThrow(
            DRAFT, switchService.updateSwitch(
                switch.id as IntId, saveReq.copy(name = SwitchName("TEST 2"))
            )
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
                if (switchId == null) segment else segment.copy(
                    switchId = switchId, startJointNumber = JointNumber(1)
                )
            }
        })

    @Test
    fun `Location track switch link changes are reported`() {
        val switchUnlinkedFromTopology =
            switchDao.insert(switch(name = "sw-unlinked-from-topology", externalId = "1.1.1.1.1"))
        val switchUnlinkedFromAlignment =
            switchDao.insert(switch(name = "sw-unlinked-from-alignment", externalId = "1.1.1.1.2"))
        val switchAddedToTopologyStart =
            switchDao.insert(switch(name = "sw-added-to-topo-start", externalId = "1.1.1.1.3"))
        val switchAddedToTopologyEnd = switchDao.insert(switch(name = "sw-added-to-topo-end", externalId = "1.1.1.1.4"))
        val switchAddedToAlignment = switchDao.insert(switch(name = "sw-added-to-alignment", externalId = "1.1.1.1.5"))
        val switchDeleted = switchDao.insert(switch(name = "sw-deleted", externalId = "1.1.1.1.6"))
        val switchMerelyRenamed = switchDao.insert(switch(name = "sw-merely-renamed", externalId = "1.1.1.1.7"))
        val originalSwitchReplacedWithNewSameName =
            switchDao.insert(switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.8"))

        val trackNumberId = getUnusedTrackNumberId()

        val originalLocationTrack = locationTrackService.saveDraft(
            locationTrack(
                trackNumberId,
                topologyStartSwitch = TopologyLocationTrackSwitch(switchUnlinkedFromTopology.id, JointNumber(1))
            ), alignmentWithSwitchLinks(
                switchUnlinkedFromAlignment.id,
                switchDeleted.id,
                switchMerelyRenamed.id,
                originalSwitchReplacedWithNewSameName.id
            )
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
        val newSwitchReplacingOldWithSameName =
            switchService.saveDraft(switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.9"))

        locationTrackService.saveDraft(
            locationTrackDao.fetch(locationTrackDao.fetchVersion(originalLocationTrack.id, OFFICIAL)!!).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyStart.id, JointNumber(1)),
                topologyEndSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyEnd.id, JointNumber(1))
            ), alignmentWithSwitchLinks(
                switchAddedToAlignment.id, switchMerelyRenamed.id, newSwitchReplacingOldWithSameName.id, null
            )
        )
        publish(
            publicationService, locationTracks = listOf(originalLocationTrack.id), switches = listOf(
                switchDeleted.id,
                switchMerelyRenamed.id,
                originalSwitchReplacedWithNewSameName.id,
                newSwitchReplacingOldWithSameName.id,
            )
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
        """.trimIndent().replace("\n", " "), diff[0].remark
        )
    }

    @Test
    fun `Location track geometry changes are reported`() {
        val trackNumberId = insertOfficialTrackNumber()
        fun segmentWithCurveToMaxY(maxY: Double) = segment(
            *(0..10)
                .map { x -> Point(x.toDouble(), (5.0 - (x.toDouble() - 5.0).absoluteValue) / 10.0 * maxY) }
                .toTypedArray())

        val referenceLineAlignment = alignmentDao.insert(alignment(segmentWithCurveToMaxY(0.0)))
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = referenceLineAlignment))

        // track that had bump to y=-10 goes to having a bump to y=10, meaning the length and ends stay the same,
        // but the geometry changes
        val originalAlignment = alignment(segmentWithCurveToMaxY(-10.0))
        val newAlignment = alignment(segmentWithCurveToMaxY(10.0))
        val originalLocationTrack = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(originalAlignment)
            )
        )
        locationTrackService.saveDraft(draft(locationTrackDao.fetch(originalLocationTrack.rowVersion)), newAlignment)
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
        print(diff)
        assertEquals(1, diff.size)
        assertEquals(
            "Muutos välillä 0000+0001-0000+0009, sivusuuntainen muutos 10.0 m",
            diff[0].remark
        )
    }

    @Test
    fun `should filter publication details by dates`() {
        clearPublicationTables()

        val trackNumberId1 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId1), alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        )

        val locationTrack1 = locationTrackAndAlignment(trackNumberId1).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish1 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId1),
            locationTracks = listOf(locationTrack1),
        )

        val trackNumberId2 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId2), alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        )

        val locationTrack2 = locationTrackAndAlignment(trackNumberId2).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish2 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId2),
            locationTracks = listOf(locationTrack2),
        )

        val publication1 = publicationDao.getPublication(publish1.publishId!!)
        val publication2 = publicationDao.getPublication(publish2.publishId!!)

        assertTrue {
            publicationService.fetchPublicationDetailsBetweenInstants(to = publication1.publicationTime).isEmpty()
        }

        assertTrue {
            publicationService.fetchPublicationDetailsBetweenInstants(
                from = publication2.publicationTime.plusMillis(1)
            ).isEmpty()
        }

        assertEquals(
            2, publicationService.fetchPublicationDetailsBetweenInstants(
                from = publication1.publicationTime, to = publication2.publicationTime.plusMillis(1)
            ).size
        )
    }

    @Test
    fun `should fetch latest publications`() {
        clearPublicationTables()

        val trackNumberId1 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId1), alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        )

        val locationTrack1 = locationTrackAndAlignment(trackNumberId1).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish1 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId1),
            locationTracks = listOf(locationTrack1),
        )

        val trackNumberId2 = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId2), alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        )

        val locationTrack2 = locationTrackAndAlignment(trackNumberId2).let { (lt, a) ->
            locationTrackService.saveDraft(lt, a).id
        }

        val publish2 = publish(
            publicationService,
            trackNumbers = listOf(trackNumberId2),
            locationTracks = listOf(locationTrack2),
        )

        assertEquals(2, publicationService.fetchPublications().size)

        assertEquals(1, publicationService.fetchLatestPublicationDetails(1).size)
        assertEquals(publish2.publishId, publicationService.fetchLatestPublicationDetails(1)[0].id)

        assertEquals(2, publicationService.fetchLatestPublicationDetails(2).size)
        assertEquals(publish1.publishId, publicationService.fetchLatestPublicationDetails(10)[1].id)

        assertTrue { publicationService.fetchLatestPublicationDetails(0).isEmpty() }
    }

    @Test
    fun `should sort publications by header column`() {
        clearPublicationTables()

        val trackNumberId1 = insertNewTrackNumber(TrackNumber("1234"), true).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId1), alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId1))

        val trackNumberId2 = insertNewTrackNumber(TrackNumber("4321"), true).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId2), alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId2))

        val rows1 = publicationService.fetchPublicationDetails(
            sortBy = PublicationTableColumn.NAME, translation = localizationService.getLocalization("fi")
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
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(11.0, 0.0))))
            )
        )
        val switch = switchDao.insert(
            switch(123, joints = listOf(
                TrackLayoutSwitchJoint(JointNumber(1), Point(4.2, 0.1), null)
            ))
        )
        val originalAlignment = alignment(
            segment(Point(0.0, 0.0), Point(4.0, 0.0)),
            segment(Point(4.0, 0.0), Point(10.0, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1))
        )
        val locationTrack =
            locationTrackDao.insert(locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment)))
        switchService.saveDraft(switchDao.fetch(switch.rowVersion).copy(joints = listOf(
            TrackLayoutSwitchJoint(JointNumber(1), Point(4.1, 0.2), null)
        )))
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
            name = "Topological switch connection test start switch", jointPositions = listOf(
                JointNumber(1) to Point(0.0, 0.0),
                JointNumber(3) to Point(1.0, 0.0),
            )
        )

        val topologyEndSwitch = createSwitchWithJoints(
            name = "Topological switch connection test end switch", jointPositions = listOf(
                JointNumber(1) to Point(2.0, 0.0), JointNumber(3) to Point(3.0, 0.0)
            )
        )

        val topologyStartSwitchId = switchDao.insert(draft(topologyStartSwitch)).id
        val topologyEndSwitchId = switchDao.insert(draft(topologyEndSwitch)).id

        val locationTrackAlignment = alignment(segment(Point(1.0, 0.0), Point(2.0, 0.0)))
        val locationTracksUnderTest = getTopologicalSwitchConnectionTestCases(
            ::getUnusedTrackNumberId,
            TopologyLocationTrackSwitch(topologyStartSwitchId, JointNumber(1)),
            TopologyLocationTrackSwitch(topologyEndSwitchId, JointNumber(3)),
        )

        val locationTrackIdsUnderTest = locationTracksUnderTest.map { locationTrack ->
            locationTrack.copy(alignmentVersion = alignmentDao.insert(locationTrackAlignment))
        }.map { locationTrack ->
            locationTrackDao.insert(draft(locationTrack)).id to locationTrack
        }

        return TopologicalSwitchConnectionTestData(
            locationTracksUnderTest = locationTrackIdsUnderTest,
            switchIdsUnderTest = listOf(topologyStartSwitchId, topologyEndSwitchId),
        )
    }

    private fun getLocationTrackValidationResult(
        locationTrackId: IntId<LocationTrack>,
        stagedSwitches: List<IntId<TrackLayoutSwitch>> = listOf(),
    ): LocationTrackPublishCandidate {
        val publishRequestIds = PublishRequestIds(
            trackNumbers = listOf(),
            locationTracks = listOf(locationTrackId),
            referenceLines = listOf(),
            switches = stagedSwitches,
            kmPosts = listOf(),
        )

        val validationResult = publicationService.validateAsPublicationUnit(
            publicationService.collectPublishCandidates().filter(publishRequestIds),
            allowMultipleSplits = false,
        )

        return validationResult.locationTracks.find { lt -> lt.id == locationTrackId }!!
    }

    private fun switchAlignmentNotConnectedTrackValidationError(locationTrackNames: String, switchName: String) =
        PublishValidationError(
            PublishValidationErrorType.WARNING,
            "validation.layout.location-track.switch-linkage.switch-alignment-not-connected",
            mapOf("locationTracks" to locationTrackNames, "switch" to switchName)
        )

    private fun switchNotPublishedError(switchName: String) = PublishValidationError(
        PublishValidationErrorType.ERROR,
        "validation.layout.location-track.switch.not-published",
        mapOf("switch" to switchName)
    )

    private fun switchFrontJointNotConnectedError(switchName: String) = PublishValidationError(
        PublishValidationErrorType.WARNING,
        "validation.layout.location-track.switch-linkage.front-joint-not-connected",
        mapOf("switch" to switchName)
    )

    private fun assertValidationErrorsForEach(
        expecteds: List<List<PublishValidationError>>,
        actuals: List<List<PublishValidationError>>,
    ) {
        assertEquals(expecteds.size, actuals.size, "size equals")
        expecteds.forEachIndexed { i, expected ->
            assertValidationErrorContentEquals(expected, actuals[i], i)
        }
    }

    private fun assertValidationErrorContentEquals(expected: List<PublishValidationError>, actual: List<PublishValidationError>, index: Int) {
        val allKeys = expected.map { it.localizationKey.toString() } + actual.map { it.localizationKey.toString() }
        val commonPrefix = allKeys.reduce { acc, next -> acc.take(acc.zip(next) { a, b -> a == b }.takeWhile { it }.count()) }
        fun cleanupKey(key: LocalizationKey) =
            key.toString().let { k -> if (commonPrefix.length > 3) "...$k" else k }

        assertEquals(
            expected.map { cleanupKey(it.localizationKey) }.sorted(),
            actual.map { cleanupKey(it.localizationKey) }.sorted(),
            "same errors by localization key, index $index, ",
        )

        val expectedByKey = expected.sortedBy { it.toString() } .groupBy { it.localizationKey }
        val actualByKey = actual.sortedBy { it.toString() }.groupBy { it.localizationKey }
        expectedByKey.keys.forEach { key ->
            assertEquals(
                expectedByKey[key]!!.map { it.params },
                actualByKey[key]!!.map { it.params }, "params for key $key at index $index, ",
            )
            assertEquals(
                expectedByKey[key]!!.map { it.type },
                actualByKey[key]!!.map { it.type }, "level for key $key at index $index, ",
            )
        }
    }

    private val topoTestDataContextOnLocationTrackValidationError = listOf(PublishValidationError(
        PublishValidationErrorType.ERROR, "validation.layout.location-track.no-context", mapOf()
    ))
    private val topoTestDataStartSwitchNotPublishedError =
        switchNotPublishedError("Topological switch connection test start switch")
    private val topoTestDataStartSwitchJointsNotConnectedError = switchAlignmentNotConnectedTrackValidationError(
        "1-5-2, 1-3", "Topological switch connection test start switch"
    )
    private val topoTestDataEndSwitchNotPublishedError =
        switchNotPublishedError("Topological switch connection test end switch")
    private val topoTestDataEndSwitchJointsNotConnectedError = switchAlignmentNotConnectedTrackValidationError(
        "1-5-2, 1-3", "Topological switch connection test end switch"
    )
    private val topoTestDataEndSwitchFrontJointNotConnectedError =
        switchFrontJointNotConnectedError("Topological switch connection test end switch")

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
        val expected =  listOf(
            topoTestDataContextOnLocationTrackValidationError,
            topoTestDataContextOnLocationTrackValidationError + noStart,
            topoTestDataContextOnLocationTrackValidationError + noEnd,
            topoTestDataContextOnLocationTrackValidationError + noStart + noEnd
        )
        val actual =  topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
            getLocationTrackValidationResult(locationTrackId, topologyTestData.switchIdsUnderTest,).errors
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
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val switchId = switchService.saveDraft(
            switch(
                123,
                switchStructureDao
                    .fetchSwitchStructures()
                    .find { ss -> ss.type.typeName == "KRV43-233-1:9" }!!.id as IntId
            ).copy(stateCategory = LayoutStateCategory.EXISTING)
        ).id
        val locationTrack1 = locationTrackService.saveDraft(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(2.0, 2.0)),
                segment(Point(2.0, 2.0), Point(5.0, 5.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(5)
                ),
                segment(Point(5.0, 5.0), Point(8.0, 8.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(2)
                ),
                segment(Point(8.0, 8.0), Point(10.0, 10.0)),
            )
        )

        fun otherAlignment() = alignment(
            segment(Point(10.0, 0.0), Point(8.0, 2.0)),
            segment(Point(8.0, 2.0), Point(5.0, 5.0)).copy(
                switchId = switchId, startJointNumber = JointNumber(4), endJointNumber = JointNumber(5)
            ),
            segment(Point(5.0, 5.0), Point(2.0, 8.0)).copy(
                switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(3)
            ),
            segment(Point(2.0, 8.0), Point(0.0, 10.0)),
        )

        val locationTrack2 = locationTrack(trackNumberId)
        val locationTrack3 = locationTrack(trackNumberId)
        val locationTrack2Id = locationTrackService.saveDraft(locationTrack2, otherAlignment())
        val locationTrack3Id = locationTrackService.saveDraft(locationTrack3, otherAlignment())

        val validated = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(), publishRequestIds(
                locationTracks = listOf(locationTrack1.id, locationTrack2Id.id, locationTrack3Id.id),
                switches = listOf(switchId),
            )
        )
        val switchValidation = validated.validatedAsPublicationUnit.switches[0].errors
        assertContains(
            switchValidation, PublishValidationError(
                PublishValidationErrorType.WARNING,
                "validation.layout.switch.track-linkage.multiple-tracks-through-joint",
                mapOf("locationTracks" to "3 (${locationTrack2.name}, ${locationTrack3.name}), 4 (${locationTrack2.name}, ${locationTrack3.name})",
                    "switch" to "TV123")
            )
        )
    }

    @Test
    fun `Switch validation requires a track to continue from the front joint`() {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val switchId = switchService.saveDraft(
            switch(
                123,
                switchStructureYV60_300_1_9().id as IntId,
            ).copy(stateCategory = LayoutStateCategory.EXISTING)
        ).id
        val trackOn152Alignment = locationTrackService.saveDraft(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(5)
                ),
                segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(2)
                ),
            )
        ).id
        val trackOn13Alignment = locationTrackService.saveDraft(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(10.0, 2.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(3)
                ),
            )
        ).id

        fun errorsWhenValidatingSwitchWithTracks(vararg locationTracks: IntId<LocationTrack>) =
            publicationService.validatePublishCandidates(
                publicationService.collectPublishCandidates(),
                publishRequestIds(
                    locationTracks = locationTracks.toList(),
                    switches = listOf(switchId),
                ),
            ).validatedAsPublicationUnit.switches[0].errors

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment), PublishValidationError(
                PublishValidationErrorType.WARNING,
                LocalizationKey("validation.layout.switch.track-linkage.front-joint-not-connected"),
                LocalizationParams(mapOf("switch" to "TV123")),
            )
        )

        val topoTrackMarkedAsDuplicate = locationTrackService.saveDraft(
            locationTrack(
                trackNumberId, topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1))
            ).copy(duplicateOf = trackOn13Alignment)
        ).id

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment, topoTrackMarkedAsDuplicate),
            PublishValidationError(
                PublishValidationErrorType.WARNING,
                "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected",
                mapOf("switch" to "TV123")
            )
        )

        val goodTopoTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)))
        ).id

        assertFalse(errorsWhenValidatingSwitchWithTracks(
            trackOn152Alignment, trackOn13Alignment, topoTrackMarkedAsDuplicate, goodTopoTrack
        ).any { e ->
            e.localizationKey.contains("validation.layout.switch.track-linkage.front-joint-not-connected") || e.localizationKey.contains(
                "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected"
            )
        })
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
            PublishValidationError(
                PublishValidationErrorType.ERROR,
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
            PublishValidationError(
                PublishValidationErrorType.ERROR,
                LocalizationKey("validation.layout.split.duplicate-on-different-track-number"),
                LocalizationParams(mapOf("duplicateTrack" to startTarget.name.toString())),
            ),
        )
    }

    @Test
    fun `km post split validation should fail on unfinished split`() {
        val trackNumberId = insertOfficialTrackNumber()
        val kmPostId = kmPostDao.insert(draft(kmPost(trackNumberId = trackNumberId, km = KmNumber.ZERO))).id
        val locationTrackId = insertLocationTrack(locationTrack(trackNumberId = trackNumberId), alignment()).id

        saveSplit(locationTrackId)

        val validation = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(),
            publishRequestIds(kmPosts = listOf(kmPostId)),
        )

        val errors = validation.validatedAsPublicationUnit.kmPosts.flatMap { it.errors }

        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `reference line split validation should fail on unfinished split`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val referenceLineVersion = insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId),
            alignment,
        ).rowVersion

        referenceLineDao.fetch(referenceLineVersion).also(referenceLineService::saveDraft)

        val locationTrackId = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)),
            alignment,
        ).id

        saveSplit(locationTrackId)

        val validation = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(),
            publishRequestIds(referenceLines = listOf(referenceLineVersion.id))
        )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.errors }

        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `reference line split validation should fail on failed splitting`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val referenceLineVersion =
            insertReferenceLine(referenceLine(trackNumberId = trackNumberId), alignment).rowVersion
        val locationTrackId = insertLocationTrack(draft(locationTrack(trackNumberId = trackNumberId)), alignment).id

        referenceLineDao.fetch(referenceLineVersion).also(referenceLineService::saveDraft)

        saveSplit(locationTrackId).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.FAILED)
        }

        val validation = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(),
            publishRequestIds(referenceLines = listOf(referenceLineVersion.id))
        )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.errors }

        assertContains(errors, validationError("validation.layout.split.split-in-progress"))
    }

    @Test
    fun `reference line split validation should not fail on finished splitting`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val referenceLineVersion =
            insertReferenceLine(referenceLine(trackNumberId = trackNumberId), alignment).rowVersion
        val locationTrackId = insertLocationTrack(draft(locationTrack(trackNumberId = trackNumberId)), alignment).id

        referenceLineDao.fetch(referenceLineVersion).also(referenceLineService::saveDraft)

        saveSplit(locationTrackId).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.DONE)
        }

        val validation = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(),
            publishRequestIds(referenceLines = listOf(referenceLineVersion.id))
        )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.errors }

        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split geometry validation should fail on geometry changes in source track`() {
        val trackNumberId = insertOfficialTrackNumber()

        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrackVersion = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        ).rowVersion

        alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(5.0, 5.0), Point(10.0, 0.0))))
            .also { newAlignment ->
                val lt = locationTrackDao.fetch(sourceTrackVersion).copy(
                    state = LayoutState.DELETED,
                    alignmentVersion = newAlignment,
                )

                locationTrackService.saveDraft(lt)
            }

        val startTargetTrackId = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
        ).id

        val endTargetTrackId = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)),
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
            referenceLine(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrackVersion = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        ).rowVersion.also { version ->
            val lt = locationTrackDao.fetch(version).copy(
                state = LayoutState.DELETED
            )

            locationTrackService.saveDraft(lt)
        }

        val startTargetTrackId = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 10.0))),
        ).id

        val endTargetTrackId = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)),
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

        val locationTrackValidationVersions = listOf(
            ValidationVersion(sourceTrack.id, sourceTrack.rowVersion),
            ValidationVersion(sourceTrack2.id, sourceTrack2.rowVersion),
            ValidationVersion(startTargetTrack.id, startTargetTrack.rowVersion),
            ValidationVersion(startTargetTrack2.id, startTargetTrack2.rowVersion),
            ValidationVersion(endTargetTrack.id, endTargetTrack.rowVersion),
            ValidationVersion(endTargetTrack2.id, endTargetTrack2.rowVersion)
        )

        assertContains(validateSplitContent(
            locationTrackValidationVersions,
            emptyList(),
            listOf(split, split2),
            false
        ).map { it.second }, validationError("validation.layout.split.multiple-splits-not-allowed"))
        assertTrue(validateSplitContent(
            locationTrackValidationVersions,
            emptyList(),
            listOf(split, split2),
            true
        ).map { it.second }.none{ error -> error == validationError("validation.layout.split.multiple-splits-not-allowed") })
    }

    @Test
    fun `Split validation should fail if switches are missing`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val switch = insertSwitch(switch(name = getUnusedSwitchName().toString()))
        val split = saveSplit(sourceTrack.id, listOf(startTargetTrack.id, endTargetTrack.id), listOf(switch.id)).let(splitDao::getOrThrow)

        val locationTrackValidationVersions = listOf(
            ValidationVersion(sourceTrack.id, sourceTrack.rowVersion),
            ValidationVersion(startTargetTrack.id, startTargetTrack.rowVersion),
            ValidationVersion(endTargetTrack.id, endTargetTrack.rowVersion),
        )

        assertContains(validateSplitContent(
            locationTrackValidationVersions,
            emptyList(),
            listOf(split),
            false
        ).map { it.second }, validationError("validation.layout.split.split-missing-switches"))
    }

    @Test
    fun `Split validation should fail if only switches are staged`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val switch = insertSwitch(switch(name = getUnusedSwitchName().toString()))
        val split = saveSplit(sourceTrack.id, listOf(startTargetTrack.id, endTargetTrack.id), listOf(switch.id)).let(splitDao::getOrThrow)

        assertContains(validateSplitContent(
            emptyList(),
            listOf(
                ValidationVersion(switch.id, switch.rowVersion)
            ),
            listOf(split),
            false
        ).map { it.second }, validationError("validation.layout.split.split-missing-location-tracks"))
    }

    @Test
    fun `Split validation should not fail if switches and location tracks are staged`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        val switch = insertSwitch(switch(name = getUnusedSwitchName().toString()))
        val split = saveSplit(sourceTrack.id, listOf(startTargetTrack.id, endTargetTrack.id), listOf(switch.id)).let(splitDao::getOrThrow)

        val locationTrackValidationVersions = listOf(
            ValidationVersion(sourceTrack.id, sourceTrack.rowVersion),
            ValidationVersion(startTargetTrack.id, startTargetTrack.rowVersion),
            ValidationVersion(endTargetTrack.id, endTargetTrack.rowVersion),
        )

        assertEquals(0, validateSplitContent(
            locationTrackValidationVersions,
            listOf(
                ValidationVersion(switch.id, switch.rowVersion)
            ),
            listOf(split),
            false
        ).size)
    }

    private fun validateLocationTracks(vararg locationTracks: IntId<LocationTrack>): List<PublishValidationError> {
        val publishRequest = publishRequestIds(locationTracks = locationTracks.asList())
        val validation = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(),
            publishRequest,
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
        )
    }

    private fun simpleSplitSetup(sourceLocationTrackState: LayoutState = LayoutState.DELETED):
            Triple<DaoResponse<LocationTrack>, DaoResponse<LocationTrack>, DaoResponse<LocationTrack>> {
        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        )

        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        )

        locationTrackDao
            .fetch(sourceTrack.rowVersion)
            .copy(state = sourceLocationTrackState)
            .also(locationTrackService::saveDraft)

        val startTrack = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0)))
        )

        val endTrack = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0)))
        )

        return Triple(sourceTrack, startTrack, endTrack)
    }

    @Test
    fun `Location track validation catches only switch topology errors related to its own changes`() {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val switchId = switchDao.insert(
            switch(
                123,
                switchStructureYV60_300_1_9().id as IntId,
            ).copy(stateCategory = LayoutStateCategory.EXISTING)
        ).id
        val officialTrackOn152 = locationTrackDao.insert(
            locationTrack(
                trackNumberId, alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(Point(0.0, 5.0), Point(0.0, 0.0)),
                        segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                            switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(5)
                        ),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                            switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(2)
                        ),
                    )
                )
            )
        )
        val officialTrackOn13 = locationTrackDao.insert(
            locationTrack(
                trackNumberId, alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(Point(0.0, 0.0), Point(10.0, 2.0)).copy(
                            switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(3)
                        ),
                    )
                )
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

        val errorsWhenDeletingBranchingTrack = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(), publishRequestIds(
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
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val switchId = switchDao.insert(
            switch(
                123,
                switchStructureYV60_300_1_9().id as IntId,
            ).copy(stateCategory = LayoutStateCategory.EXISTING)
        ).id
        val officialTrackOn152 = locationTrackDao.insert(
            locationTrack(
                trackNumberId, alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                            switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(5)
                        ),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                            switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(2)
                        ),
                    )
                )
            )
        )
        locationTrackService.saveDraft(
            locationTrackDao.fetch(officialTrackOn152.rowVersion).copy(state = LayoutState.DELETED)
        )
        locationTrackDao.insert(
            locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(alignment(
                segment(Point(0.0, 0.0), Point(10.0, 2.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(3)
                ),
            )
            )))

        val locationTrackDeletionErrors = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(), publishRequestIds(
                locationTracks = listOf(officialTrackOn152.id)
            )
        ).validatedAsPublicationUnit.locationTracks[0].errors
        assertTrue(locationTrackDeletionErrors.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("locationTracks") == "1-5-2" && error.params.get("switch") == "TV123"
        })
        // but it's OK if we link a replacement track
        val replacementTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(5.0, 0.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(5)
                ),
                segment(Point(5.0, 0.0), Point(10.0, 0.0)).copy(
                    switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(2)
                ),
            )
        )
        val errorsWithReplacementTrackLinked = publicationService.validatePublishCandidates(
            publicationService.collectPublishCandidates(), publishRequestIds(
                locationTracks = listOf(officialTrackOn152.id, replacementTrack.id)
            )
        ).validatedAsPublicationUnit.locationTracks[0].errors
        assertFalse(errorsWithReplacementTrackLinked.any { error ->
            error.localizationKey == LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected")
        })
    }

    @Test
    fun `Should fetch split details correctly`() {
        val (sourceTrack, startTargetTrack, endTargetTrack) = simpleSplitSetup()
        saveSplit(sourceTrack.id, startTargetTrack.id, endTargetTrack.id)

        val publishId = publicationService.getValidationVersions(
            publishRequest(locationTracks = listOf(sourceTrack.id, startTargetTrack.id, endTargetTrack.id))
        ).let { versions ->
            publicationService.publishChanges(versions, getCalculatedChangesInRequest(versions), "").publishId
        }

        val splitInPublication = publicationService.getSplitInPublication(publishId!!)
        assertNotNull(splitInPublication)
        assertEquals(sourceTrack.id, splitInPublication.locationTrack.id)
        assertEquals(2, splitInPublication.targetLocationTracks.size)
        assertEquals(startTargetTrack.id, splitInPublication.targetLocationTracks[0].id)
        assertEquals(true, splitInPublication.targetLocationTracks[0].newlyCreated)
        assertEquals(endTargetTrack.id, splitInPublication.targetLocationTracks[1].id)
        assertEquals(true, splitInPublication.targetLocationTracks[1].newlyCreated)
    }
}

private fun publishRequestIds(
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
): PublishRequestIds = PublishRequestIds(trackNumbers, locationTracks, referenceLines, switches, kmPosts)

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
        calculatedChanges.indirectChanges.trackNumberChanges, publicationDetails.indirectChanges.trackNumbers
    )

    locationTrackEquals(
        calculatedChanges.indirectChanges.locationTrackChanges, publicationDetails.indirectChanges.locationTracks
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

private fun getTopologicalSwitchConnectionTestCases(
    trackNumberGenerator: () -> IntId<TrackLayoutTrackNumber>,
    topologyStartSwitch: TopologyLocationTrackSwitch,
    topologyEndSwitch: TopologyLocationTrackSwitch,
): List<LocationTrack> {
    return listOf(
        locationTrack(
            trackNumberId = trackNumberGenerator(),
        ),

        locationTrack(
            trackNumberId = trackNumberGenerator(),
            topologyStartSwitch = topologyStartSwitch,
        ),

        locationTrack(
            trackNumberId = trackNumberGenerator(),
            topologyEndSwitch = topologyEndSwitch,
        ),

        locationTrack(
            trackNumberId = trackNumberGenerator(),
            topologyStartSwitch = topologyStartSwitch,
            topologyEndSwitch = topologyEndSwitch,
        )
    )
}

private fun createSwitchWithJoints(
    name: String,
    jointPositions: List<Pair<JointNumber, Point>>,
): TrackLayoutSwitch {
    return switch(name = name).copy(joints = jointPositions.map { (jointNumber, position) ->
        TrackLayoutSwitchJoint(
            number = jointNumber, location = position, locationAccuracy = null
        )
    })
}
