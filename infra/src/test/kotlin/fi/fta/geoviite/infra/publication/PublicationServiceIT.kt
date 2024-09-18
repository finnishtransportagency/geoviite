package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.DuplicateLocationTrackNameInPublicationException
import fi.fta.geoviite.infra.error.DuplicateNameInPublicationException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTarget
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.split.validateSplitContent
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.DescriptionSuffixType
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutAssetService
import fi.fta.geoviite.infra.tracklayout.LayoutDaoResponse
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.MainDraftContextData
import fi.fta.geoviite.infra.tracklayout.MainOfficialContextData
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.StoredContextIdHolder
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asDesignDraft
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someSegment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.tracklayout.trackNumberSaveRequest
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.SortOrder
import java.math.BigDecimal
import kotlin.math.absoluteValue
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
    val layoutDesignDao: LayoutDesignDao,
    val geographyService: GeographyService,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearPublicationTables()
        testDBService.clearLayoutTables()
        val request =
            publicationService.collectPublicationCandidates(PublicationInMain).let {
                PublicationRequestIds(
                    it.trackNumbers.map(TrackNumberPublicationCandidate::id),
                    it.locationTracks.map(LocationTrackPublicationCandidate::id),
                    it.referenceLines.map(ReferenceLinePublicationCandidate::id),
                    it.switches.map(SwitchPublicationCandidate::id),
                    it.kmPosts.map(KmPostPublicationCandidate::id),
                )
            }
        publicationService.revertPublicationCandidates(LayoutBranch.main, request)
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
        val draftCalculatedChanges = getCalculatedChangesInRequest(publicationVersions)
        val beforeInsert = testDBService.getDbTime()
        val publicationResult = testPublish(LayoutBranch.main, publicationVersions, draftCalculatedChanges)
        val afterInsert = testDBService.getDbTime()
        assertNotNull(publicationResult.publicationId)
        val publish = publicationService.getPublicationDetails(publicationResult.publicationId!!)
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
        vararg responses: LayoutDaoResponse<T>,
    ) {
        assertEquals(responses.size, candidates.size)
        responses.forEach { response ->
            val candidate = candidates.find { c -> c.id == response.id }
            assertNotNull(candidate)
            assertEquals(response.rowVersion, candidate.rowVersion)
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
        val publish = publicationService.getPublicationDetails(publicationResult.publicationId!!)
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
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publicationResult = testPublish(LayoutBranch.main, versions, draftCalculatedChanges)
        val publication = publicationService.getPublicationDetails(publicationResult.publicationId!!)

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
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publication = testPublish(LayoutBranch.main, versions, draftCalculatedChanges)
        val publicationDetails = publicationService.getPublicationDetails(publication.publicationId!!)
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
        val draftCalculatedChanges2 = getCalculatedChangesInRequest(versions2)
        val publication2 = testPublish(LayoutBranch.main, versions2, draftCalculatedChanges2)
        val publicationDetails2 = publicationService.getPublicationDetails(publication2.publicationId!!)
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

        publishAndVerify(LayoutBranch.main, publicationRequest(referenceLines = listOf(officialId)))

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

        publishAndVerify(LayoutBranch.main, publicationRequest(locationTracks = listOf(officialId)))

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
                .insert(
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

        publishAndVerify(LayoutBranch.main, publicationRequest(switches = listOf(officialId)))

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
                .insert(
                    trackNumber(draft = false)
                        .copy(number = testDBService.getUnusedTrackNumber(), description = FreeText("Test 1"))
                )
                .id

        trackNumberService.saveDraft(
            LayoutBranch.main,
            trackNumberService
                .get(MainLayoutContext.draft, officialId)!!
                .copy(number = testDBService.getUnusedTrackNumber(), description = FreeText("Test 2")),
        )

        assertNotEquals(
            trackNumberService.getOrThrow(MainLayoutContext.official, officialId).number,
            trackNumberService.getOrThrow(MainLayoutContext.draft, officialId).number,
        )

        assertEquals(
            FreeText("Test 1"),
            trackNumberService.getOrThrow(MainLayoutContext.official, officialId).description,
        )
        assertEquals(FreeText("Test 2"), trackNumberService.getOrThrow(MainLayoutContext.draft, officialId).description)

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
    fun fetchingPublicationListingWorks() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )
        val (track, alignment) = locationTrackAndAlignment(trackNumberId, draft = true)
        val draftId = locationTrackService.saveDraft(LayoutBranch.main, track, alignment).id
        assertThrows<NoSuchEntityException> {
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.official, draftId)
        }
        assertEquals(draftId, locationTrackService.getOrThrow(MainLayoutContext.draft, draftId).id)

        val publicationCountBeforePublishing = publicationService.fetchPublications(LayoutBranch.main).size

        val publicationResult =
            publish(publicationService, trackNumbers = listOf(trackNumberId), locationTracks = listOf(draftId))

        val publicationCountAfterPublishing = publicationService.fetchPublications(LayoutBranch.main)

        assertEquals(publicationCountBeforePublishing + 1, publicationCountAfterPublishing.size)
        assertEquals(publicationResult.publicationId, publicationCountAfterPublishing.last().id)
    }

    @Test
    fun publishingTrackNumberWorks() {
        verifyPublishingWorks(
            trackNumberDao,
            trackNumberService,
            { trackNumber(testDBService.getUnusedTrackNumber(), draft = true) },
            { orig -> asMainDraft(orig.copy(description = FreeText("${orig.description}_edit"))) },
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
        val splitSetup = simpleSplitSetup()
        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

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
        val splitSetup = simpleSplitSetup()
        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

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
        val splitSetup = simpleSplitSetup()
        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

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
                            getCalculatedChangesInRequest(versions),
                            FreeTextWithNewLines.of(""),
                        )
                        .publicationId
                }

        assertEquals(publicationId, splitDao.getOrThrow(splitBeforePublish.id).publicationId)
    }

    @Test
    fun `split source and target location tracks depend on each other`() {
        val splitSetup = simpleSplitSetup()
        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

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
    fun `should sort publications by publication time in descending order`() {
        val trackNumber1Id = mainDraftContext.createLayoutTrackNumber().id
        val trackNumber2Id = mainDraftContext.createLayoutTrackNumber().id
        val publish1Result =
            publicationRequest(trackNumbers = listOf(trackNumber1Id, trackNumber2Id)).let { r ->
                val versions = publicationService.getValidationVersions(LayoutBranch.main, r)
                testPublish(LayoutBranch.main, versions, getCalculatedChangesInRequest(versions))
            }

        assertEquals(2, publish1Result.trackNumbers)

        val trackNumber1 = trackNumberService.get(MainLayoutContext.official, trackNumber1Id)
        val trackNumber2 = trackNumberService.get(MainLayoutContext.official, trackNumber2Id)
        assertNotNull(trackNumber1)
        assertNotNull(trackNumber2)

        val newTrackNumber1TrackNumber = "${trackNumber1.number} ZZZ"

        trackNumberService.saveDraft(
            LayoutBranch.main,
            trackNumber1.copy(number = TrackNumber(newTrackNumber1TrackNumber)),
        )
        val publish2Result =
            publicationRequest(trackNumbers = listOf(trackNumber1Id)).let { r ->
                val versions = publicationService.getValidationVersions(LayoutBranch.main, r)
                testPublish(LayoutBranch.main, versions, getCalculatedChangesInRequest(versions))
            }

        assertEquals(1, publish2Result.trackNumbers)
    }

    @Test
    fun `Validating official location track should work`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val (locationTrack, alignment) =
            locationTrackAndAlignment(trackNumber, segment(Point(4.0, 4.0), Point(5.0, 5.0)), draft = false)
        val locationTrackId =
            locationTrackDao.insert(locationTrack.copy(alignmentVersion = alignmentDao.insert(alignment)))

        val validation =
            publicationService
                .validateLocationTracks(
                    draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                    listOf(locationTrackId.id),
                )
                .first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official track number should work`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id

        val validation =
            publicationService
                .validateTrackNumbersAndReferenceLines(
                    draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                    listOf(trackNumber),
                )
                .first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official switch should work`() {
        val switchId = switchDao.insert(switch(draft = false, stateCategory = EXISTING)).id

        val validation =
            publicationService.validateSwitches(
                draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                listOf(switchId),
            )
        assertEquals(1, validation.size)
        assertEquals(1, validation[0].errors.size)
    }

    @Test
    fun `Validating multiple switches should work`() {
        val switchId = switchDao.insert(switch(draft = false)).id
        val switchId2 = switchDao.insert(switch(draft = false)).id
        val switchId3 = switchDao.insert(switch(draft = false)).id

        val validationIds =
            publicationService
                .validateSwitches(
                    draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                    listOf(switchId, switchId2, switchId3),
                )
                .map { it.id }
        assertEquals(3, validationIds.size)
        assertContains(validationIds, switchId)
        assertContains(validationIds, switchId2)
        assertContains(validationIds, switchId3)
    }

    @Test
    fun `Validating official km post should work`() {
        val kmPostId =
            kmPostDao
                .insert(kmPost(mainOfficialContext.createLayoutTrackNumber().id, km = KmNumber.ZERO, draft = false))
                .id

        val validation =
            publicationService
                .validateKmPosts(draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main), listOf(kmPostId))
                .first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Publication validation identifies duplicate names`() {
        trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = false))
        val draftTrackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = true)).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLineId =
            referenceLineDao
                .insert(referenceLine(draftTrackNumberId, alignmentVersion = someAlignment, draft = true))
                .id
        locationTrackDao.insert(
            locationTrack(draftTrackNumberId, name = "LT", alignmentVersion = someAlignment, draft = false)
        )
        // one new draft location track trying to use an official one's name
        val draftLocationTrackId =
            locationTrackDao
                .insert(locationTrack(draftTrackNumberId, name = "LT", alignmentVersion = someAlignment, draft = true))
                .id

        // two new location tracks stepping over each other's names
        val newLt =
            locationTrack(
                draftTrackNumberId,
                name = "NLT",
                alignmentVersion = someAlignment,
                externalId = null,
                draft = true,
            )
        val newLocationTrack1 = locationTrackDao.insert(newLt).id
        val newLocationTrack2 = locationTrackDao.insert(newLt).id

        switchDao.insert(switch(name = "SW", stateCategory = LayoutStateCategory.EXISTING, draft = false))
        // one new switch trying to use an official one's name
        val draftSwitchId =
            switchDao.insert(switch(name = "SW", stateCategory = LayoutStateCategory.EXISTING, draft = true)).id

        // two new switches both trying to use the same name
        val newSwitch = switch(name = "NSW", stateCategory = LayoutStateCategory.EXISTING, draft = true)
        val newSwitch1 = switchDao.insert(newSwitch).id
        val newSwitch2 = switchDao.insert(newSwitch).id

        val validation =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                PublicationRequestIds(
                    trackNumbers = listOf(draftTrackNumberId),
                    locationTracks = listOf(draftLocationTrackId, newLocationTrack1, newLocationTrack2),
                    kmPosts = listOf(),
                    referenceLines = listOf(referenceLineId),
                    switches = listOf(draftSwitchId, newSwitch1, newSwitch2),
                ),
            )

        assertEquals(
            listOf(
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.location-track.duplicate-name-official",
                    mapOf("locationTrack" to AlignmentName("LT"), "trackNumber" to TrackNumber("TN")),
                )
            ),
            validation.validatedAsPublicationUnit.locationTracks.find { lt -> lt.id == draftLocationTrackId }?.issues,
        )

        assertEquals(
            List(2) {
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.location-track.duplicate-name-draft",
                    mapOf("locationTrack" to AlignmentName("NLT"), "trackNumber" to TrackNumber("TN")),
                )
            },
            validation.validatedAsPublicationUnit.locationTracks
                .filter { lt -> lt.name == AlignmentName("NLT") }
                .flatMap { it.issues },
        )

        assertEquals(
            listOf(
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.switch.duplicate-name-official",
                    mapOf("switch" to SwitchName("SW")),
                )
            ),
            validation.validatedAsPublicationUnit.switches
                .find { it.name == SwitchName("SW") }
                ?.issues
                ?.filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-official" },
        )

        assertEquals(
            List(2) {
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.switch.duplicate-name-draft",
                    mapOf("switch" to SwitchName("NSW")),
                )
            },
            validation.validatedAsPublicationUnit.switches
                .filter { it.name == SwitchName("NSW") }
                .flatMap { it.issues }
                .filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-draft" },
        )

        assertEquals(
            listOf(
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.track-number.duplicate-name-official",
                    mapOf("trackNumber" to TrackNumber("TN")),
                )
            ),
            validation.validatedAsPublicationUnit.trackNumbers[0].issues,
        )
    }

    @Test
    fun `Publication rejects duplicate track number names`() {
        trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = false))
        val draftTrackNumberId = trackNumberDao.insert(trackNumber(number = TrackNumber("TN"), draft = true)).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        referenceLineDao.insert(referenceLine(draftTrackNumberId, alignmentVersion = someAlignment, draft = true)).id
        val exception =
            assertThrows<DuplicateNameInPublicationException> {
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
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = someAlignment, draft = true)).id

        val lt1 =
            locationTrack(
                trackNumberId = trackNumberId,
                name = "LT1",
                alignmentVersion = someAlignment,
                externalId = null,
                draft = false,
            )
        val lt1OriginalVersion = locationTrackDao.insert(lt1).rowVersion
        val lt1RenamedDraft =
            locationTrackDao.insert(
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
        val lt2OriginalVersion = locationTrackDao.insert(lt2).rowVersion
        val lt2RenamedDraft =
            locationTrackDao.insert(
                asMainDraft(locationTrackDao.fetch(lt2OriginalVersion).copy(name = AlignmentName("LT1")))
            )

        publish(publicationService, locationTracks = listOf(lt1RenamedDraft.id, lt2RenamedDraft.id))
    }

    @Test
    fun `Publication rejects duplicate switch names`() {
        switchDao.insert(switch(name = "SW123", draft = false, stateCategory = EXISTING))
        val draftSwitchId = switchDao.insert(switch(name = "SW123", draft = true, stateCategory = EXISTING)).id
        val exception =
            assertThrows<DuplicateNameInPublicationException> {
                publish(publicationService, switches = listOf(draftSwitchId))
            }
        assertEquals("error.publication.duplicate-name-on.switch", exception.localizationKey.toString())
        assertEquals(mapOf("name" to "SW123"), exception.localizationParams.params)
    }

    @Test
    fun `Publication validation rejects duplication by another referencing track`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val dummyAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))))
        // Initial state, all official: Small duplicates middle, middle and big don't duplicate
        // anything
        val middleTrack =
            locationTrackDao.insert(
                locationTrack(trackNumberId, name = "middle track", alignmentVersion = dummyAlignment, draft = false)
            )
        val smallTrack =
            locationTrackDao.insert(
                locationTrack(
                    trackNumberId,
                    name = "small track",
                    duplicateOf = middleTrack.id,
                    alignmentVersion = dummyAlignment,
                    draft = false,
                )
            )
        val bigTrack =
            locationTrackDao.insert(
                locationTrack(trackNumberId, name = "big track", alignmentVersion = dummyAlignment, draft = false)
            )

        // In new draft, middle wants to duplicate big (leading to: small->middle->big)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(middleTrack.rowVersion).copy(duplicateOf = bigTrack.id),
        )

        fun getPublishingDuplicateWhileDuplicatedValidationError(
            vararg publishableTracks: IntId<LocationTrack>
        ): LayoutValidationIssue? {
            val validation =
                publicationService.validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    PublicationRequestIds(
                        trackNumbers = listOf(),
                        locationTracks = listOf(*publishableTracks),
                        kmPosts = listOf(),
                        referenceLines = listOf(),
                        switches = listOf(),
                    ),
                )
            val trackErrors = validation.validatedAsPublicationUnit.locationTracks[0].issues
            return trackErrors.find { error ->
                error.localizationKey ==
                    LocalizationKey(
                        "validation.layout.location-track.duplicate-of.publishing-duplicate-while-duplicated"
                    )
            }
        }

        // if we're only trying to publish the middle track, but the small is still duplicating it,
        // we pop
        val duplicateError = getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id)
        assertNotNull(duplicateError, "small track duplicates to-be-published middle track which duplicates big track")
        assertEquals("small track", duplicateError.params.get("otherDuplicates"))
        assertEquals("big track", duplicateError.params.get("duplicateTrack"))

        // if we have a draft of the small track that is not a duplicate of the middle track, but
        // we're not publishing
        // it in this unit, that doesn't fix the issue yet
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(smallTrack.rowVersion).copy(duplicateOf = null),
        )
        assertNotNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id),
            "only saving a draft of small track",
        )

        // but if we have the new non-duplicating small track in the same publication unit, it's
        // fine
        assertNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id),
            "publishing new small track",
        )

        // finally, if we have a track whose official version doesn't duplicate the middle track,
        // but the draft does,
        // it's only bad if the draft is in the publication unit
        val otherSmallTrack =
            locationTrackDao.insert(
                locationTrack(
                    trackNumberId,
                    name = "other small track",
                    alignmentVersion = dummyAlignment,
                    draft = false,
                )
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(otherSmallTrack.rowVersion).copy(duplicateOf = middleTrack.id),
        )
        assertNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id),
            "publishing new small track with other small track added",
        )
        assertNotNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id, otherSmallTrack.id),
            "publishing new small track with other small track added and in publication unit",
        )
    }

    private fun getCalculatedChangesInRequest(versions: ValidationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChanges(versions)

    private fun publishAndVerify(layoutBranch: LayoutBranch, request: PublicationRequestIds): PublicationResult {
        val versions = publicationService.getValidationVersions(layoutBranch, request)
        verifyVersions(request, versions)
        verifyVersionsAreDrafts(layoutBranch, versions)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publicationResult = testPublish(layoutBranch, versions, draftCalculatedChanges)
        val publicationDetails = publicationService.getPublicationDetails(publicationResult.publicationId!!)
        assertNotNull(publicationResult.publicationId)
        verifyPublished(layoutBranch, versions.trackNumbers, trackNumberDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(layoutBranch, versions.referenceLines, referenceLineDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(layoutBranch, versions.kmPosts, kmPostDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(layoutBranch, versions.locationTracks, locationTrackDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }
        verifyPublished(layoutBranch, versions.switches, switchDao) { draft, published ->
            assertMatches(draft, published, contextMatch = false)
        }

        assertEqualsCalculatedChanges(draftCalculatedChanges, publicationDetails)
        return publicationResult
    }

    private fun verifyVersionsAreDrafts(branch: LayoutBranch, versions: ValidationVersions) {
        verifyVersionsAreDrafts(branch, trackNumberDao, versions.trackNumbers)
        verifyVersionsAreDrafts(branch, referenceLineDao, versions.referenceLines)
        verifyVersionsAreDrafts(branch, locationTrackDao, versions.locationTracks)
        verifyVersionsAreDrafts(branch, switchDao, versions.switches)
        verifyVersionsAreDrafts(branch, kmPostDao, versions.kmPosts)
    }

    @Test
    fun `Track number diff finds all changed fields`() {
        val (trackNumberId, trackNumber) =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(
                        number = testDBService.getUnusedTrackNumber(),
                        description = "TEST",
                        state = LayoutState.IN_USE,
                    ),
                )
                .let { r -> r.id to trackNumberDao.fetch(r.rowVersion) }
        val rl = referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumber!!.id as IntId)!!
        publishAndVerify(
            LayoutBranch.main,
            publicationRequest(trackNumbers = listOf(trackNumber.id as IntId), referenceLines = listOf(rl.id as IntId)),
        )
        trackNumberService.update(
            LayoutBranch.main,
            trackNumberId,
            trackNumberSaveRequest(
                number = TrackNumber(trackNumber.number.value + " T"),
                description = "${trackNumber.description}_TEST",
                state = LayoutState.NOT_IN_USE,
            ),
        )
        publishAndVerify(LayoutBranch.main, publicationRequest(trackNumbers = listOf(trackNumber.id as IntId)))
        val thisAndPreviousPublication = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val changes =
            publicationDao.fetchPublicationTrackNumberChanges(
                LayoutBranch.main,
                thisAndPreviousPublication.first().id,
                thisAndPreviousPublication.last().publicationTime,
            )

        val diff =
            publicationService.diffTrackNumber(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(trackNumber.id as IntId),
                thisAndPreviousPublication.first().publicationTime,
                thisAndPreviousPublication.last().publicationTime,
            ) { _, _ ->
                null
            }
        assertEquals(3, diff.size)
        assertEquals("track-number", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
        assertEquals("description", diff[2].propKey.key.toString())
    }

    @Test
    fun `Changing specific Track Number field returns only that field`() {
        val (trackNumberId, trackNumber) =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(
                        number = testDBService.getUnusedTrackNumber(),
                        description = "TEST",
                        state = LayoutState.IN_USE,
                    ),
                )
                .let { r -> r.id to trackNumberDao.fetch(r.rowVersion) }
        val rl = referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumber.id as IntId)!!
        publishAndVerify(
            LayoutBranch.main,
            publicationRequest(trackNumbers = listOf(trackNumber.id as IntId), referenceLines = listOf(rl.id as IntId)),
        )

        val idOfUpdated =
            trackNumberService
                .update(
                    LayoutBranch.main,
                    trackNumberId,
                    trackNumberSaveRequest(
                        number = trackNumber.number,
                        description = "TEST2",
                        state = trackNumber.state,
                    ),
                )
                .id
        publishAndVerify(LayoutBranch.main, publicationRequest(trackNumbers = listOf(trackNumber.id as IntId)))
        val thisAndPreviousPublication = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val changes =
            publicationDao.fetchPublicationTrackNumberChanges(
                LayoutBranch.main,
                thisAndPreviousPublication.first().id,
                thisAndPreviousPublication.last().publicationTime,
            )
        val updatedTrackNumber = trackNumberService.getOrThrow(MainLayoutContext.official, idOfUpdated)

        val diff =
            publicationService.diffTrackNumber(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(trackNumber.id as IntId),
                thisAndPreviousPublication.first().publicationTime,
                thisAndPreviousPublication.last().publicationTime,
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("description", diff[0].propKey.key.toString())
        assertEquals(trackNumber.description, diff[0].value.oldValue)
        assertEquals(updatedTrackNumber.description, diff[0].value.newValue)
    }

    @Test
    fun `Location track diff finds all changed fields`() {
        val duplicate =
            locationTrackDao.fetch(
                locationTrackService
                    .insert(
                        LayoutBranch.main,
                        LocationTrackSaveRequest(
                            AlignmentName("TEST duplicate"),
                            FreeText("Test"),
                            DescriptionSuffixType.NONE,
                            LocationTrackType.MAIN,
                            LocationTrackState.IN_USE,
                            mainDraftContext.createLayoutTrackNumber().id,
                            null,
                            TopologicalConnectivityType.NONE,
                            IntId(1),
                        ),
                    )
                    .rowVersion
            )

        val duplicate2 =
            locationTrackDao.fetch(
                locationTrackService
                    .insert(
                        LayoutBranch.main,
                        LocationTrackSaveRequest(
                            AlignmentName("TEST duplicate 2"),
                            FreeText("Test"),
                            DescriptionSuffixType.NONE,
                            LocationTrackType.MAIN,
                            LocationTrackState.IN_USE,
                            mainDraftContext.createLayoutTrackNumber().id,
                            null,
                            TopologicalConnectivityType.NONE,
                            IntId(1),
                        ),
                    )
                    .rowVersion
            )

        val locationTrack =
            locationTrackDao.fetch(
                locationTrackService
                    .insert(
                        LayoutBranch.main,
                        LocationTrackSaveRequest(
                            AlignmentName("TEST"),
                            FreeText("Test"),
                            DescriptionSuffixType.NONE,
                            LocationTrackType.MAIN,
                            LocationTrackState.IN_USE,
                            mainDraftContext.createLayoutTrackNumber().id,
                            duplicate.id as IntId<LocationTrack>,
                            TopologicalConnectivityType.NONE,
                            IntId(1),
                        ),
                    )
                    .rowVersion
            )
        publishAndVerify(
            LayoutBranch.main,
            publicationRequest(
                locationTracks =
                    listOf(
                        locationTrack.id as IntId<LocationTrack>,
                        duplicate.id as IntId<LocationTrack>,
                        duplicate2.id as IntId<LocationTrack>,
                    )
            ),
        )

        val updatedLocationTrack =
            locationTrackDao.fetch(
                locationTrackService
                    .update(
                        LayoutBranch.main,
                        locationTrack.id as IntId,
                        LocationTrackSaveRequest(
                            name = AlignmentName("TEST2"),
                            descriptionBase = FreeText("Test2"),
                            descriptionSuffix = DescriptionSuffixType.SWITCH_TO_BUFFER,
                            type = LocationTrackType.SIDE,
                            state = LocationTrackState.NOT_IN_USE,
                            trackNumberId = locationTrack.trackNumberId,
                            duplicate2.id as IntId<LocationTrack>,
                            topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                            IntId(1),
                        ),
                    )
                    .rowVersion
            )
        publish(publicationService, locationTracks = listOf(updatedLocationTrack.id as IntId<LocationTrack>))
        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(locationTrack.id as IntId<LocationTrack>),
                null,
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                emptySet(),
            ) { _, _ ->
                null
            }
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
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (draftOnlyTrack, draftOnlyAlignment) =
            locationTrackAndAlignment(
                trackNumberId = trackNumberId,
                segments = listOf(someSegment()),
                duplicateOf = null,
                draft = true,
            )
        val draftOnlyId = locationTrackService.saveDraft(LayoutBranch.main, draftOnlyTrack, draftOnlyAlignment).id

        val (duplicateTrack, duplicateAlignment) =
            locationTrackAndAlignment(
                trackNumberId = trackNumberId,
                segments = listOf(someSegment()),
                duplicateOf = draftOnlyId,
                draft = true,
            )
        val duplicateId = locationTrackService.saveDraft(LayoutBranch.main, duplicateTrack, duplicateAlignment).id

        // Both tracks in validation set: this is fine
        assertFalse(
            containsDuplicateOfNotPublishedError(
                validateLocationTrack(toValidate = duplicateId, duplicateId, draftOnlyId)
            )
        )
        // Only the target (main) track in set: this is also fine
        assertFalse(containsDuplicateOfNotPublishedError(validateLocationTrack(toValidate = draftOnlyId, draftOnlyId)))
        // Only the duplicate track in set: this would result in official referring to draft through
        // duplicateOf
        assertTrue(containsDuplicateOfNotPublishedError(validateLocationTrack(toValidate = duplicateId, duplicateId)))
    }

    private fun containsDuplicateOfNotPublishedError(errors: List<LayoutValidationIssue>) =
        containsError(errors, "validation.layout.location-track.duplicate-of.not-published")

    private fun containsError(errors: List<LayoutValidationIssue>, key: String) =
        errors.any { e -> e.localizationKey.toString() == key }

    private fun validateLocationTrack(
        toValidate: IntId<LocationTrack>,
        vararg publicationSet: IntId<LocationTrack>,
    ): List<LayoutValidationIssue> {
        val candidates =
            publicationService
                .collectPublicationCandidates(PublicationInMain)
                .filter(publicationRequest(locationTracks = publicationSet.toList()))
        return publicationService
            .validateAsPublicationUnit(candidates, false)
            .locationTracks
            .find { c -> c.id == toValidate }!!
            .issues
    }

    @Test
    fun `Changing specific Location Track field returns only that field`() {
        val saveReq =
            LocationTrackSaveRequest(
                AlignmentName("TEST"),
                FreeText("Test"),
                DescriptionSuffixType.NONE,
                LocationTrackType.MAIN,
                LocationTrackState.IN_USE,
                mainOfficialContext.createLayoutTrackNumber().id,
                null,
                TopologicalConnectivityType.NONE,
                IntId(1),
            )

        val locationTrack = locationTrackDao.fetch(locationTrackService.insert(LayoutBranch.main, saveReq).rowVersion)
        publish(publicationService, locationTracks = listOf(locationTrack.id as IntId<LocationTrack>))

        val updatedLocationTrack =
            locationTrackDao.fetch(
                locationTrackService
                    .update(
                        LayoutBranch.main,
                        locationTrack.id as IntId,
                        saveReq.copy(descriptionBase = FreeText("TEST2")),
                    )
                    .rowVersion
            )
        publish(publicationService, locationTracks = listOf(updatedLocationTrack.id as IntId<LocationTrack>))
        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(locationTrack.id as IntId<LocationTrack>),
                null,
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                emptySet(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("description-base", diff[0].propKey.key.toString())
        assertEquals(locationTrack.descriptionBase, diff[0].value.oldValue)
        assertEquals(updatedLocationTrack.descriptionBase, diff[0].value.newValue)
    }

    @Test
    fun `KM Post diff finds all changed fields`() {
        val trackNumberId =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(number = testDBService.getUnusedTrackNumber(), description = "TEST"),
                )
                .id
        val trackNumber2Id =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(number = testDBService.getUnusedTrackNumber(), description = "TEST 2"),
                )
                .id

        val kmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.insertKmPost(
                    LayoutBranch.main,
                    TrackLayoutKmPostSaveRequest(
                        KmNumber(0),
                        LayoutState.IN_USE,
                        trackNumberId,
                        gkLocation = null,
                        gkLocationSource = null,
                        gkLocationConfirmed = false,
                    ),
                ),
            )
        publish(
            publicationService,
            kmPosts = listOf(kmPost.id as IntId),
            trackNumbers = listOf(trackNumberId, trackNumber2Id),
        )
        val updatedKmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.updateKmPost(
                    LayoutBranch.main,
                    kmPost.id as IntId,
                    TrackLayoutKmPostSaveRequest(
                        KmNumber(1),
                        LayoutState.NOT_IN_USE,
                        trackNumber2Id,
                        gkLocation = null,
                        gkLocationSource = null,
                        gkLocationConfirmed = false,
                    ),
                ),
            )
        publish(publicationService, kmPosts = listOf(updatedKmPost.id as IntId))

        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)

        val diff =
            publicationService.diffKmPost(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(kmPost.id as IntId),
                latestPub.publicationTime,
                latestPub.publicationTime.minusMillis(1),
                trackNumberDao.fetchTrackNumberNames(),
                { _, _ -> null },
                { geographyService.getCoordinateSystem(it).name.toString() },
            )
        assertEquals(2, diff.size)
        // assertEquals("track-number", diff[0].propKey) TODO Enable when track number switching
        // works
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
    }

    @Test
    fun `simple km post change in design is reported`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val kmPost = mainOfficialContext.insert(kmPost(trackNumber, KmNumber(1))).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        kmPostService.saveDraft(testBranch, mainOfficialContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(2)))
        publish(publicationService, testBranch, kmPosts = listOf(kmPost))
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        publish(publicationService, kmPosts = listOf(kmPost))
        val diff = getLatestPublicationDiffForKmPost(kmPost)
        assertEquals(1, diff.size)
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals(KmNumber(1), diff[0].value.oldValue)
        assertEquals(KmNumber(2), diff[0].value.newValue)
    }

    private fun getLatestPublicationDiffForKmPost(
        id: IntId<TrackLayoutKmPost>
    ): List<PublicationChange<out Comparable<Nothing>?>> {
        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)
        return publicationService.diffKmPost(
            localizationService.getLocalization(LocalizationLanguage.FI),
            changes.getValue(id),
            latestPub.publicationTime,
            latestPub.publicationTime.minusMillis(1),
            trackNumberDao.fetchTrackNumberNames(),
            { _, _ -> null },
            { geographyService.getCoordinateSystem(it).name.toString() },
        )
    }

    @Test
    fun `Changing specific KM Post field returns only that field`() {
        val saveReq =
            TrackLayoutKmPostSaveRequest(
                KmNumber(0),
                LayoutState.IN_USE,
                mainOfficialContext.createLayoutTrackNumber().id,
                gkLocation = null,
                gkLocationSource = null,
                gkLocationConfirmed = false,
            )

        val kmPost =
            kmPostService.getOrThrow(MainLayoutContext.draft, kmPostService.insertKmPost(LayoutBranch.main, saveReq))
        publish(publicationService, kmPosts = listOf(kmPost.id as IntId))
        val updatedKmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.updateKmPost(LayoutBranch.main, kmPost.id as IntId, saveReq.copy(kmNumber = KmNumber(1))),
            )
        publish(publicationService, kmPosts = listOf(updatedKmPost.id as IntId))
        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)

        val diff =
            publicationService.diffKmPost(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(kmPost.id as IntId),
                latestPub.publicationTime,
                latestPub.publicationTime.minusMillis(1),
                trackNumberDao.fetchTrackNumberNames(),
                { _, _ -> null },
                { geographyService.getCoordinateSystem(it).name.toString() },
            )
        assertEquals(1, diff.size)
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals(kmPost.kmNumber, diff[0].value.oldValue)
        assertEquals(updatedKmPost.kmNumber, diff[0].value.newValue)
    }

    @Test
    fun `Switch diff finds all changed fields`() {
        val trackNumberSaveReq =
            TrackNumberSaveRequest(
                testDBService.getUnusedTrackNumber(),
                FreeText("TEST"),
                LayoutState.IN_USE,
                TrackMeter(0, 0),
            )
        val tn1 = trackNumberService.insert(LayoutBranch.main, trackNumberSaveReq).id
        val tn2 =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveReq.copy(testDBService.getUnusedTrackNumber(), FreeText("TEST 2")),
                )
                .id

        val switch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.insertSwitch(
                    LayoutBranch.main,
                    TrackLayoutSwitchSaveRequest(
                        SwitchName("TEST"),
                        IntId(1),
                        LayoutStateCategory.EXISTING,
                        IntId(1),
                        false,
                    ),
                ),
            )
        publish(publicationService, switches = listOf(switch.id as IntId), trackNumbers = listOf(tn1, tn2))
        val updatedSwitch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.updateSwitch(
                    LayoutBranch.main,
                    switch.id as IntId,
                    TrackLayoutSwitchSaveRequest(
                        SwitchName("TEST 2"),
                        IntId(2),
                        LayoutStateCategory.NOT_EXISTING,
                        IntId(2),
                        true,
                    ),
                ),
            )
        publish(publicationService, switches = listOf(updatedSwitch.id as IntId))

        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id as IntId),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.DELETE,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(5, diff.size)
        assertEquals("switch", diff[0].propKey.key.toString())
        assertEquals("state-category", diff[1].propKey.key.toString())
        assertEquals("switch-type", diff[2].propKey.key.toString())
        assertEquals("trap-point", diff[3].propKey.key.toString())
        assertEquals("owner", diff[4].propKey.key.toString())
    }

    @Test
    fun `Changing specific switch field returns only that field`() {
        val saveReq =
            TrackLayoutSwitchSaveRequest(SwitchName("TEST"), IntId(1), LayoutStateCategory.EXISTING, IntId(1), false)

        val switch =
            switchService.getOrThrow(MainLayoutContext.draft, switchService.insertSwitch(LayoutBranch.main, saveReq))
        publish(publicationService, switches = listOf(switch.id as IntId))
        val updatedSwitch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.updateSwitch(
                    LayoutBranch.main,
                    switch.id as IntId,
                    saveReq.copy(name = SwitchName("TEST 2")),
                ),
            )
        publish(publicationService, switches = listOf(updatedSwitch.id as IntId))

        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id as IntId),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.MODIFY,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("switch", diff[0].propKey.key.toString())
        assertEquals(switch.name, diff[0].value.oldValue)
        assertEquals(updatedSwitch.name, diff[0].value.newValue)
    }

    private fun alignmentWithSwitchLinks(vararg switchIds: IntId<TrackLayoutSwitch>?): LayoutAlignment =
        alignment(
            switchIds.mapIndexed { index, switchId ->
                segment(Point(0.0, index * 1.0), Point(0.0, index * 1.0 + 1.0)).let { segment ->
                    if (switchId == null) {
                        segment
                    } else {
                        segment.copy(switchId = switchId, startJointNumber = JointNumber(1))
                    }
                }
            }
        )

    @Test
    fun `Location track switch link changes are reported`() {
        val switchUnlinkedFromTopology =
            switchDao.insert(switch(name = "sw-unlinked-from-topology", externalId = "1.1.1.1.1", draft = false))
        val switchUnlinkedFromAlignment =
            switchDao.insert(switch(name = "sw-unlinked-from-alignment", externalId = "1.1.1.1.2", draft = false))
        val switchAddedToTopologyStart =
            switchDao.insert(switch(name = "sw-added-to-topo-start", externalId = "1.1.1.1.3", draft = false))
        val switchAddedToTopologyEnd =
            switchDao.insert(switch(name = "sw-added-to-topo-end", externalId = "1.1.1.1.4", draft = false))
        val switchAddedToAlignment =
            switchDao.insert(switch(name = "sw-added-to-alignment", externalId = "1.1.1.1.5", draft = false))
        val switchDeleted = switchDao.insert(switch(name = "sw-deleted", externalId = "1.1.1.1.6", draft = false))
        val switchMerelyRenamed =
            switchDao.insert(switch(name = "sw-merely-renamed", externalId = "1.1.1.1.7", draft = false))
        val originalSwitchReplacedWithNewSameName =
            switchDao.insert(switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.8", draft = false))

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        val originalLocationTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchUnlinkedFromTopology.id, JointNumber(1)),
                    draft = true,
                ),
                alignmentWithSwitchLinks(
                    switchUnlinkedFromAlignment.id,
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                ),
            )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao.fetch(switchDeleted.rowVersion).copy(stateCategory = LayoutStateCategory.NOT_EXISTING),
        )
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao
                .fetch(originalSwitchReplacedWithNewSameName.rowVersion)
                .copy(stateCategory = LayoutStateCategory.NOT_EXISTING),
        )
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao.fetch(switchMerelyRenamed.rowVersion).copy(name = SwitchName("sw-with-new-name")),
        )
        val newSwitchReplacingOldWithSameName =
            switchService.saveDraft(
                LayoutBranch.main,
                switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.9", draft = true),
            )

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao
                .getOrThrow(MainLayoutContext.official, originalLocationTrack.id)
                .copy(
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyStart.id, JointNumber(1)),
                    topologyEndSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyEnd.id, JointNumber(1)),
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
            switches =
                listOf(
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                    newSwitchReplacingOldWithSameName.id,
                ),
        )
        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs[0]
        val previousPub = latestPubs[1]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(originalLocationTrack.id),
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(latestPub.id)[originalLocationTrack.id],
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                setOf(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("linked-switches", diff[0].propKey.key.toString())
        assertEquals(
            """
                Vaihteiden sw-deleted, sw-replaced-with-new-same-name (1.1.1.1.8), sw-unlinked-from-alignment,
                sw-unlinked-from-topology linkitys purettu. Vaihteet sw-added-to-alignment, sw-added-to-topo-end,
                sw-added-to-topo-start, sw-replaced-with-new-same-name (1.1.1.1.9) linkitetty.
            """
                .trimIndent()
                .replace("\n", " "),
            diff[0].remark,
        )
    }

    @Test
    fun `Location track switch link changes made in design are reported`() {
        val switchUnlinkedFromTopology =
            switchDao.insert(switch(name = "sw-unlinked-from-topology", externalId = "1.1.1.1.1", draft = false))
        val switchUnlinkedFromAlignment =
            switchDao.insert(switch(name = "sw-unlinked-from-alignment", externalId = "1.1.1.1.2", draft = false))
        val switchAddedToTopologyStart =
            switchDao.insert(switch(name = "sw-added-to-topo-start", externalId = "1.1.1.1.3", draft = false))
        val switchAddedToTopologyEnd =
            switchDao.insert(switch(name = "sw-added-to-topo-end", externalId = "1.1.1.1.4", draft = false))
        val switchAddedToAlignment =
            switchDao.insert(switch(name = "sw-added-to-alignment", externalId = "1.1.1.1.5", draft = false))
        val switchDeleted = switchDao.insert(switch(name = "sw-deleted", externalId = "1.1.1.1.6", draft = false))
        val switchMerelyRenamed =
            switchDao.insert(switch(name = "sw-merely-renamed", externalId = "1.1.1.1.7", draft = false))
        val originalSwitchReplacedWithNewSameName =
            switchDao.insert(switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.8", draft = false))

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        val originalLocationTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchUnlinkedFromTopology.id, JointNumber(1)),
                    draft = true,
                ),
                alignmentWithSwitchLinks(
                    switchUnlinkedFromAlignment.id,
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                ),
            )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))

        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))

        switchService.saveDraft(
            testBranch,
            switchDao.fetch(switchDeleted.rowVersion).copy(stateCategory = LayoutStateCategory.NOT_EXISTING),
        )
        switchService.saveDraft(
            testBranch,
            switchDao
                .fetch(originalSwitchReplacedWithNewSameName.rowVersion)
                .copy(stateCategory = LayoutStateCategory.NOT_EXISTING),
        )
        switchService.saveDraft(
            testBranch,
            switchDao.fetch(switchMerelyRenamed.rowVersion).copy(name = SwitchName("sw-with-new-name")),
        )
        val newSwitchReplacingOldWithSameName =
            testDBService
                .testContext(testBranch, DRAFT)
                .insert(switch(name = "sw-replaced-with-new-same-name", externalId = "1.1.1.1.9"))

        locationTrackService.saveDraft(
            testBranch,
            locationTrackDao
                .getOrThrow(MainLayoutContext.official, originalLocationTrack.id)
                .copy(
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyStart.id, JointNumber(1)),
                    topologyEndSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyEnd.id, JointNumber(1)),
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
            testBranch,
            locationTracks = listOf(originalLocationTrack.id),
            switches =
                listOf(
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                    newSwitchReplacingOldWithSameName.id,
                ),
        )
        locationTrackService.mergeToMainBranch(testBranch, originalLocationTrack.id)
        listOf(
                switchDeleted.id,
                switchMerelyRenamed.id,
                originalSwitchReplacedWithNewSameName.id,
                newSwitchReplacingOldWithSameName.id,
            )
            .forEach { id -> switchService.mergeToMainBranch(testBranch, id) }
        publish(
            publicationService,
            locationTracks = listOf(originalLocationTrack.id),
            switches =
                listOf(
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                    newSwitchReplacingOldWithSameName.id,
                ),
        )
        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs[0]
        val previousPub = latestPubs[1]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(originalLocationTrack.id),
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(latestPub.id)[originalLocationTrack.id],
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                setOf(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("linked-switches", diff[0].propKey.key.toString())
        assertEquals(
            """
                Vaihteiden sw-deleted, sw-replaced-with-new-same-name (1.1.1.1.8), sw-unlinked-from-alignment,
                sw-unlinked-from-topology linkitys purettu. Vaihteet sw-added-to-alignment, sw-added-to-topo-end,
                sw-added-to-topo-start, sw-replaced-with-new-same-name (1.1.1.1.9) linkitetty.
            """
                .trimIndent()
                .replace("\n", " "),
            diff[0].remark,
        )
    }

    @Test
    fun `Location track geometry changes are reported`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        fun segmentWithCurveToMaxY(maxY: Double) =
            segment(
                *(0..10)
                    .map { x -> Point(x.toDouble(), (5.0 - (x.toDouble() - 5.0).absoluteValue) / 10.0 * maxY) }
                    .toTypedArray()
            )

        val referenceLineAlignment = alignmentDao.insert(alignment(segmentWithCurveToMaxY(0.0)))
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = referenceLineAlignment, draft = false))

        // track that had bump to y=-10 goes to having a bump to y=10, meaning the length and ends
        // stay the same,
        // but the geometry changes
        val originalAlignment = alignment(segmentWithCurveToMaxY(-10.0))
        val newAlignment = alignment(segmentWithCurveToMaxY(10.0))
        val originalLocationTrack =
            locationTrackDao.insert(
                locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment), draft = false)
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(locationTrackDao.fetch(originalLocationTrack.rowVersion)),
            newAlignment,
        )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))
        val latestPub = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1)[0]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(originalLocationTrack.id),
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(latestPub.id)[originalLocationTrack.id],
                LayoutBranch.main,
                latestPub.publicationTime,
                latestPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                setOf(KmNumber(0)),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("Muutos välillä 0000+0001-0000+0009, sivusuuntainen muutos 10.0 m", diff[0].remark)
    }

    @Test
    fun `should filter publication details by dates`() {
        val trackNumberId1 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 =
            locationTrackAndAlignment(trackNumberId1, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish1 =
            publish(publicationService, trackNumbers = listOf(trackNumberId1), locationTracks = listOf(locationTrack1))

        val trackNumberId2 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 =
            locationTrackAndAlignment(trackNumberId2, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish2 =
            publish(publicationService, trackNumbers = listOf(trackNumberId2), locationTracks = listOf(locationTrack2))

        val publication1 = publicationDao.getPublication(publish1.publicationId!!)
        val publication2 = publicationDao.getPublication(publish2.publicationId!!)

        assertTrue {
            publicationService
                .fetchPublicationDetailsBetweenInstants(LayoutBranch.main, to = publication1.publicationTime)
                .isEmpty()
        }

        assertTrue {
            publicationService
                .fetchPublicationDetailsBetweenInstants(
                    LayoutBranch.main,
                    from = publication2.publicationTime.plusMillis(1),
                )
                .isEmpty()
        }

        assertEquals(
            2,
            publicationService
                .fetchPublicationDetailsBetweenInstants(
                    LayoutBranch.main,
                    from = publication1.publicationTime,
                    to = publication2.publicationTime.plusMillis(1),
                )
                .size,
        )
    }

    @Test
    fun `should fetch latest publications`() {
        val trackNumberId1 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 =
            locationTrackAndAlignment(trackNumberId1, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish1 =
            publish(publicationService, trackNumbers = listOf(trackNumberId1), locationTracks = listOf(locationTrack1))

        val trackNumberId2 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 =
            locationTrackAndAlignment(trackNumberId2, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish2 =
            publish(publicationService, trackNumbers = listOf(trackNumberId2), locationTracks = listOf(locationTrack2))

        assertEquals(2, publicationService.fetchPublications(LayoutBranch.main).size)

        assertEquals(1, publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).size)
        assertEquals(
            publish2.publicationId,
            publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1)[0].id,
        )

        assertEquals(2, publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).size)
        assertEquals(
            publish1.publicationId,
            publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 10)[1].id,
        )

        assertTrue { publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 0).isEmpty() }
    }

    @Test
    fun `should sort publications by header column`() {
        val trackNumberId1 = mainDraftContext.getOrCreateLayoutTrackNumber(TrackNumber("1234")).id as IntId
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId1))

        val trackNumberId2 = mainDraftContext.getOrCreateLayoutTrackNumber(TrackNumber("4321")).id as IntId
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId2))

        val rows1 =
            publicationService.fetchPublicationDetails(
                LayoutBranch.main,
                sortBy = PublicationTableColumn.NAME,
                translation = localizationService.getLocalization(LocalizationLanguage.FI),
            )

        assertEquals(2, rows1.size)
        assertTrue { rows1[0].name.contains("1234") }

        val rows2 =
            publicationService.fetchPublicationDetails(
                LayoutBranch.main,
                sortBy = PublicationTableColumn.NAME,
                order = SortOrder.DESCENDING,
                translation = localizationService.getLocalization(LocalizationLanguage.FI),
            )

        assertEquals(2, rows2.size)
        assertTrue { rows2[0].name.contains("4321") }

        val rows3 =
            publicationService.fetchPublicationDetails(
                LayoutBranch.main,
                sortBy = PublicationTableColumn.PUBLICATION_TIME,
                order = SortOrder.ASCENDING,
                translation = localizationService.getLocalization(LocalizationLanguage.FI),
            )

        assertEquals(2, rows3.size)
        assertTrue { rows3[0].name.contains("1234") }
    }

    @Test
    fun `switch diff consistently uses segment point for joint location`() {
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(TrackNumber("1234")).id as IntId
        referenceLineDao.insert(
            referenceLine(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(11.0, 0.0)))),
                draft = false,
            )
        )
        val switch =
            switchDao.insert(
                switch(joints = listOf(TrackLayoutSwitchJoint(JointNumber(1), Point(4.2, 0.1), null)), draft = false)
            )
        val originalAlignment =
            alignment(
                segment(Point(0.0, 0.0), Point(4.0, 0.0)),
                segment(Point(4.0, 0.0), Point(10.0, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        val locationTrack =
            locationTrackDao.insert(
                locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment), draft = false)
            )
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao
                .fetch(switch.rowVersion)
                .copy(joints = listOf(TrackLayoutSwitchJoint(JointNumber(1), Point(4.1, 0.2), null))),
        )
        val updatedAlignment =
            alignment(
                segment(Point(0.1, 0.0), Point(4.1, 0.0)),
                segment(Point(4.1, 0.0), Point(10.1, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(locationTrack.rowVersion),
            updatedAlignment,
        )

        publish(publicationService, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))

        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.MODIFY,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(2, diff.size)
        assertEquals(
            listOf("switch-joint-location", "switch-track-address").sorted(),
            diff.map { it.propKey.key.toString() }.sorted(),
        )
        val jointLocationDiff = diff.find { it.propKey.key.toString() == "switch-joint-location" }!!
        assertEquals("4.000 E, 0.000 N", jointLocationDiff.value.oldValue)
        assertEquals("4.100 E, 0.000 N", jointLocationDiff.value.newValue)
        val trackAddressDiff = diff.find { it.propKey.key.toString() == "switch-track-address" }!!
        assertEquals("0000+0004.100", trackAddressDiff.value.newValue)
    }

    @Test
    fun `switch diff consistently uses segment point for joint location with edit made in design`() {
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(TrackNumber("1234")).id as IntId
        referenceLineDao.insert(
            referenceLine(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(11.0, 0.0)))),
                draft = false,
            )
        )
        val switch =
            switchDao.insert(
                switch(joints = listOf(TrackLayoutSwitchJoint(JointNumber(1), Point(4.2, 0.1), null)), draft = false)
            )
        val originalAlignment =
            alignment(
                segment(Point(0.0, 0.0), Point(4.0, 0.0)),
                segment(Point(4.0, 0.0), Point(10.0, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        val locationTrack =
            locationTrackDao.insert(
                locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment), draft = false)
            )

        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))

        switchService.saveDraft(
            testBranch,
            switchDao
                .fetch(switch.rowVersion)
                .copy(joints = listOf(TrackLayoutSwitchJoint(JointNumber(1), Point(4.1, 0.2), null))),
        )
        val updatedAlignment =
            alignment(
                segment(Point(0.1, 0.0), Point(4.1, 0.0)),
                segment(Point(4.1, 0.0), Point(10.1, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        locationTrackService.saveDraft(testBranch, locationTrackDao.fetch(locationTrack.rowVersion), updatedAlignment)

        publish(publicationService, testBranch, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))
        locationTrackService.mergeToMainBranch(testBranch, locationTrack.id)
        switchService.mergeToMainBranch(testBranch, switch.id)
        publish(publicationService, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))

        val latestPubs = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2)
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.MODIFY,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(2, diff.size)
        assertEquals(
            listOf("switch-joint-location", "switch-track-address").sorted(),
            diff.map { it.propKey.key.toString() }.sorted(),
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
        val switchInnerTrackIds: List<IntId<LocationTrack>>,
    )

    private fun getTopologicalSwitchConnectionTestData(): TopologicalSwitchConnectionTestData {
        val (topologyStartSwitchId, topologyStartSwitchInnerTrackIds) =
            mainDraftContext.createSwitchWithInnerTracks(
                name = "Topological switch connection test start switch",
                listOf(JointNumber(1) to Point(0.0, 0.0), JointNumber(3) to Point(1.0, 0.0)),
            )
        val (topologyEndSwitchId, topologyEndSwitchInnerTrackIds) =
            mainDraftContext.createSwitchWithInnerTracks(
                name = "Topological switch connection test end switch",
                listOf(JointNumber(1) to Point(2.0, 0.0), JointNumber(3) to Point(3.0, 0.0)),
            )

        val locationTrackAlignment = alignment(segment(Point(1.0, 0.0), Point(2.0, 0.0)))
        val locationTracksUnderTest =
            getTopologicalSwitchConnectionTestCases(
                { mainOfficialContext.createLayoutTrackNumber().id },
                TopologyLocationTrackSwitch(topologyStartSwitchId, JointNumber(1)),
                TopologyLocationTrackSwitch(topologyEndSwitchId, JointNumber(3)),
            )

        val locationTrackIdsUnderTest =
            locationTracksUnderTest
                .map { locationTrack ->
                    locationTrack.copy(alignmentVersion = alignmentDao.insert(locationTrackAlignment))
                }
                .map { locationTrack -> locationTrackDao.insert(asMainDraft(locationTrack)).id to locationTrack }

        return TopologicalSwitchConnectionTestData(
            locationTracksUnderTest = locationTrackIdsUnderTest,
            switchIdsUnderTest = listOf(topologyStartSwitchId, topologyEndSwitchId),
            switchInnerTrackIds = topologyStartSwitchInnerTrackIds + topologyEndSwitchInnerTrackIds,
        )
    }

    private fun getLocationTrackValidationResult(
        locationTrackId: IntId<LocationTrack>,
        stagedSwitches: List<IntId<TrackLayoutSwitch>> = listOf(),
        stagedTracks: List<IntId<LocationTrack>> = listOf(locationTrackId),
    ): LocationTrackPublicationCandidate {
        val publicationRequestIds =
            PublicationRequestIds(
                trackNumbers = listOf(),
                locationTracks = stagedTracks,
                referenceLines = listOf(),
                switches = stagedSwitches,
                kmPosts = listOf(),
            )

        val validationResult =
            publicationService.validateAsPublicationUnit(
                publicationService.collectPublicationCandidates(PublicationInMain).filter(publicationRequestIds),
                allowMultipleSplits = false,
            )

        return validationResult.locationTracks.find { lt -> lt.id == locationTrackId }!!
    }

    private fun switchAlignmentNotConnectedTrackValidationError(locationTrackNames: String, switchName: String) =
        LayoutValidationIssue(
            LayoutValidationIssueType.WARNING,
            "validation.layout.location-track.switch-linkage.switch-alignment-not-connected",
            mapOf("locationTracks" to locationTrackNames, "switch" to switchName),
        )

    private fun switchNotPublishedError(switchName: String) =
        LayoutValidationIssue(
            LayoutValidationIssueType.ERROR,
            "validation.layout.location-track.switch.not-published",
            mapOf("switch" to switchName),
        )

    private fun switchFrontJointNotConnectedError(switchName: String) =
        LayoutValidationIssue(
            LayoutValidationIssueType.WARNING,
            "validation.layout.location-track.switch-linkage.front-joint-not-connected",
            mapOf("switch" to switchName),
        )

    private fun assertValidationErrorsForEach(
        expecteds: List<List<LayoutValidationIssue>>,
        actuals: List<List<LayoutValidationIssue>>,
    ) {
        assertEquals(expecteds.size, actuals.size, "size equals")
        expecteds.forEachIndexed { i, expected -> assertValidationErrorContentEquals(expected, actuals[i], i) }
    }

    private fun assertValidationErrorContentEquals(
        expected: List<LayoutValidationIssue>,
        actual: List<LayoutValidationIssue>,
        index: Int,
    ) {
        val allKeys = expected.map { it.localizationKey.toString() } + actual.map { it.localizationKey.toString() }
        val commonPrefix =
            allKeys.reduce { acc, next -> acc.take(acc.zip(next) { a, b -> a == b }.takeWhile { it }.count()) }

        fun cleanupKey(key: LocalizationKey) = key.toString().let { k -> if (commonPrefix.length > 3) "...$k" else k }

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

    private val topoTestDataContextOnLocationTrackValidationError =
        listOf(validationError("validation.layout.location-track.no-context"))
    private val topoTestDataStartSwitchNotPublishedError =
        switchNotPublishedError("Topological switch connection test start switch")
    private val topoTestDataStartSwitchJointsNotConnectedError =
        switchAlignmentNotConnectedTrackValidationError(
            "1-5-2", // alignment 1-3 is generated in the data, 1-5-2 is not
            "Topological switch connection test start switch",
        )
    private val topoTestDataEndSwitchNotPublishedError =
        switchNotPublishedError("Topological switch connection test end switch")
    private val topoTestDataEndSwitchJointsNotConnectedError =
        switchAlignmentNotConnectedTrackValidationError(
            "1-5-2", // alignment 1-3 is generated in the data, 1-5-2 is not
            "Topological switch connection test end switch",
        )
    private val topoTestDataEndSwitchFrontJointNotConnectedError =
        switchFrontJointNotConnectedError("Topological switch connection test end switch")

    @Test
    fun `Location track validation should fail for unofficial and unstaged topologically linked switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()
        val noStart =
            listOf(
                topoTestDataStartSwitchNotPublishedError
                // no error about no track continuing from the front joint, because a track in fact
                // does continue from it
            )
        val noEnd = listOf(topoTestDataEndSwitchNotPublishedError)
        val expected =
            listOf(
                topoTestDataContextOnLocationTrackValidationError,
                topoTestDataContextOnLocationTrackValidationError + noStart,
                topoTestDataContextOnLocationTrackValidationError + noEnd,
                topoTestDataContextOnLocationTrackValidationError + noStart + noEnd,
            )
        val actual =
            topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
                getLocationTrackValidationResult(locationTrackId).issues
            }
        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Location track validation should succeed for unofficial, but staged topologically linked switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()
        val noStart =
            listOf(
                topoTestDataStartSwitchJointsNotConnectedError
                // no error about no track continuing from the front joint, because a track in fact
                // does continue from it
            )
        val noEnd =
            listOf(topoTestDataEndSwitchJointsNotConnectedError, topoTestDataEndSwitchFrontJointNotConnectedError)
        val expected =
            listOf(
                topoTestDataContextOnLocationTrackValidationError,
                topoTestDataContextOnLocationTrackValidationError + noStart,
                topoTestDataContextOnLocationTrackValidationError + noEnd,
                topoTestDataContextOnLocationTrackValidationError + noStart + noEnd,
            )
        val actual =
            topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
                getLocationTrackValidationResult(
                        locationTrackId,
                        topologyTestData.switchIdsUnderTest,
                        topologyTestData.switchInnerTrackIds + locationTrackId,
                    )
                    .issues
            }

        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Location track validation should succeed for topologically linked official switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()

        val noStart =
            listOf(
                topoTestDataStartSwitchJointsNotConnectedError
                // no error about no track continuing from the front joint, because a track in fact
                // does continue from it
            )
        val noEnd =
            listOf(topoTestDataEndSwitchJointsNotConnectedError, topoTestDataEndSwitchFrontJointNotConnectedError)
        val expected =
            listOf(
                topoTestDataContextOnLocationTrackValidationError,
                topoTestDataContextOnLocationTrackValidationError + noStart,
                topoTestDataContextOnLocationTrackValidationError + noEnd,
                topoTestDataContextOnLocationTrackValidationError + noStart + noEnd,
            )

        publish(
            publicationService,
            switches = topologyTestData.switchIdsUnderTest,
            locationTracks = topologyTestData.switchInnerTrackIds,
        )
        val actual =
            topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
                getLocationTrackValidationResult(locationTrackId).issues
            }
        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Switch validation checks duplicate tracks through non-math joints`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId =
            switchService
                .saveDraft(
                    LayoutBranch.main,
                    switch(
                        name = "TV123",
                        joints = listOf(TrackLayoutSwitchJoint(JointNumber(1), Point(0.0, 0.0), null)),
                        structureId =
                            switchStructureDao
                                .fetchSwitchStructures()
                                .find { ss -> ss.type.typeName == "KRV43-233-1:9" }!!
                                .id as IntId,
                        stateCategory = LayoutStateCategory.EXISTING,
                        draft = true,
                    ),
                )
                .id
        val locationTrack1 =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                alignment(
                    segment(Point(0.0, 0.0), Point(2.0, 2.0)),
                    segment(Point(2.0, 2.0), Point(5.0, 5.0))
                        .copy(switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(5)),
                    segment(Point(5.0, 5.0), Point(8.0, 8.0))
                        .copy(switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(2)),
                    segment(Point(8.0, 8.0), Point(10.0, 10.0)),
                ),
            )

        fun otherAlignment() =
            alignment(
                segment(Point(10.0, 0.0), Point(8.0, 2.0)),
                segment(Point(8.0, 2.0), Point(5.0, 5.0))
                    .copy(switchId = switchId, startJointNumber = JointNumber(4), endJointNumber = JointNumber(5)),
                segment(Point(5.0, 5.0), Point(2.0, 8.0))
                    .copy(switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(3)),
                segment(Point(2.0, 8.0), Point(0.0, 10.0)),
            )

        val locationTrack2 = locationTrack(trackNumberId, draft = true)
        val locationTrack3 = locationTrack(trackNumberId, draft = true)
        val locationTrack2Id = locationTrackService.saveDraft(LayoutBranch.main, locationTrack2, otherAlignment())
        val locationTrack3Id = locationTrackService.saveDraft(LayoutBranch.main, locationTrack3, otherAlignment())

        val validated =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(
                    locationTracks = listOf(locationTrack1.id, locationTrack2Id.id, locationTrack3Id.id),
                    switches = listOf(switchId),
                ),
            )
        val switchValidation = validated.validatedAsPublicationUnit.switches[0].issues
        assertContains(
            switchValidation,
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                "validation.layout.switch.track-linkage.multiple-tracks-through-joint",
                mapOf(
                    "locationTracks" to
                        "3 (${locationTrack2.name}, ${locationTrack3.name}), 4 (${locationTrack2.name}, ${locationTrack3.name})",
                    "switch" to "TV123",
                ),
            ),
        )
    }

    @Test
    fun `Switch validation requires a track to continue from the front joint`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId =
            switchService
                .saveDraft(
                    LayoutBranch.main,
                    switch(
                        name = "TV123",
                        joints = listOf(TrackLayoutSwitchJoint(JointNumber(1), Point(0.0, 0.0), null)),
                        structureId = switchStructureYV60_300_1_9().id as IntId,
                        stateCategory = LayoutStateCategory.EXISTING,
                        draft = true,
                    ),
                )
                .id
        val trackOn152Alignment =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    alignment(
                        segment(Point(0.0, 0.0), Point(5.0, 0.0))
                            .copy(
                                switchId = switchId,
                                startJointNumber = JointNumber(1),
                                endJointNumber = JointNumber(5),
                            ),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0))
                            .copy(
                                switchId = switchId,
                                startJointNumber = JointNumber(5),
                                endJointNumber = JointNumber(2),
                            ),
                    ),
                )
                .id
        val trackOn13Alignment =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    alignment(
                        segment(Point(0.0, 0.0), Point(10.0, 2.0))
                            .copy(
                                switchId = switchId,
                                startJointNumber = JointNumber(5),
                                endJointNumber = JointNumber(3),
                            )
                    ),
                )
                .id

        fun errorsWhenValidatingSwitchWithTracks(vararg locationTracks: IntId<LocationTrack>) =
            publicationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = locationTracks.toList(), switches = listOf(switchId)),
                )
                .validatedAsPublicationUnit
                .switches[0]
                .issues

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment),
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                LocalizationKey("validation.layout.switch.track-linkage.front-joint-not-connected"),
                LocalizationParams(mapOf("switch" to "TV123")),
            ),
        )

        val topoTrackMarkedAsDuplicate =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(
                        trackNumberId = trackNumberId,
                        topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)),
                        duplicateOf = trackOn13Alignment,
                        draft = true,
                    ),
                )
                .id

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment, topoTrackMarkedAsDuplicate),
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected",
                mapOf("switch" to "TV123"),
            ),
        )

        val goodTopoTrack =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(
                        trackNumberId,
                        topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)),
                        draft = true,
                    ),
                )
                .id

        val errors =
            errorsWhenValidatingSwitchWithTracks(
                trackOn152Alignment,
                trackOn13Alignment,
                topoTrackMarkedAsDuplicate,
                goodTopoTrack,
            )
        assertFalse(
            errors.any { e ->
                e.localizationKey.contains("validation.layout.switch.track-linkage.front-joint-not-connected") ||
                    e.localizationKey.contains(
                        "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected"
                    )
            }
        )
    }

    @Test
    fun `split target location track validation should fail if it's part of a published split`() {
        val splitSetup = simpleSplitSetup()

        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.targetTracks.first().first.id),
            )

        val errors = validateLocationTracks(listOf(draft.id))
        assertContains(
            errors,
            validationError(
                "validation.layout.split.track-split-in-progress",
                "sourceName" to locationTrackDao.fetch(splitSetup.sourceTrack.rowVersion).name,
            ),
        )
    }

    @Test
    fun `split target location track validation should not fail on finished split`() {
        val splitSetup = simpleSplitSetup()

        val splitId = saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        splitDao.updateSplit(splitId, bulkTransferState = BulkTransferState.DONE)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.targetTracks.first().first.id),
            )

        val errors = validateLocationTracks(listOf(draft.id))
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split source location track validation should fail if it's part of a published split`() {
        val splitSetup = simpleSplitSetup()

        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.sourceTrack.id),
            )

        val errors = validateLocationTracks(listOf(draft.id))
        assertContains(
            errors,
            validationError(
                "validation.layout.split.track-split-in-progress",
                "sourceName" to locationTrackDao.fetch(draft.rowVersion).name,
            ),
        )
    }

    @Test
    fun `split source location track validation should not fail on finished split`() {
        val splitSetup = simpleSplitSetup()

        val splitId = saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        splitDao.updateSplit(splitId, bulkTransferState = BulkTransferState.DONE)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.sourceTrack.id),
            )

        val errors = validateLocationTracks(listOf(draft.id))
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split source location track validation should fail if source location track isn't deleted`() {
        val splitSetup = simpleSplitSetup(LocationTrackState.IN_USE)

        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams).also(splitDao::get)

        val errors = validateLocationTracks(splitSetup.trackIds)
        assertContains(
            errors,
            LayoutValidationIssue(
                LayoutValidationIssueType.ERROR,
                LocalizationKey("validation.layout.split.source-not-deleted"),
                localizationParams("sourceName" to locationTrackDao.fetch(splitSetup.sourceTrack.rowVersion).name),
            ),
        )
    }

    @Test
    fun `split location track validation should fail if a target is on a different track number`() {
        val splitSetup = simpleSplitSetup()
        val startTarget = locationTrackDao.fetch(splitSetup.targetTracks.first().first.rowVersion)
        locationTrackDao.update(startTarget.copy(trackNumberId = mainDraftContext.createLayoutTrackNumber().id))

        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

        val errors = validateLocationTracks(splitSetup.trackIds)
        assertContains(
            errors,
            LayoutValidationIssue(
                LayoutValidationIssueType.ERROR,
                LocalizationKey("validation.layout.split.source-and-target-track-numbers-are-different"),
                localizationParams(
                    "targetName" to startTarget.name,
                    "sourceName" to locationTrackDao.fetch(splitSetup.sourceTrack.rowVersion).name,
                ),
            ),
        )
    }

    @Test
    fun `km post split validation should fail on unfinished split`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val kmPostId = mainDraftContext.insert(kmPost(trackNumberId = trackNumberId, km = KmNumber.ZERO)).id
        val locationTrackResponse = mainDraftContext.insert(locationTrack(trackNumberId), alignment())

        saveSplit(locationTrackResponse.rowVersion)

        val validation =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(locationTracks = listOf(locationTrackResponse.id), kmPosts = listOf(kmPostId)),
            )

        val errors = validation.validatedAsPublicationUnit.kmPosts.flatMap { it.issues }

        assertContains(
            errors,
            validationError(
                "validation.layout.split.affected-split-in-progress",
                "sourceName" to locationTrackDao.fetch(locationTrackResponse.rowVersion).name,
            ),
        )
    }

    @Test
    fun `reference line split validation should fail on unfinished split`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val referenceLineVersion =
            mainOfficialContext.insert(referenceLine(trackNumberId), alignment).let { v ->
                referenceLineService.saveDraft(LayoutBranch.main, referenceLineDao.fetch(v.rowVersion))
            }

        val locationTrackResponse = mainDraftContext.insert(locationTrack(trackNumberId), alignment)

        saveSplit(locationTrackResponse.rowVersion)

        val validation =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(
                    locationTracks = listOf(locationTrackResponse.id),
                    referenceLines = listOf(referenceLineVersion.id),
                ),
            )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.issues }

        assertContains(
            errors,
            validationError(
                "validation.layout.split.affected-split-in-progress",
                "sourceName" to locationTrackDao.fetch(locationTrackResponse.rowVersion).name,
            ),
        )
    }

    @Test
    fun `reference line split validation should not fail on finished splitting`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val referenceLine = mainOfficialContext.insert(referenceLine(trackNumberId), alignment)
        val locationTrackResponse = mainDraftContext.insert(locationTrack(trackNumberId), alignment)

        referenceLineDao.fetch(referenceLine.rowVersion).also { d ->
            referenceLineService.saveDraft(LayoutBranch.main, d)
        }

        saveSplit(locationTrackResponse.rowVersion).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplit(split.id, bulkTransferState = BulkTransferState.DONE)
        }

        val validation =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(referenceLines = listOf(referenceLine.id)),
            )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.issues }

        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split geometry validation should fail on geometry changes in source track`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        mainOfficialContext.insert(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val sourceTrackResponse =
            mainOfficialContext.insert(
                locationTrack(trackNumberId),
                alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0)), segment(Point(5.0, 0.0), Point(10.0, 0.0))),
            )

        val updatedSourceTrackResponse =
            alignmentDao
                .insert(
                    alignment(segment(Point(0.0, 0.0), Point(5.0, 5.0)), segment(Point(5.0, 5.0), Point(10.0, 0.0)))
                )
                .let { newAlignment ->
                    val lt =
                        locationTrackDao
                            .fetch(sourceTrackResponse.rowVersion)
                            .copy(state = LocationTrackState.DELETED, alignmentVersion = newAlignment)

                    locationTrackService.saveDraft(LayoutBranch.main, lt)
                }

        assertEquals(sourceTrackResponse.id, updatedSourceTrackResponse.id)
        assertNotEquals(sourceTrackResponse.rowVersion, updatedSourceTrackResponse.rowVersion)

        val startTargetTrackId =
            mainDraftContext
                .insert(locationTrack(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0))))
                .id

        val endTargetTrackId =
            mainDraftContext
                .insert(locationTrack(trackNumberId), alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))))
                .id

        saveSplit(updatedSourceTrackResponse.rowVersion, listOf(startTargetTrackId to 0..0, endTargetTrackId to 1..1))

        val errors = validateLocationTracks(sourceTrackResponse.id, startTargetTrackId, endTargetTrackId)

        assertTrue { errors.any { it.localizationKey == LocalizationKey("validation.layout.split.geometry-changed") } }
    }

    @Test
    fun `split geometry validation should fail on geometry changes in target track`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        mainOfficialContext.insert(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val sourceTrackResponse =
            mainOfficialContext
                .insert(
                    locationTrack(trackNumberId),
                    alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0)), segment(Point(5.0, 0.0), Point(10.0, 0.0))),
                )
                .let { response ->
                    val lt = locationTrackDao.fetch(response.rowVersion).copy(state = LocationTrackState.DELETED)
                    locationTrackService.saveDraft(LayoutBranch.main, lt)
                }

        val startTargetTrackId =
            mainDraftContext
                .insert(locationTrack(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(5.0, 10.0))))
                .id

        val endTargetTrackId =
            mainDraftContext
                .insert(locationTrack(trackNumberId), alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))))
                .id

        saveSplit(sourceTrackResponse.rowVersion, listOf(startTargetTrackId to 0..0, endTargetTrackId to 1..1))

        val errors = validateLocationTracks(sourceTrackResponse.id, startTargetTrackId, endTargetTrackId)
        assertTrue { errors.any { it.localizationKey == LocalizationKey("validation.layout.split.geometry-changed") } }
    }

    @Test
    fun `split validation should fail if the publication unit does not contain source track`() {
        val splitSetup = simpleSplitSetup()
        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

        val errors = validateLocationTracks(splitSetup.targetTracks.map { it.first.id })
        assertContains(errors, validationError("validation.layout.split.split-missing-location-tracks"))
    }

    @Test
    fun `split validation should fail if the publication unit does not contain target track`() {
        val splitSetup = simpleSplitSetup()
        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

        val errors = validateLocationTracks(listOf(splitSetup.sourceTrack.id))
        assertContains(errors, validationError("validation.layout.split.split-missing-location-tracks"))
    }

    @Test
    fun `Split validation should respect allowMultipleSplits`() {
        val splitSetup = simpleSplitSetup()
        val split =
            saveSplit(sourceTrackVersion = splitSetup.sourceTrack.rowVersion, targetTracks = splitSetup.targetParams)
                .let(splitDao::getOrThrow)

        val splitSetup2 = simpleSplitSetup()
        val split2 =
            saveSplit(sourceTrackVersion = splitSetup2.sourceTrack.rowVersion, targetTracks = splitSetup2.targetParams)
                .let(splitDao::getOrThrow)

        val trackValidationVersions = splitSetup.trackValidationVersions + splitSetup2.trackValidationVersions

        assertContains(
            validateSplitContent(trackValidationVersions, emptyList(), listOf(split, split2), false).map { it.second },
            validationError("validation.layout.split.multiple-splits-not-allowed"),
        )
        assertTrue(
            validateSplitContent(trackValidationVersions, emptyList(), listOf(split, split2), true)
                .map { it.second }
                .none { error -> error == validationError("validation.layout.split.multiple-splits-not-allowed") }
        )
    }

    @Test
    fun `Split validation should fail if switches are missing`() {
        val splitSetup = simpleSplitSetup()
        val switch = mainOfficialContext.createSwitch()
        val split =
            saveSplit(
                    sourceTrackVersion = splitSetup.sourceTrack.rowVersion,
                    targetTracks = splitSetup.targetParams,
                    switches = listOf(switch.id),
                )
                .let(splitDao::getOrThrow)

        assertContains(
            validateSplitContent(splitSetup.trackValidationVersions, emptyList(), listOf(split), false).map {
                it.second
            },
            validationError("validation.layout.split.split-missing-switches"),
        )
    }

    @Test
    fun `Split validation should fail if only switches are staged`() {
        val splitSetup = simpleSplitSetup()
        val switch = mainOfficialContext.createSwitch()
        val split =
            saveSplit(
                    sourceTrackVersion = splitSetup.sourceTrack.rowVersion,
                    targetTracks = splitSetup.targetParams,
                    switches = listOf(switch.id),
                )
                .let(splitDao::getOrThrow)

        assertContains(
            validateSplitContent(
                    emptyList(),
                    listOf(ValidationVersion(switch.id, switch.rowVersion)),
                    listOf(split),
                    false,
                )
                .map { it.second },
            validationError("validation.layout.split.split-missing-location-tracks"),
        )
    }

    @Test
    fun `Split validation should not fail if switches and location tracks are staged`() {
        val splitSetup = simpleSplitSetup()
        val switch = mainOfficialContext.createSwitch()
        val split =
            saveSplit(
                    sourceTrackVersion = splitSetup.sourceTrack.rowVersion,
                    targetTracks = splitSetup.targetParams,
                    switches = listOf(switch.id),
                )
                .let(splitDao::getOrThrow)

        assertEquals(
            0,
            validateSplitContent(
                    splitSetup.trackValidationVersions,
                    listOf(ValidationVersion(switch.id, switch.rowVersion)),
                    listOf(split),
                    false,
                )
                .size,
        )
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

        publishAndVerify(
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

        publishAndVerify(
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

        publishAndVerify(
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
        publishAndVerify(
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

        publishAndVerify(
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

        publishAndVerify(
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

        publishAndVerify(
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

        publishAndVerify(
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

        publishAndVerify(
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

        publishAndVerify(
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

        publishAndVerify(
            testBranch,
            publicationRequest(locationTracks = listOf(locationTrack), switches = listOf(switch)),
        )

        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        switchService.mergeToMainBranch(testBranch, switch)

        publishAndVerify(
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

        publishAndVerify(testBranch, publicationRequest(locationTracks = listOf(locationTrack)))
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        publishAndVerify(MainBranch.instance, publicationRequest(locationTracks = listOf(locationTrack)))
    }

    private fun validateLocationTracks(vararg locationTracks: IntId<LocationTrack>): List<LayoutValidationIssue> =
        validateLocationTracks(locationTracks.toList())

    private fun validateLocationTracks(locationTracks: List<IntId<LocationTrack>>): List<LayoutValidationIssue> {
        val publicationRequest = publicationRequestIds(locationTracks = locationTracks)
        val validation =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequest,
            )

        return validation.validatedAsPublicationUnit.locationTracks.flatMap { it.issues }
    }

    private fun saveSplit(
        sourceTrackVersion: LayoutRowVersion<LocationTrack>,
        targetTracks: List<Pair<IntId<LocationTrack>, IntRange>> = listOf(),
        switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    ): IntId<Split> {
        return splitDao.saveSplit(
            sourceTrackVersion,
            splitTargets = targetTracks.map { (id, indices) -> SplitTarget(id, indices, SplitTargetOperation.CREATE) },
            relinkedSwitches = switches,
            updatedDuplicates = emptyList(),
        )
    }

    data class SplitSetup(
        val sourceTrack: LayoutDaoResponse<LocationTrack>,
        val targetTracks: List<Pair<LayoutDaoResponse<LocationTrack>, IntRange>>,
    ) {

        val targetParams: List<Pair<IntId<LocationTrack>, IntRange>> =
            targetTracks.map { (track, range) -> track.id to range }

        val trackResponses = (listOf(sourceTrack) + targetTracks.map { it.first })

        val trackValidationVersions = trackResponses.map { ValidationVersion(it.id, it.rowVersion) }

        val trackIds = trackResponses.map { r -> r.id }
    }

    private fun simpleSplitSetup(
        sourceLocationTrackState: LocationTrackState = LocationTrackState.DELETED
    ): SplitSetup {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.insert(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val startSegment = segment(Point(0.0, 0.0), Point(5.0, 0.0))
        val endSegment = segment(Point(5.0, 0.0), Point(10.0, 0.0))
        val sourceTrack = mainOfficialContext.insert(locationTrack(trackNumberId), alignment(startSegment, endSegment))

        val draftSource =
            locationTrackDao.fetch(sourceTrack.rowVersion).copy(state = sourceLocationTrackState).let { d ->
                locationTrackService.saveDraft(LayoutBranch.main, d)
            }

        val startTrack = mainDraftContext.insert(locationTrack(trackNumberId), alignment(startSegment))

        val endTrack = mainDraftContext.insert(locationTrack(trackNumberId), alignment(endSegment))

        return SplitSetup(draftSource, listOf(startTrack to 0..0, endTrack to 1..1))
    }

    @Test
    fun `Location track validation catches only switch topology errors related to its own changes`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId =
            switchDao
                .insert(
                    switch(name = "TV123", structureId = switchStructureYV60_300_1_9().id as IntId, draft = false)
                        .copy(stateCategory = LayoutStateCategory.EXISTING)
                )
                .id
        val officialTrackOn152 =
            locationTrackDao.insert(
                locationTrack(
                    trackNumberId = trackNumberId,
                    alignmentVersion =
                        alignmentDao.insert(
                            alignment(
                                segment(Point(0.0, 5.0), Point(0.0, 0.0)),
                                segment(Point(0.0, 0.0), Point(5.0, 0.0))
                                    .copy(
                                        switchId = switchId,
                                        startJointNumber = JointNumber(1),
                                        endJointNumber = JointNumber(5),
                                    ),
                                segment(Point(5.0, 0.0), Point(10.0, 0.0))
                                    .copy(
                                        switchId = switchId,
                                        startJointNumber = JointNumber(5),
                                        endJointNumber = JointNumber(2),
                                    ),
                            )
                        ),
                    draft = false,
                )
            )
        val officialTrackOn13 =
            locationTrackDao.insert(
                locationTrack(
                    trackNumberId = trackNumberId,
                    alignmentVersion =
                        alignmentDao.insert(
                            alignment(
                                segment(Point(0.0, 0.0), Point(10.0, 2.0))
                                    .copy(
                                        switchId = switchId,
                                        startJointNumber = JointNumber(1),
                                        endJointNumber = JointNumber(3),
                                    )
                            )
                        ),
                    draft = false,
                )
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(officialTrackOn152.rowVersion).copy(state = LocationTrackState.DELETED),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(officialTrackOn13.rowVersion).copy(state = LocationTrackState.DELETED),
        )

        val errorsWhenDeletingStraightTrack = getLocationTrackValidationResult(officialTrackOn152.id).issues
        assertTrue(
            errorsWhenDeletingStraightTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("locationTracks") == "1-5-2" &&
                    error.params.get("switch") == "TV123"
            }
        )
        assertTrue(
            errorsWhenDeletingStraightTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.front-joint-not-connected") &&
                    error.params.get("switch") == "TV123"
            }
        )

        val errorsWhenDeletingBranchingTrack =
            publicationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = listOf(officialTrackOn13.id)),
                )
                .validatedAsPublicationUnit
                .locationTracks[0]
                .issues
        assertTrue(
            errorsWhenDeletingBranchingTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("locationTracks") == "1-3" &&
                    error.params.get("switch") == "TV123"
            }
        )
        assertFalse(
            errorsWhenDeletingBranchingTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.front-joint-not-connected")
            }
        )
    }

    @Test
    fun `Location track validation catches track removal causing switches to go unlinked`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId =
            switchDao
                .insert(
                    switch(
                        name = "TV123",
                        joints =
                            listOf(
                                TrackLayoutSwitchJoint(
                                    JointNumber(1),
                                    location = Point(0.0, 0.0),
                                    locationAccuracy = null,
                                )
                            ),
                        structureId = switchStructureYV60_300_1_9().id as IntId,
                        stateCategory = LayoutStateCategory.EXISTING,
                        draft = false,
                    )
                )
                .id
        val officialTrackOn152 =
            locationTrackDao.insert(
                locationTrack(
                    trackNumberId = trackNumberId,
                    alignmentVersion =
                        alignmentDao.insert(
                            alignment(
                                segment(Point(0.0, 0.0), Point(5.0, 0.0))
                                    .copy(
                                        switchId = switchId,
                                        startJointNumber = JointNumber(1),
                                        endJointNumber = JointNumber(5),
                                    ),
                                segment(Point(5.0, 0.0), Point(10.0, 0.0))
                                    .copy(
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
            LayoutBranch.main,
            locationTrackDao.fetch(officialTrackOn152.rowVersion).copy(state = LocationTrackState.DELETED),
        )
        locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                alignmentVersion =
                    alignmentDao.insert(
                        alignment(
                            segment(Point(0.0, 0.0), Point(10.0, 2.0))
                                .copy(
                                    switchId = switchId,
                                    startJointNumber = JointNumber(1),
                                    endJointNumber = JointNumber(3),
                                )
                        )
                    ),
                draft = false,
            )
        )

        val locationTrackDeletionErrors =
            publicationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = listOf(officialTrackOn152.id)),
                )
                .validatedAsPublicationUnit
                .locationTracks[0]
                .issues
        assertTrue(
            locationTrackDeletionErrors.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("locationTracks") == "1-5-2" &&
                    error.params.get("switch") == "TV123"
            }
        )
        // but it's OK if we link a replacement track
        val replacementTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                alignment(
                    segment(Point(0.0, 0.0), Point(5.0, 0.0))
                        .copy(switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(5)),
                    segment(Point(5.0, 0.0), Point(10.0, 0.0))
                        .copy(switchId = switchId, startJointNumber = JointNumber(5), endJointNumber = JointNumber(2)),
                ),
            )
        val errorsWithReplacementTrackLinked =
            publicationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = listOf(officialTrackOn152.id, replacementTrack.id)),
                )
                .validatedAsPublicationUnit
                .locationTracks[0]
                .issues
        assertFalse(
            errorsWithReplacementTrackLinked.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected")
            }
        )
    }

    @Test
    fun `Should fetch split details correctly`() {
        val splitSetup = simpleSplitSetup()
        saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

        val publicationId =
            publicationService
                .getValidationVersions(LayoutBranch.main, publicationRequest(locationTracks = splitSetup.trackIds))
                .let { versions ->
                    testPublish(LayoutBranch.main, versions, getCalculatedChangesInRequest(versions)).publicationId
                }

        val splitInPublication = publicationService.getSplitInPublication(publicationId!!)
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
        val splitSetup = simpleSplitSetup()

        val splitId = saveSplit(splitSetup.sourceTrack.rowVersion, splitSetup.targetParams)

        splitDao.get(splitId).let { split ->
            assertNotNull(split)
            assertEquals(null, split.publicationId)
        }

        val publicationId =
            publicationService
                .getValidationVersions(LayoutBranch.main, publicationRequest(locationTracks = splitSetup.trackIds))
                .let { versions ->
                    testPublish(LayoutBranch.main, versions, getCalculatedChangesInRequest(versions)).publicationId
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
        val splitSetup = simpleSplitSetup()

        val someSwitch = mainDraftContext.createSwitch()

        val splitId =
            saveSplit(
                sourceTrackVersion = splitSetup.sourceTrack.rowVersion,
                targetTracks = splitSetup.targetParams,
                switches = listOf(someSwitch.id),
            )

        publicationService
            .getValidationVersions(
                LayoutBranch.main,
                publicationRequest(locationTracks = splitSetup.trackIds, switches = listOf(someSwitch.id)),
            )
            .let { versions ->
                testPublish(LayoutBranch.main, versions, getCalculatedChangesInRequest(versions)).publicationId
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
    fun `track number created in design is reported as created`() {
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val trackNumber = testDBService.testContext(testBranch, DRAFT).insert(trackNumber()).id
        publish(publicationService, testBranch, trackNumbers = listOf(trackNumber))
        trackNumberService.mergeToMainBranch(testBranch, trackNumber)
        publish(publicationService, trackNumbers = listOf(trackNumber))
        val latestPub = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1)
        assertEquals(Operation.CREATE, latestPub[0].trackNumbers[0].operation)
    }

    @Test
    fun `location track created in design is reported as created`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val locationTrack =
            testDBService.testContext(testBranch, DRAFT).insert(locationTrack(trackNumber), alignment()).id
        publish(publicationService, testBranch, locationTracks = listOf(locationTrack))
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        publish(publicationService, locationTracks = listOf(locationTrack))
        val latestPub = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1)
        assertEquals(Operation.CREATE, latestPub[0].locationTracks[0].operation)
    }

    @Test
    fun `switch created in design is reported as created`() {
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val switch = testDBService.testContext(testBranch, DRAFT).insert(switch()).id
        publish(publicationService, testBranch, switches = listOf(switch))
        switchService.mergeToMainBranch(testBranch, switch)
        publish(publicationService, switches = listOf(switch))
        val latestPub = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1)
        assertEquals(Operation.CREATE, latestPub[0].switches[0].operation)
    }

    @Test
    fun `km post created in design is reported as created`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val kmPost = testDBService.testContext(testBranch, DRAFT).insert(kmPost(trackNumber, KmNumber(2))).id
        publish(publicationService, testBranch, kmPosts = listOf(kmPost))
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        publish(publicationService, kmPosts = listOf(kmPost))
        val latestPub = publicationService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1)
        assertEquals(Operation.CREATE, latestPub[0].kmPosts[0].operation)
    }

    @Test
    fun `duplicate km posts are fatal in validation`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.insert(kmPost(trackNumber, KmNumber(1)))
        mainOfficialContext.insert(referenceLineAndAlignment(trackNumber, segment(Point(0.0, 0.0), Point(2.0, 2.0))))
        val design = testDBService.createDesignBranch()
        val designKmPost = testDBService.testContext(design, OFFICIAL).insert(kmPost(trackNumber, KmNumber(1)))
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation = publicationService.validateKmPosts(transition, listOf(designKmPost.id)).first()

        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("$VALIDATION_GEOCODING.duplicate-km-posts")
            }
        )
    }

    @Test
    fun `duplicate switch names are found in merge to main`() {
        mainDraftContext.insert(switch(name = "ABC V123"))
        val design = testDBService.createDesignBranch()
        val designSwitch = testDBService.testContext(design, OFFICIAL).insert(switch(name = "ABC V123"))
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation = publicationService.validateSwitches(transition, listOf(designSwitch.id)).first()
        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("validation.layout.switch.duplicate-name-draft-in-main")
            }
        )
    }

    @Test
    fun `duplicate track numbers are found in merge to main`() {
        mainDraftContext.insert(trackNumber(number = TrackNumber("123")))
        val design = testDBService.createDesignBranch()
        val designTrackNumber =
            testDBService.testContext(design, OFFICIAL).insert(trackNumber(number = TrackNumber("123")))
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation =
            publicationService.validateTrackNumbersAndReferenceLines(transition, listOf(designTrackNumber.id)).first()
        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("validation.layout.track-number.duplicate-name-draft-in-main")
            }
        )
    }

    @Test
    fun `duplicate location track from draft mode are found`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        mainDraftContext.insert(
            locationTrackAndAlignment(
                trackNumber,
                name = "ABC 123",
                segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
            )
        )
        val design = testDBService.createDesignBranch()
        val designLocationTrack =
            testDBService
                .testContext(design, OFFICIAL)
                .insert(
                    locationTrackAndAlignment(
                        trackNumber,
                        name = "ABC 123",
                        segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
                    )
                )
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation = publicationService.validateLocationTracks(transition, listOf(designLocationTrack.id)).first()
        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("$VALIDATION_LOCATION_TRACK.duplicate-name-draft-in-main")
            }
        )
    }

    @Test
    fun `reference lines are validated on merge to main`() {
        val design = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(design, OFFICIAL)
        val trackNumberId = designOfficialContext.insert(trackNumber()).id
        val referenceLineId =
            designOfficialContext
                .insert(
                    // segment with bendy alignment
                    referenceLineAndAlignment(trackNumberId, segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(0.0, 1.0)))
                )
                .id
        val validated =
            publicationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(MergeFromDesign(design)),
                PublicationRequestIds(
                    trackNumbers = listOf(trackNumberId),
                    locationTracks = listOf(),
                    kmPosts = listOf(),
                    referenceLines = listOf(referenceLineId),
                    switches = listOf(),
                ),
            )
        val referenceLineIssues = validated.validatedAsPublicationUnit.referenceLines[0].issues
        assertEquals(1, referenceLineIssues.size)
        assertEquals(
            LocalizationKey("validation.layout.reference-line.points.not-continuous"),
            referenceLineIssues[0].localizationKey,
        )
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
            sourceTrack.rowVersion,
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

    private fun testPublish(
        layoutBranch: LayoutBranch,
        versions: ValidationVersions,
        calculatedChanges: CalculatedChanges,
    ): PublicationResult =
        publicationService.publishChanges(
            layoutBranch,
            versions,
            calculatedChanges,
            FreeTextWithNewLines.of("${this::class.simpleName}"),
        )
}

fun publicationRequestIds(
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
            val locationTrack = publishedLocationTracks.find { it.id == calculatedTrack.locationTrackId }
            assertNotNull(locationTrack)
            assertEquals(locationTrack.changedKmNumbers, calculatedTrack.changedKmNumbers)
        }
    }

    fun trackNumberEquals(
        calculatedTrackNumbers: List<TrackNumberChange>,
        publishedTrackNumbers: List<PublishedTrackNumber>,
    ) {
        calculatedTrackNumbers.forEach { calculatedTrackNumber ->
            val trackNumber = publishedTrackNumbers.find { it.id == calculatedTrackNumber.trackNumberId }
            assertNotNull(trackNumber)
            assertEquals(trackNumber.changedKmNumbers, calculatedTrackNumber.changedKmNumbers)
        }
    }

    calculatedChanges.directChanges.kmPostChanges.forEach { calculatedKmPostId ->
        assertTrue(publicationDetails.kmPosts.any { it.id == calculatedKmPostId })
    }

    calculatedChanges.directChanges.referenceLineChanges.forEach { calculatedReferenceLineId ->
        assertTrue(publicationDetails.referenceLines.any { it.id == calculatedReferenceLineId })
    }

    trackNumberEquals(calculatedChanges.directChanges.trackNumberChanges, publicationDetails.trackNumbers)
    locationTrackEquals(calculatedChanges.directChanges.locationTrackChanges, publicationDetails.locationTracks)

    calculatedChanges.directChanges.switchChanges.forEach { calculatedSwitch ->
        val switch = publicationDetails.switches.find { it.id == calculatedSwitch.switchId }
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
        val switch = publicationDetails.indirectChanges.switches.find { s -> s.id == calculatedSwitch.switchId }
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

private fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyVersionsAreDrafts(
    branch: LayoutBranch,
    dao: S,
    versions: List<ValidationVersion<T>>,
) {
    versions
        .map { v -> v.validatedAssetVersion }
        .map(dao::fetch)
        .forEach { asset ->
            assertTrue(asset.isDraft)
            assertEquals(branch, asset.branch)
        }
}

private fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublishingWorks(
    dao: S,
    service: LayoutAssetService<T, S>,
    create: () -> T,
    mutate: (orig: T) -> T,
) {
    // First id remains official
    val (officialId, draftVersion1) = service.saveDraft(LayoutBranch.main, create())
    assertEquals(1, draftVersion1.version)

    val officialVersion1 = publishAndCheck(draftVersion1, dao, service).first
    assertEquals(officialId, officialVersion1.id)
    // First row remains and is updated as official
    assertEquals(draftVersion1.rowId, officialVersion1.rowVersion.rowId)
    assertEquals(2, officialVersion1.rowVersion.version)

    val draftVersion2 = service.saveDraft(LayoutBranch.main, mutate(dao.fetch(officialVersion1.rowVersion)))
    assertEquals(officialId, draftVersion2.id)
    // Second draft must be a separate row to not touch the official
    assertNotEquals(officialVersion1.rowVersion.rowId, draftVersion2.rowVersion.rowId)
    assertEquals(1, draftVersion2.rowVersion.version)

    val officialVersion2 = publishAndCheck(draftVersion2.rowVersion, dao, service).first
    assertEquals(officialId, officialVersion2.id)
    // Second publish should update the original row again
    assertEquals(officialVersion1.rowVersion.rowId, officialVersion2.rowVersion.rowId)
    assertEquals(3, officialVersion2.rowVersion.version)
}

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> publishAndCheck(
    rowVersion: LayoutRowVersion<T>,
    dao: S,
    service: LayoutAssetService<T, S>,
): Pair<LayoutDaoResponse<T>, T> {
    val draft = dao.fetch(rowVersion)
    val id = draft.id

    assertTrue(id is IntId)
    assertNotEquals(rowVersion, dao.fetchVersion(MainLayoutContext.official, id))
    assertEquals(rowVersion, dao.fetchVersion(MainLayoutContext.draft, id))
    assertTrue(draft.isDraft)
    assertEquals(DataType.STORED, draft.dataType)
    assertEquals(
        MainDraftContextData(
            contextIdHolder = StoredContextIdHolder(rowVersion),
            officialRowId = draft.contextData.officialRowId,
            designRowId = null,
        ),
        draft.contextData,
    )

    val published = service.publish(LayoutBranch.main, ValidationVersion(id, rowVersion))
    assertEquals(id, published.id)
    assertEquals(published.rowVersion, dao.fetchVersionOrThrow(MainLayoutContext.official, id))
    assertEquals(published.rowVersion, dao.fetchVersion(MainLayoutContext.draft, id))

    val publishedItem = dao.fetch(published.rowVersion)
    assertFalse(publishedItem.isDraft)
    assertEquals(id, published.id)
    assertEquals(id.intValue, published.rowVersion.rowId.intValue)
    assertEquals(MainOfficialContextData(StoredContextIdHolder(published.rowVersion)), publishedItem.contextData)

    return published to publishedItem
}

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublished(
    layoutBranch: LayoutBranch,
    validationVersions: List<ValidationVersion<T>>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) = validationVersions.forEach { v -> verifyPublished(layoutBranch, v, dao, checkMatch) }

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyPublished(
    layoutBranch: LayoutBranch,
    validationVersion: ValidationVersion<T>,
    dao: S,
    checkMatch: (draft: T, published: T) -> Unit,
) {
    val currentOfficialVersion = dao.fetchVersionOrThrow(layoutBranch.official, validationVersion.officialId)
    val currentDraftVersion = dao.fetchVersionOrThrow(layoutBranch.draft, validationVersion.officialId)
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
        locationTrack(trackNumberId = trackNumberGenerator(), draft = true),
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
        ),
    )
}
