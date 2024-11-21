package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.error.DuplicateLocationTrackNameInPublicationException
import fi.fta.geoviite.infra.error.DuplicateNameInPublicationException
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTarget
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutAssetService
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.MainOfficialContextData
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.StoredAssetId
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asDesignDraft
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import java.math.BigDecimal
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import publicationRequest
import publish

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationServiceIT
@Autowired
constructor(
    val publicationService: PublicationService,
    val publicationValidationService: PublicationValidationService,
    val publicationLogService: PublicationLogService,
    val publicationTestSupportService: PublicationTestSupportService,
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
    val switchStructureDao: SwitchStructureDao,
    val splitDao: SplitDao,
    val splitService: SplitService,
    val layoutDesignDao: LayoutDesignDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        publicationTestSupportService.cleanupPublicationTables()
    }

    @Test
    fun `Publication ChangeSet is stored and loaded correctly`() {
        val trackNumbers = mainDraftContext.createLayoutTrackNumbers(2)
        val officialTrackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        val switches = mainDraftContext.insertMany(switch(), switch())

        val referenceLines =
            mainDraftContext.insertMany(
                referenceLineAndAlignment(officialTrackNumberId),
                referenceLineAndAlignment(trackNumbers[0].id, segment(Point(1.0, 1.0), Point(2.0, 2.0))),
                referenceLineAndAlignment(trackNumbers[1].id, segment(Point(5.0, 5.0), Point(6.0, 6.0))),
            )

        val locationTracks =
            mainDraftContext.insertMany(
                locationTrackAndAlignment(trackNumbers[0].id),
                locationTrackAndAlignment(trackNumbers[0].id),
            )

        val kmPosts =
            mainDraftContext.insertMany(
                kmPost(trackNumbers[0].id, KmNumber(1)),
                kmPost(trackNumbers[0].id, KmNumber(2)),
            )

        val publicationRequestIds =
            PublicationRequestIds(
                trackNumbers.map { it.id },
                locationTracks.map { it.id },
                referenceLines.map { it.id },
                switches.map { it.id },
                kmPosts.map { it.id },
            )

        val publicationVersions = publicationService.getValidationVersions(LayoutBranch.main, publicationRequestIds)
        val draftCalculatedChanges = publicationTestSupportService.getCalculatedChangesInRequest(publicationVersions)
        val beforeInsert = testDBService.getDbTime()
        val publicationResult =
            publicationTestSupportService.testPublish(LayoutBranch.main, publicationVersions, draftCalculatedChanges)
        val afterInsert = testDBService.getDbTime()
        assertNotNull(publicationResult.publicationId)
        val publish = publicationLogService.getPublicationDetails(publicationResult.publicationId!!)
        assertTrue(publish.publicationTime in beforeInsert..afterInsert)
        assertEqualsCalculatedChanges(draftCalculatedChanges, publish)
    }

    @Test
    fun `Fetching all publication candidates works`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber()

        val switch = mainDraftContext.insert(switch())

        val segment = segment(Point(0.0, 0.0), Point(1.0, 1.0), switchId = switch.id)
        val track1 = mainDraftContext.insert(locationTrackAndAlignment(trackNumber.id, segment))
        val track2 = mainDraftContext.insert(locationTrackAndAlignment(trackNumber.id, name = "TEST-1"))

        val referenceLine = mainDraftContext.insert(referenceLineAndAlignment(trackNumber.id))

        val kmPost = mainDraftContext.insert(kmPost(trackNumber.id, KmNumber.ZERO))

        val candidates = publicationService.collectPublicationCandidates(PublicationInMain)
        assertCandidatesContainCorrectVersions(candidates.switches, switch)
        assertCandidatesContainCorrectVersions(candidates.locationTracks, track1, track2)
        assertCandidatesContainCorrectVersions(candidates.trackNumbers, trackNumber)
        assertCandidatesContainCorrectVersions(candidates.referenceLines, referenceLine)
        assertCandidatesContainCorrectVersions(candidates.kmPosts, kmPost)
    }

    private fun <T> assertCandidatesContainCorrectVersions(
        candidates: List<PublicationCandidate<T>>,
        vararg responses: LayoutRowVersion<T>,
    ) {
        assertEquals(responses.size, candidates.size)
        responses.forEach { response ->
            val candidate = candidates.find { c -> c.id == response.id }
            assertNotNull(candidate)
            assertEquals(response, candidate.rowVersion)
        }
    }

    @Test
    fun `Publication contains correct TrackNumber links for switches`() {
        val switch = mainDraftContext.insert(switch())
        val trackNumberIds =
            listOf(mainOfficialContext.createLayoutTrackNumber().id, mainOfficialContext.createLayoutTrackNumber().id)
        val locationTracks =
            trackNumberIds.map { trackNumberId ->
                val segment = segment(Point(0.0, 0.0), Point(1.0, 1.0), switchId = switch.id)
                mainDraftContext.insert(locationTrackAndAlignment(trackNumberId, segment))
            }

        val publicationResult =
            publish(publicationService, locationTracks = locationTracks.map { it.id }, switches = listOf(switch.id))
        val publish = publicationLogService.getPublicationDetails(publicationResult.publicationId!!)
        assertEquals(
            trackNumberIds.sortedBy { it.intValue },
            publish.switches[0].trackNumberIds.sortedBy { it.intValue },
        )
    }

    @Test
    fun `Publishing ReferenceLine works`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber()
        val draftLine = mainDraftContext.insert(referenceLineAndAlignment(trackNumber.id))
        assertNull(mainOfficialContext.fetch(draftLine.id))
        assertNotNull(mainDraftContext.fetch(draftLine.id))

        val publicationRequest = publicationRequest(referenceLines = listOf(draftLine.id))
        val versions = publicationService.getValidationVersions(LayoutBranch.main, publicationRequest)
        val draftCalculatedChanges = publicationTestSupportService.getCalculatedChangesInRequest(versions)
        val publicationResult =
            publicationTestSupportService.testPublish(LayoutBranch.main, versions, draftCalculatedChanges)
        val publication = publicationLogService.getPublicationDetails(publicationResult.publicationId!!)

        assertNotNull(publicationResult.publicationId)
        assertEquals(0, publicationResult.trackNumbers)
        assertEquals(1, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(mainOfficialContext.fetch(draftLine.id), mainDraftContext.fetch(draftLine.id))

        assertEqualsCalculatedChanges(draftCalculatedChanges, publication)
    }

    @Test
    fun `Publishing reference line change without track number figures out the operation correctly`() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val (line, alignment) = referenceLineAndAlignment(trackNumberId, draft = true)
        val referenceLineId = referenceLineService.saveDraft(LayoutBranch.main, line, alignment).id
        assertNull(mainOfficialContext.fetch(trackNumberId))
        assertNotNull(mainDraftContext.fetch(trackNumberId))
        assertNull(mainOfficialContext.fetch(referenceLineId))
        assertNotNull(mainDraftContext.fetch(referenceLineId))
        // The first publication must be together with the track number
        val publicationRequest =
            publicationRequest(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))
        val versions = publicationService.getValidationVersions(LayoutBranch.main, publicationRequest)
        val draftCalculatedChanges = publicationTestSupportService.getCalculatedChangesInRequest(versions)
        val publication = publicationTestSupportService.testPublish(LayoutBranch.main, versions, draftCalculatedChanges)
        val publicationDetails = publicationLogService.getPublicationDetails(publication.publicationId!!)
        assertEquals(1, publicationDetails.trackNumbers.size)
        assertEquals(1, publicationDetails.referenceLines.size)
        assertEquals(Operation.CREATE, publicationDetails.trackNumbers[0].operation)
        assertEquals(Operation.CREATE, publicationDetails.referenceLines[0].operation)
        val publishedReferenceLine = referenceLineService.get(MainLayoutContext.official, referenceLineId)!!

        // Update can happen independently
        val updateResponse =
            referenceLineService.updateTrackNumberReferenceLine(
                LayoutBranch.main,
                publishedReferenceLine.trackNumberId,
                publishedReferenceLine.startAddress.copy(
                    meters = publishedReferenceLine.startAddress.meters.add(BigDecimal.ONE)
                ),
            )
        val pubReq2 = publicationRequest(referenceLines = listOf(updateResponse!!.id))
        val versions2 = publicationService.getValidationVersions(LayoutBranch.main, pubReq2)
        val draftCalculatedChanges2 = publicationTestSupportService.getCalculatedChangesInRequest(versions2)
        val publication2 =
            publicationTestSupportService.testPublish(LayoutBranch.main, versions2, draftCalculatedChanges2)
        val publicationDetails2 = publicationLogService.getPublicationDetails(publication2.publicationId!!)
        assertEquals(1, publicationDetails2.referenceLines.size)
        assertEquals(Operation.MODIFY, publicationDetails2.referenceLines[0].operation)
    }

    @Test
    fun `Publishing new LocationTrack works`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        mainDraftContext.insert(referenceLine(trackNumber.id), alignment)
        val draftId = mainDraftContext.insert(locationTrack(trackNumber.id), alignment).id
        assertNull(mainOfficialContext.fetch(draftId))
        assertNotNull(mainDraftContext.fetch(draftId))

        val publicationResult = publish(publicationService, locationTracks = listOf(draftId))

        assertNotNull(publicationResult.publicationId)
        assertEquals(0, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(1, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(mainOfficialContext.fetch(draftId), mainDraftContext.fetch(draftId))
    }

    @Test
    fun publishingReferenceLineChangesWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val officialId =
            mainOfficialContext
                .insert(
                    referenceLineAndAlignment(
                        trackNumberId,
                        segment(Point(1.0, 1.0), Point(2.0, 2.0)),
                        startAddress = TrackMeter("0001", 10),
                    )
                )
                .id

        val (tmpLine, tmpAlignment) = referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.draft, officialId)
        referenceLineService.saveDraft(
            LayoutBranch.main,
            tmpLine.copy(startAddress = TrackMeter("0002", 20)),
            tmpAlignment.copy(
                segments =
                    fixSegmentStarts(
                        listOf(segment(Point(1.0, 1.0), Point(2.0, 2.0)), segment(Point(2.0, 2.0), Point(3.0, 3.0)))
                    )
            ),
        )
        assertNotEquals(
            referenceLineService.getOrThrow(MainLayoutContext.official, officialId).startAddress,
            referenceLineService.getOrThrow(MainLayoutContext.draft, officialId).startAddress,
        )

        assertEquals(
            1,
            referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.official, officialId).second.segments.size,
        )
        assertEquals(
            2,
            referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.draft, officialId).second.segments.size,
        )

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(referenceLines = listOf(officialId)),
        )

        assertEquals(
            referenceLineService.getOrThrow(MainLayoutContext.official, officialId).startAddress,
            referenceLineService.getOrThrow(MainLayoutContext.draft, officialId).startAddress,
        )
        assertEquals(
            2,
            referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.official, officialId).second.segments.size,
        )
        assertEquals(
            referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.official, officialId).second.segments,
            referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.draft, officialId).second.segments,
        )
    }

    @Test
    fun publishingLocationTrackChangesWorks() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    lineAlignment = alignment(segment(Point(0.0, 0.0), Point(4.0, 4.0)))
                )
                .id

        val officialId =
            mainOfficialContext
                .insert(
                    locationTrack(trackNumberId = trackNumberId, name = "test 01"),
                    alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0))),
                )
                .id

        val (tmpTrack, tmpAlignment) = locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, officialId)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            tmpTrack.copy(name = AlignmentName("DRAFT test 01")),
            tmpAlignment.copy(
                segments =
                    fixSegmentStarts(
                        listOf(segment(Point(1.0, 1.0), Point(2.0, 2.0)), segment(Point(2.0, 2.0), Point(3.0, 3.0)))
                    )
            ),
        )
        assertNotEquals(
            locationTrackService.getOrThrow(MainLayoutContext.official, officialId).name,
            locationTrackService.getOrThrow(MainLayoutContext.draft, officialId).name,
        )
        assertEquals(
            1,
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.official, officialId).second.segments.size,
        )
        assertEquals(
            2,
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, officialId).second.segments.size,
        )

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(locationTracks = listOf(officialId)),
        )

        assertEquals(
            locationTrackService.getOrThrow(MainLayoutContext.official, officialId).name,
            locationTrackService.getOrThrow(MainLayoutContext.draft, officialId).name,
        )
        assertEquals(
            2,
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.official, officialId).second.segments.size,
        )
        assertEquals(
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.official, officialId).second.segments,
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, officialId).second.segments,
        )
    }

    @Test
    fun publishingNewSwitchWorks() {
        val draftId = switchService.saveDraft(LayoutBranch.main, switch(draft = true)).id
        assertNull(switchService.get(MainLayoutContext.official, draftId))
        assertEquals(draftId, switchService.getOrThrow(MainLayoutContext.draft, draftId).id)

        val publicationResult = publish(publicationService, switches = listOf(draftId))
        assertNotNull(publicationResult.publicationId)
        assertEquals(0, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(1, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            switchService.getOrThrow(MainLayoutContext.official, draftId).name,
            switchService.getOrThrow(MainLayoutContext.draft, draftId).name,
        )
    }

    @Test
    fun publishingSwitchChangesWorks() {
        val officialId =
            switchDao
                .save(
                    switch(draft = false)
                        .copy(name = SwitchName("TST 001"), joints = listOf(switchJoint(1), switchJoint(3)))
                )
                .id

        switchService.saveDraft(
            LayoutBranch.main,
            switchService
                .getOrThrow(MainLayoutContext.draft, officialId)
                .copy(
                    name = SwitchName("DRAFT TST 001"),
                    joints = listOf(switchJoint(2), switchJoint(3), switchJoint(4)),
                ),
        )
        assertNotEquals(
            switchService.getOrThrow(MainLayoutContext.official, officialId).name,
            switchService.getOrThrow(MainLayoutContext.draft, officialId).name,
        )
        assertEquals(2, switchService.getOrThrow(MainLayoutContext.official, officialId).joints.size)
        assertEquals(3, switchService.getOrThrow(MainLayoutContext.draft, officialId).joints.size)

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(switches = listOf(officialId)),
        )

        assertEquals(
            switchService.getOrThrow(MainLayoutContext.official, officialId).name,
            switchService.getOrThrow(MainLayoutContext.draft, officialId).name,
        )
        assertEquals(3, switchService.getOrThrow(MainLayoutContext.official, officialId).joints.size)
        assertEquals(
            switchService.getOrThrow(MainLayoutContext.official, officialId).joints,
            switchService.getOrThrow(MainLayoutContext.draft, officialId).joints,
        )
    }

    @Test
    fun publishingNewTrackNumberWorks() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val draftId = trackNumberService.saveDraft(LayoutBranch.main, trackNumber).id
        assertNull(trackNumberService.get(MainLayoutContext.official, draftId))
        assertEquals(draftId, trackNumberService.getOrThrow(MainLayoutContext.draft, draftId).id)

        val publicationResult = publish(publicationService, trackNumbers = listOf(draftId))

        assertNotNull(publicationResult.publicationId)
        assertEquals(1, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            trackNumberService.getOrThrow(MainLayoutContext.official, draftId).number,
            trackNumberService.getOrThrow(MainLayoutContext.draft, draftId).number,
        )
    }

    @Test
    fun publishingTrackNumberChangesWorks() {
        val officialId =
            trackNumberDao
                .save(
                    trackNumber(draft = false)
                        .copy(
                            number = testDBService.getUnusedTrackNumber(),
                            description = TrackNumberDescription("Test 1"),
                        )
                )
                .id

        trackNumberService.saveDraft(
            LayoutBranch.main,
            trackNumberService
                .get(MainLayoutContext.draft, officialId)!!
                .copy(number = testDBService.getUnusedTrackNumber(), description = TrackNumberDescription("Test 2")),
        )

        assertNotEquals(
            trackNumberService.getOrThrow(MainLayoutContext.official, officialId).number,
            trackNumberService.getOrThrow(MainLayoutContext.draft, officialId).number,
        )

        assertEquals(
            TrackNumberDescription("Test 1"),
            trackNumberService.getOrThrow(MainLayoutContext.official, officialId).description,
        )
        assertEquals(
            TrackNumberDescription("Test 2"),
            trackNumberService.getOrThrow(MainLayoutContext.draft, officialId).description,
        )

        val publicationResult = publish(publicationService, trackNumbers = listOf(officialId))

        assertNotNull(publicationResult.publicationId)
        assertEquals(1, publicationResult.trackNumbers)
        assertEquals(0, publicationResult.referenceLines)
        assertEquals(0, publicationResult.locationTracks)
        assertEquals(0, publicationResult.switches)
        assertEquals(0, publicationResult.kmPosts)

        assertEquals(
            trackNumberService.getOrThrow(MainLayoutContext.official, officialId).number,
            trackNumberService.getOrThrow(MainLayoutContext.draft, officialId).number,
        )

        assertEquals(
            trackNumberService.getOrThrow(MainLayoutContext.official, officialId).description,
            trackNumberService.getOrThrow(MainLayoutContext.draft, officialId).description,
        )
    }

    @Test
    fun publishingTrackNumberWorks() {
        verifyPublishingWorks(
            trackNumberDao,
            trackNumberService,
            { trackNumber(testDBService.getUnusedTrackNumber(), draft = true) },
            { orig -> asMainDraft(orig.copy(description = TrackNumberDescription("${orig.description}_edit"))) },
        )
    }

    @Test
    fun publishingReferenceLineWorks() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        verifyPublishingWorks(
            referenceLineDao,
            referenceLineService,
            { referenceLine(tnId, draft = true) },
            { orig -> asMainDraft(orig.copy(startAddress = TrackMeter(12, 34))) },
        )
    }

    @Test
    fun publishingKmPostWorks() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        verifyPublishingWorks(
            kmPostDao,
            kmPostService,
            { kmPost(tnId, KmNumber(123), draft = true) },
            { orig -> asMainDraft(orig.copy(kmNumber = KmNumber(321))) },
        )
    }

    @Test
    fun publishingLocationTrackWorks() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        verifyPublishingWorks(
            locationTrackDao,
            locationTrackService,
            { locationTrack(tnId, draft = true) },
            { orig ->
                asMainDraft(orig.copy(descriptionBase = LocationTrackDescriptionBase("${orig.descriptionBase}_edit")))
            },
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
        val switch1 = switchService.saveDraft(LayoutBranch.main, switch(draft = true)).id
        val switch2 = switchService.saveDraft(LayoutBranch.main, switch(draft = true)).id

        val revertResult =
            publicationService.revertPublicationCandidates(
                LayoutBranch.main,
                PublicationRequestIds(listOf(), listOf(), listOf(), listOf(switch1), listOf()),
            )

        assertEquals(revertResult.switches, 1)
        assertNull(switchService.get(MainLayoutContext.draft, switch1))
        assertDoesNotThrow { switchService.get(MainLayoutContext.draft, switch2) }
    }

    @Test
    fun `reverting split source track will remove the whole split`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        assertTrue {
            splitDao.fetchUnfinishedSplits(LayoutBranch.main).any { split ->
                split.sourceLocationTrackId == splitSetup.sourceTrack.id
            }
        }

        publicationService.revertPublicationCandidates(
            LayoutBranch.main,
            publicationRequest(locationTracks = listOf(splitSetup.sourceTrack.id)),
        )

        assertTrue {
            splitDao.fetchUnfinishedSplits(LayoutBranch.main).none { split ->
                split.sourceLocationTrackId == splitSetup.sourceTrack.id
            }
        }
    }

    @Test
    fun `reverting one of the split target tracks will remove the whole split`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        val startTargetTrack = splitSetup.targetTracks.first().first
        val endTargetTrack = splitSetup.targetTracks.last().first

        assertTrue {
            splitDao.fetchUnfinishedSplits(LayoutBranch.main).any { split ->
                split.containsLocationTrack(endTargetTrack.id)
            }
        }

        publicationService.revertPublicationCandidates(
            LayoutBranch.main,
            publicationRequest(locationTracks = listOf(startTargetTrack.id)),
        )

        assertTrue {
            splitDao.fetchUnfinishedSplits(LayoutBranch.main).none { split ->
                split.containsLocationTrack(endTargetTrack.id)
            }
        }
    }

    @Test
    fun `publication id should be added to splits that have location tracks published`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        val splitBeforePublish =
            splitDao.fetchUnfinishedSplits(LayoutBranch.main).first { split ->
                split.sourceLocationTrackId == splitSetup.sourceTrack.id
            }

        assertNull(splitBeforePublish.publicationId)

        val publicationId =
            publicationService
                .getValidationVersions(LayoutBranch.main, publicationRequest(locationTracks = splitSetup.trackIds))
                .let { versions ->
                    publicationService
                        .publishChanges(
                            LayoutBranch.main,
                            versions,
                            publicationTestSupportService.getCalculatedChangesInRequest(versions),
                            FreeTextWithNewLines.of(""),
                        )
                        .publicationId
                }

        assertEquals(publicationId, splitDao.getOrThrow(splitBeforePublish.id).publicationId)
    }

    @Test
    fun `split source and target location tracks depend on each other`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        for (id in splitSetup.trackIds) {
            val dependencies =
                publicationService.getRevertRequestDependencies(
                    LayoutBranch.main,
                    publicationRequest(locationTracks = listOf(id)),
                )

            for (otherId in splitSetup.trackIds) {
                assertContains(dependencies.locationTracks, otherId)
            }
        }
    }

    @Test
    fun trackNumberAndReferenceLineChangesDependOnEachOther() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val referenceLine =
            referenceLineService.saveDraft(LayoutBranch.main, referenceLine(trackNumber, draft = true)).id
        val publishBoth = publicationRequest(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine))
        assertEquals(
            publishBoth,
            publicationService.getRevertRequestDependencies(
                LayoutBranch.main,
                publicationRequest(trackNumbers = listOf(trackNumber)),
            ),
        )
        assertEquals(
            publishBoth,
            publicationService.getRevertRequestDependencies(
                LayoutBranch.main,
                publicationRequest(referenceLines = listOf(referenceLine)),
            ),
        )
    }

    @Test
    fun `Assets on draft only track number depend on its reference line`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val referenceLine =
            referenceLineService.saveDraft(LayoutBranch.main, referenceLine(trackNumber, draft = true)).id
        val kmPost = kmPostService.saveDraft(LayoutBranch.main, kmPost(trackNumber, KmNumber(0), draft = true)).id
        val locationTrack =
            locationTrackService.saveDraft(LayoutBranch.main, locationTrack(trackNumber, draft = true)).id
        val publishAll =
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
            )
        assertEquals(
            publishAll,
            publicationService.getRevertRequestDependencies(
                LayoutBranch.main,
                publicationRequest(referenceLines = listOf(referenceLine)),
            ),
        )
    }

    @Test
    fun kmPostsAndLocationTracksDependOnTheirTrackNumber() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val locationTrack =
            locationTrackService.saveDraft(LayoutBranch.main, locationTrack(trackNumber, draft = true)).id
        val kmPost = kmPostService.saveDraft(LayoutBranch.main, kmPost(trackNumber, KmNumber(0), draft = true)).id
        val all =
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                locationTracks = listOf(locationTrack),
                kmPosts = listOf(kmPost),
            )
        assertEquals(
            all,
            publicationService.getRevertRequestDependencies(
                LayoutBranch.main,
                publicationRequest(trackNumbers = listOf(trackNumber)),
            ),
        )
    }

    @Test
    fun `Publication rejects duplicate track number names`() {
        trackNumberDao.save(trackNumber(number = TrackNumber("TN"), draft = false))
        val draftTrackNumberId = trackNumberDao.save(trackNumber(number = TrackNumber("TN"), draft = true)).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.save(referenceLine(draftTrackNumberId, alignmentVersion = someAlignment, draft = true)).id
        val exception =
            assertThrows<DuplicateNameInPublicationException> {
                publish(publicationService, trackNumbers = listOf(draftTrackNumberId))
            }
        assertEquals("error.publication.duplicate-name-on.track-number", exception.localizationKey.toString())
        assertEquals(mapOf("name" to "TN"), exception.localizationParams.params)
    }

    @Test
    fun `Publication rejects duplicate location track names`() {
        val trackNumberId = trackNumberDao.save(trackNumber(number = TrackNumber("TN"), draft = false)).id
        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.save(referenceLine(trackNumberId, alignmentVersion = someAlignment, draft = true)).id

        locationTrackDao.save(
            locationTrack(trackNumberId, name = "LT", alignmentVersion = someAlignment, draft = false)
        )
        val draftLt = locationTrack(trackNumberId, name = "LT", alignmentVersion = someAlignment, draft = true)
        val draftLocationTrackId = locationTrackDao.save(draftLt).id
        val exception =
            assertThrows<DuplicateLocationTrackNameInPublicationException> {
                publish(publicationService, locationTracks = listOf(draftLocationTrackId))
            }
        assertEquals("error.publication.duplicate-name-on.location-track", exception.localizationKey.toString())
        assertEquals(mapOf("locationTrack" to "LT", "trackNumber" to "TN"), exception.localizationParams.params)
    }

    @Test
    fun `Location tracks can be renamed over each other`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.save(referenceLine(trackNumberId, alignmentVersion = someAlignment, draft = true)).id

        val lt1 =
            locationTrack(
                trackNumberId = trackNumberId,
                name = "LT1",
                alignmentVersion = someAlignment,
                externalId = null,
                draft = false,
            )
        val lt1OriginalVersion = locationTrackDao.save(lt1)
        val lt1RenamedDraft =
            locationTrackDao.save(
                asMainDraft(locationTrackDao.fetch(lt1OriginalVersion).copy(name = AlignmentName("LT2")))
            )

        val lt2 =
            locationTrack(
                trackNumberId = trackNumberId,
                name = "LT2",
                alignmentVersion = someAlignment,
                externalId = null,
                draft = false,
            )
        val lt2OriginalVersion = locationTrackDao.save(lt2)
        val lt2RenamedDraft =
            locationTrackDao.save(
                asMainDraft(locationTrackDao.fetch(lt2OriginalVersion).copy(name = AlignmentName("LT1")))
            )

        publish(publicationService, locationTracks = listOf(lt1RenamedDraft.id, lt2RenamedDraft.id))
    }

    @Test
    fun `Publication rejects duplicate switch names`() {
        switchDao.save(switch(name = "SW123", draft = false, stateCategory = EXISTING))
        val draftSwitchId = switchDao.save(switch(name = "SW123", draft = true, stateCategory = EXISTING)).id
        val exception =
            assertThrows<DuplicateNameInPublicationException> {
                publish(publicationService, switches = listOf(draftSwitchId))
            }
        assertEquals("error.publication.duplicate-name-on.switch", exception.localizationKey.toString())
        assertEquals(mapOf("name" to "SW123"), exception.localizationParams.params)
    }

    @Test
    fun `create in design and publish in main`() {
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val designDraftContext = testDBService.testContext(testBranch, DRAFT)
        val trackNumber = designDraftContext.insert(trackNumber()).id
        val alignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLine = designDraftContext.insert(referenceLine(trackNumber, alignmentVersion = alignment)).id
        val locationTrack = designDraftContext.insert(locationTrack(trackNumber, alignmentVersion = alignment)).id
        val kmPost = designDraftContext.insert(kmPost(trackNumber, KmNumber(1), Point(1.0, 1.0))).id
        val switch = designDraftContext.insert(switch()).id

        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )

        trackNumberService.mergeToMainBranch(testBranch, trackNumber)
        referenceLineService.mergeToMainBranch(testBranch, referenceLine)
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        switchService.mergeToMainBranch(testBranch, switch)

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )
    }

    @Test
    fun `create in design and update once in design before publishing in main`() {
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val testDraftContext = testDBService.testContext(testBranch, DRAFT)
        val trackNumber = testDraftContext.insert(trackNumber()).id
        val alignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLine = testDraftContext.insert(referenceLine(trackNumber, alignmentVersion = alignment)).id
        val locationTrack = testDraftContext.insert(locationTrack(trackNumber, alignmentVersion = alignment)).id
        val kmPost = testDraftContext.insert(kmPost(trackNumber, KmNumber(1), Point(1.0, 1.0))).id
        val switch = testDraftContext.insert(switch()).id

        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )

        val testOfficialContext = testDBService.testContext(testBranch, OFFICIAL)
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(trackNumber)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(referenceLine)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(locationTrack)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(kmPost)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(switch)!!, testBranch.designId))
        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )

        trackNumberService.mergeToMainBranch(testBranch, trackNumber)
        referenceLineService.mergeToMainBranch(testBranch, referenceLine)
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        switchService.mergeToMainBranch(testBranch, switch)

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )
    }

    @Test
    fun `create in main and alter in design once before updating main`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val alignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLine = mainOfficialContext.insert(referenceLine(trackNumber, alignmentVersion = alignment)).id
        val locationTrack = mainOfficialContext.insert(locationTrack(trackNumber, alignmentVersion = alignment)).id
        val kmPost = mainOfficialContext.insert(kmPost(trackNumber, KmNumber(1), Point(1.0, 1.0))).id
        val switch = mainOfficialContext.insert(switch()).id

        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val testDraftContext = testDBService.testContext(testBranch, DRAFT)

        testDraftContext.insert(
            asDesignDraft(
                mainOfficialContext.fetch(trackNumber)!!.copy(number = TrackNumber("edited")),
                testBranch.designId,
            )
        )
        testDraftContext.insert(
            asDesignDraft(
                mainOfficialContext.fetch(referenceLine)!!.copy(startAddress = TrackMeter("0001+0123")),
                testBranch.designId,
            )
        )
        testDraftContext.insert(
            asDesignDraft(
                mainOfficialContext.fetch(locationTrack)!!.copy(name = AlignmentName("edited")),
                testBranch.designId,
            )
        )
        testDraftContext.insert(
            asDesignDraft(mainOfficialContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(123)), testBranch.designId)
        )
        testDraftContext.insert(
            asDesignDraft(mainOfficialContext.fetch(switch)!!.copy(name = SwitchName("edited")), testBranch.designId)
        )

        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )
        trackNumberService.mergeToMainBranch(testBranch, trackNumber)
        referenceLineService.mergeToMainBranch(testBranch, referenceLine)
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        switchService.mergeToMainBranch(testBranch, switch)

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )

        assertEquals("edited", mainOfficialContext.fetch(trackNumber)!!.number.toString())
        assertEquals("0001+0123", mainOfficialContext.fetch(referenceLine)!!.startAddress.toString())
        assertEquals("edited", mainOfficialContext.fetch(locationTrack)!!.name.toString())
        assertEquals("0123", mainOfficialContext.fetch(kmPost)!!.kmNumber.toString())
        assertEquals("edited", mainOfficialContext.fetch(switch)!!.name.toString())
    }

    @Test
    fun `create in main and alter in design twice before updating main`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val alignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLine = mainOfficialContext.insert(referenceLine(trackNumber, alignmentVersion = alignment)).id
        val locationTrack = mainOfficialContext.insert(locationTrack(trackNumber, alignmentVersion = alignment)).id
        val kmPost = mainOfficialContext.insert(kmPost(trackNumber, KmNumber(1), Point(1.0, 1.0))).id
        val switch = mainOfficialContext.insert(switch()).id

        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val testDraftContext = testDBService.testContext(testBranch, DRAFT)
        val testOfficialContext = testDBService.testContext(testBranch, OFFICIAL)

        testDraftContext.insert(asDesignDraft(mainOfficialContext.fetch(trackNumber)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(mainOfficialContext.fetch(referenceLine)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(mainOfficialContext.fetch(locationTrack)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(mainOfficialContext.fetch(kmPost)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(mainOfficialContext.fetch(switch)!!, testBranch.designId))

        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )

        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(trackNumber)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(referenceLine)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(locationTrack)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(kmPost)!!, testBranch.designId))
        testDraftContext.insert(asDesignDraft(testOfficialContext.fetch(switch)!!, testBranch.designId))

        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )

        trackNumberService.mergeToMainBranch(testBranch, trackNumber)
        referenceLineService.mergeToMainBranch(testBranch, referenceLine)
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        switchService.mergeToMainBranch(testBranch, switch)

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(
                trackNumbers = listOf(trackNumber),
                referenceLines = listOf(referenceLine),
                kmPosts = listOf(kmPost),
                locationTracks = listOf(locationTrack),
                switches = listOf(switch),
            ),
        )
    }

    @Test
    fun `create linked switch in design and publish`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        mainOfficialContext.insert(
            referenceLine(
                trackNumber,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0)))),
            )
        )
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val testDraftContext = testDBService.testContext(testBranch, DRAFT)

        val switch = testDraftContext.insert(switch()).id

        val locationTrack =
            testDraftContext
                .insert(
                    locationTrackAndAlignment(
                        trackNumber,
                        segment(Point(0.0, 0.0), Point(10.0, 10.0))
                            .copy(startJointNumber = JointNumber(1), switchId = switch),
                    )
                )
                .id

        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(locationTracks = listOf(locationTrack), switches = listOf(switch)),
        )

        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        switchService.mergeToMainBranch(testBranch, switch)

        publicationTestSupportService.publishAndVerify(
            MainBranch.instance,
            publicationRequest(locationTracks = listOf(locationTrack), switches = listOf(switch)),
        )
    }

    @Test
    fun `edit location track linking switch in design and publish`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        mainOfficialContext.insert(
            referenceLine(
                trackNumber,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0)))),
            )
        )
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val testDraftContext = testDBService.testContext(testBranch, DRAFT)

        val switch = mainOfficialContext.insert(switch()).id

        val locationTrack =
            mainOfficialContext
                .insert(
                    locationTrackAndAlignment(
                        trackNumber,
                        segment(Point(0.0, 0.0), Point(10.0, 10.0))
                            .copy(startJointNumber = JointNumber(1), switchId = switch),
                    )
                )
                .id

        val editedAlignmentVersion =
            alignmentDao.insert(
                alignment(
                    segment(Point(0.0, 0.0), Point(10.0, 10.0)).copy(endJointNumber = JointNumber(1), switchId = switch)
                )
            )
        testDraftContext.insert(
            asDesignDraft(
                mainOfficialContext.fetch(locationTrack)!!.copy(alignmentVersion = editedAlignmentVersion),
                testBranch.designId,
            )
        )

        publicationTestSupportService.publishAndVerify(
            testBranch,
            publicationRequest(locationTracks = listOf(locationTrack)),
        )
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        publicationTestSupportService.publishAndVerify(
            MainBranch.instance,
            publicationRequest(locationTracks = listOf(locationTrack)),
        )
    }

    @Test
    fun `Should fetch split details correctly`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        val publicationId =
            publicationService
                .getValidationVersions(LayoutBranch.main, publicationRequest(locationTracks = splitSetup.trackIds))
                .let { versions ->
                    publicationTestSupportService
                        .testPublish(
                            LayoutBranch.main,
                            versions,
                            publicationTestSupportService.getCalculatedChangesInRequest(versions),
                        )
                        .publicationId
                }

        val splitInPublication = publicationLogService.getSplitInPublication(publicationId!!)
        assertNotNull(splitInPublication)
        assertEquals(splitSetup.sourceTrack.id, splitInPublication.locationTrack.id)
        assertEquals(splitSetup.targetTracks.size, splitInPublication.targetLocationTracks.size)
        splitSetup.targetTracks.forEachIndexed { index, (daoResponse, _) ->
            val splitTarget = splitInPublication.targetLocationTracks[index]
            assertEquals(daoResponse.id, splitTarget.id)
            assertEquals(SplitTargetOperation.CREATE, splitTarget.operation)
        }
    }

    @Test
    fun `Publication group should not be set for assets unrelated to a split`() {
        // Add some additional assets as "noise", other assets should not have publication
        // groups even if there are unpublished splits.
        insertPublicationGroupTestData()

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.insert(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val someTrack =
            mainDraftContext.insert(locationTrack(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val someDuplicateTrack =
            mainDraftContext.insert(
                locationTrack(trackNumberId = trackNumberId, duplicateOf = someTrack.id),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )

        val someTrackIds = listOf(someTrack.id, someDuplicateTrack.id)

        val someSwitchId = mainDraftContext.createSwitch().id

        val publicationCandidates = publicationService.collectPublicationCandidates(PublicationInMain)

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
            val splits =
                splitService.findUnfinishedSplits(
                    LayoutBranch.main,
                    locationTrackIds = listOf(testData.sourceLocationTrackId),
                )

            assertEquals(1, splits.size)
            val splitId = splits[0].id

            val publicationCandidates = publicationService.collectPublicationCandidates(PublicationInMain)

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
                .also { filteredCandidates -> assertEquals(amountOfSwitchesInCurrentTest, filteredCandidates.size) }
                .forEach { candidate -> assertEquals(splitId, candidate.publicationGroup?.id) }
        }
    }

    @Test
    fun `Published split should not be deleted when a target location track is later modified and reverted`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()

        val splitId = publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        splitDao.get(splitId).let { split ->
            assertNotNull(split)
            assertEquals(null, split.publicationId)
        }

        val publicationId =
            publicationService
                .getValidationVersions(LayoutBranch.main, publicationRequest(locationTracks = splitSetup.trackIds))
                .let { versions ->
                    publicationTestSupportService
                        .testPublish(
                            LayoutBranch.main,
                            versions,
                            publicationTestSupportService.getCalculatedChangesInRequest(versions),
                        )
                        .publicationId
                }

        splitDao.get(splitId).let { split ->
            assertNotNull(split)
            assertEquals(publicationId, split.publicationId)
        }

        val (targetTrackToModify, targetAlignment) =
            locationTrackService.getWithAlignmentOrThrow(
                MainLayoutContext.draft,
                splitSetup.targetTracks.first().first.id,
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            draftAsset = targetTrackToModify.copy(name = AlignmentName("Some other draft name")),
            alignment = targetAlignment,
        )

        publicationService.revertPublicationCandidates(
            LayoutBranch.main,
            publicationRequestIds(locationTracks = listOf(targetTrackToModify.id as IntId)),
        )

        // Split should be found and not be deleted even after reverting the draft change to the
        // modified locationTrack.
        assertNotNull(splitDao.get(splitId))
    }

    @Test
    fun `Published split should not be deleted when a relinked switch is later modified and reverted`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()

        val someSwitch = mainDraftContext.createSwitch()

        val splitId =
            publicationTestSupportService.saveSplit(
                sourceTrackVersion = splitSetup.sourceTrack,
                targetTracks = splitSetup.targetParams,
                switches = listOf(someSwitch.id),
            )

        publicationService
            .getValidationVersions(
                LayoutBranch.main,
                publicationRequest(locationTracks = splitSetup.trackIds, switches = listOf(someSwitch.id)),
            )
            .let { versions ->
                publicationTestSupportService
                    .testPublish(
                        LayoutBranch.main,
                        versions,
                        publicationTestSupportService.getCalculatedChangesInRequest(versions),
                    )
                    .publicationId
            }

        switchService.get(MainLayoutContext.draft, someSwitch.id).let { publishedSwitch ->
            assertNotNull(publishedSwitch)
            assertEquals(true, publishedSwitch.isOfficial)

            switchService.saveDraft(
                LayoutBranch.main,
                publishedSwitch.copy(name = SwitchName("some other switch name")),
            )
        }

        publicationService.revertPublicationCandidates(
            LayoutBranch.main,
            publicationRequestIds(switches = listOf(someSwitch.id)),
        )

        // Split should be found and not be deleted even after reverting the draft change to the
        // modified switch.
        assertNotNull(splitDao.get(splitId))
    }

    @Test
    fun `switch_location_tracks records when a switch but not its linked location track was edited in design`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val switch = mainOfficialContext.insert(switch()).id
        mainOfficialContext.insert(
            locationTrack(trackNumber),
            alignment(
                segment(Point(0.0, 0.0), Point(1.0, 1.0)).copy(startJointNumber = JointNumber(1), switchId = switch)
            ),
        )
        val designBranch = testDBService.createDesignBranch()
        switchDao.save(asDesignDraft(mainOfficialContext.fetch(switch)!!, designBranch.designId))
        val publicationId =
            publicationTestSupportService
                .publishAndVerify(designBranch, publicationRequestIds(switches = listOf(switch)))
                .publicationId!!
        val changes = publicationDao.fetchPublishedSwitches(publicationId)
        assertEquals(setOf(trackNumber), changes.directChanges[0].trackNumberIds)
    }

    data class PublicationGroupTestData(
        val sourceLocationTrackId: IntId<LocationTrack>,
        val allLocationTrackIds: List<IntId<LocationTrack>>,
        val duplicateLocationTrackIds: List<IntId<LocationTrack>>,
        val switchIds: List<IntId<TrackLayoutSwitch>>,
    )

    private fun insertPublicationGroupTestData(): PublicationGroupTestData {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.insert(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        // Due to using splitDao.saveSplit and not actually running a split,
        // the sourceTrack is created as a draft as well.
        val sourceTrack =
            mainDraftContext.insert(locationTrack(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val middleTrack =
            mainDraftContext.insert(locationTrack(trackNumberId), alignment(segment(Point(2.0, 0.0), Point(3.0, 0.0))))

        val endTrack =
            mainDraftContext.insert(locationTrack(trackNumberId), alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))))

        val someSwitches = (0..5).map { mainDraftContext.createSwitch().id }

        val someDuplicates =
            (0..4).map { i ->
                mainDraftContext
                    .insert(
                        locationTrack(trackNumberId = trackNumberId, duplicateOf = sourceTrack.id),
                        alignment(segment(Point(i.toDouble(), 0.0), Point(i + 0.75, 0.0))),
                    )
                    .id
            }

        splitDao.saveSplit(
            sourceTrack,
            listOf(
                SplitTarget(middleTrack.id, 0..0, SplitTargetOperation.CREATE),
                SplitTarget(endTrack.id, 0..0, SplitTargetOperation.CREATE),
            ),
            relinkedSwitches = someSwitches,
            updatedDuplicates = someDuplicates,
        )

        return PublicationGroupTestData(
            sourceLocationTrackId = sourceTrack.id,
            allLocationTrackIds = listOf(listOf(sourceTrack.id, middleTrack.id, endTrack.id), someDuplicates).flatten(),
            switchIds = someSwitches,
            duplicateLocationTrackIds = someDuplicates,
        )
    }
}

