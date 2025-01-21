package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitTarget
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@Service
class PublicationTestSupportService
@Autowired
constructor(
    val publicationService: PublicationService,
    val publicationLogService: PublicationLogService,
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
    val switchStructureDao: SwitchStructureDao,
    val splitDao: SplitDao,
) : DBTestBase() {

    fun cleanupPublicationTables() {
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

    fun simpleSplitSetup(sourceLocationTrackState: LocationTrackState = LocationTrackState.DELETED): SplitSetup {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.insert(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val startSegment = segment(Point(0.0, 0.0), Point(5.0, 0.0))
        val endSegment = segment(Point(5.0, 0.0), Point(10.0, 0.0))
        val sourceTrack = mainOfficialContext.insert(locationTrack(trackNumberId), alignment(startSegment, endSegment))
        locationTrackDao.insertExternalId(sourceTrack.id, LayoutBranch.main, someOid())

        val draftSource =
            locationTrackDao.fetch(sourceTrack).copy(state = sourceLocationTrackState).let { d ->
                locationTrackService.saveDraft(LayoutBranch.main, d)
            }

        val startTrack = mainDraftContext.insert(locationTrack(trackNumberId), alignment(startSegment))

        val endTrack = mainDraftContext.insert(locationTrack(trackNumberId), alignment(endSegment))

        return SplitSetup(draftSource, listOf(startTrack to 0..0, endTrack to 1..1))
    }

    fun saveSplit(
        sourceTrackVersion: LayoutRowVersion<LocationTrack>,
        targetTracks: List<Pair<IntId<LocationTrack>, IntRange>> = listOf(),
        switches: List<IntId<LayoutSwitch>> = listOf(),
    ): IntId<Split> {
        return splitDao.saveSplit(
            sourceTrackVersion,
            splitTargets = targetTracks.map { (id, indices) -> SplitTarget(id, indices, SplitTargetOperation.CREATE) },
            relinkedSwitches = switches,
            updatedDuplicates = emptyList(),
        )
    }

    fun getCalculatedChangesInRequest(versions: ValidationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChanges(versions)

    fun publish(layoutBranch: LayoutBranch, request: PublicationRequestIds): PublicationResult {
        val versions = publicationService.getValidationVersions(layoutBranch, request)
        verifyVersions(request, versions)
        verifyVersionsAreDrafts(layoutBranch, versions)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        return testPublish(layoutBranch, versions, draftCalculatedChanges)
    }

    fun publishAndVerify(layoutBranch: LayoutBranch, request: PublicationRequestIds): PublicationResult {
        val versions = publicationService.getValidationVersions(layoutBranch, request)
        verifyVersions(request, versions)
        verifyVersionsAreDrafts(layoutBranch, versions)
        val draftCalculatedChanges = getCalculatedChangesInRequest(versions)
        val publicationResult = testPublish(layoutBranch, versions, draftCalculatedChanges)
        val publicationDetails = publicationLogService.getPublicationDetails(publicationResult.publicationId!!)
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

        if (layoutBranch == LayoutBranch.main) {
            assertEqualsCalculatedChanges(draftCalculatedChanges, publicationDetails)
        }
        return publicationResult
    }

    private fun verifyVersionsAreDrafts(branch: LayoutBranch, versions: ValidationVersions) {
        verifyVersionsAreDrafts(branch, trackNumberDao, versions.trackNumbers)
        verifyVersionsAreDrafts(branch, referenceLineDao, versions.referenceLines)
        verifyVersionsAreDrafts(branch, locationTrackDao, versions.locationTracks)
        verifyVersionsAreDrafts(branch, switchDao, versions.switches)
        verifyVersionsAreDrafts(branch, kmPostDao, versions.kmPosts)
    }

    fun testPublish(
        layoutBranch: LayoutBranch,
        versions: ValidationVersions,
        calculatedChanges: CalculatedChanges,
    ): PublicationResult =
        publicationService.publishChanges(
            layoutBranch,
            versions,
            calculatedChanges,
            FreeTextWithNewLines.of("${this::class.simpleName}"),
            PublicationCause.MANUAL,
        )
}

data class SplitSetup(
    val sourceTrack: LayoutRowVersion<LocationTrack>,
    val targetTracks: List<Pair<LayoutRowVersion<LocationTrack>, IntRange>>,
) {

    val targetParams: List<Pair<IntId<LocationTrack>, IntRange>> =
        targetTracks.map { (track, range) -> track.id to range }

    val trackResponses = (listOf(sourceTrack) + targetTracks.map { it.first })

    val trackIds = trackResponses.map { r -> r.id }
}

fun verifyVersions(publicationRequestIds: PublicationRequestIds, validationVersions: ValidationVersions) {
    verifyVersions(publicationRequestIds.trackNumbers, validationVersions.trackNumbers)
    verifyVersions(publicationRequestIds.referenceLines, validationVersions.referenceLines)
    verifyVersions(publicationRequestIds.kmPosts, validationVersions.kmPosts)
    verifyVersions(publicationRequestIds.locationTracks, validationVersions.locationTracks)
    verifyVersions(publicationRequestIds.switches, validationVersions.switches)
}

fun <T : LayoutAsset<T>> verifyVersions(ids: List<IntId<T>>, versions: List<LayoutRowVersion<T>>) {
    assertEquals(ids.size, versions.size)
    ids.forEach { id -> assertTrue(versions.any { v -> v.id == id }) }
}

fun <T : LayoutAsset<T>, S : LayoutAssetDao<T>> verifyVersionsAreDrafts(
    branch: LayoutBranch,
    dao: S,
    versions: List<LayoutRowVersion<T>>,
) {
    versions.map(dao::fetch).forEach { asset ->
        assertTrue(asset.isDraft)
        assertEquals(branch, asset.branch)
    }
}

fun assertEqualsCalculatedChanges(calculatedChanges: CalculatedChanges, publicationDetails: PublicationDetails) {
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