fun publicationRequestIds(
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
): PublicationRequestIds = PublicationRequestIds(trackNumbers, locationTracks, referenceLines, switches, kmPosts)

private fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublishingWorks(
    dao: S,
    service: LayoutAssetService<T, S>,
    create: () -> T,
    mutate: (orig: T) -> T,
) {
    // First id remains official
    val draftVersion1 = service.saveDraft(LayoutBranch.main, create())
    val officialId = draftVersion1.id
    assertEquals(1, draftVersion1.version)

    val officialVersion1 = publishAndCheck(draftVersion1, dao, service).first
    assertEquals(officialId, officialVersion1.id)
    // First publication, so gets version 1
    assertEquals(1, officialVersion1.version)

    val draftVersion2 = service.saveDraft(LayoutBranch.main, mutate(dao.fetch(officialVersion1)))
    assertEquals(officialId, draftVersion2.id)
    assertNotEquals(officialVersion1.rowId, draftVersion2.rowId)
    // a draft already existed before, and was deleted for publication, so this is now version 3
    assertEquals(3, draftVersion2.version)

    val officialVersion2 = publishAndCheck(draftVersion2, dao, service).first
    assertEquals(officialId, officialVersion2.id)
    assertEquals(officialVersion1.rowId, officialVersion2.rowId)
    // second publication, so version 2
    assertEquals(2, officialVersion2.version)
}

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> publishAndCheck(
    rowVersion: LayoutRowVersion<T>,
    dao: S,
    service: LayoutAssetService<T, S>,
): Pair<LayoutRowVersion<T>, T> {
    val draft = dao.fetch(rowVersion)
    val id = draft.id

    assertTrue(id is IntId)
    assertNotEquals(rowVersion, dao.fetchVersion(MainLayoutContext.official, id))
    assertEquals(rowVersion, dao.fetchVersion(MainLayoutContext.draft, id))
    assertTrue(draft.isDraft)
    assertEquals(DataType.STORED, draft.dataType)
    assertEquals(StoredAssetId(rowVersion), draft.contextData.layoutAssetId)

    val published = service.publish(LayoutBranch.main, rowVersion)
    assertEquals(id, published.id)
    assertEquals(published, dao.fetchVersionOrThrow(MainLayoutContext.official, id))
    assertEquals(published, dao.fetchVersion(MainLayoutContext.draft, id))

    val publishedItem = dao.fetch(published)
    assertFalse(publishedItem.isDraft)
    assertEquals(id, published.id)
    assertEquals(id.intValue, published.id.intValue)
    assertEquals(MainOfficialContextData(StoredAssetId(published)), publishedItem.contextData)

    return published to publishedItem
}

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublished(
    layoutBranch: LayoutBranch,
    validationVersions: List<LayoutRowVersion<T>>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) = validationVersions.forEach { v -> verifyPublished(layoutBranch, v, dao, checkMatch) }

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublished(
    layoutBranch: LayoutBranch,
    validationVersion: LayoutRowVersion<T>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) {
    val currentOfficialVersion = dao.fetchVersionOrThrow(layoutBranch.official, validationVersion.id)
    val currentDraftVersion = dao.fetchVersionOrThrow(layoutBranch.draft, validationVersion.id)
    assertEquals(currentOfficialVersion, currentDraftVersion)
    val draft = dao.fetch(validationVersion)
    assertTrue(draft.isDraft)
    val official = dao.fetch(currentOfficialVersion)
    assertTrue(official.isOfficial)
    checkMatch(draft, official)
}
