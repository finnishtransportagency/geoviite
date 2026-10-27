package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.DesignState
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

private val TRACK_NUMBER_SEGMENT = segment(Point(0.0, 0.0), Point(200.0, 0.0))
private val TRACK_SEGMENT = segment(Point(0.0, 0.0), Point(100.0, 0.0))

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtDesignIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val layoutDesignService: LayoutDesignService,
    private val layoutDesignDao: LayoutDesignDao,
    private val publicationDao: PublicationDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
) : DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Design collection should be returned with design metadata`() {
        assertEquals(emptyList<ExtTestDesignV1>(), api.designCollection.get().suunnitelmat)

        initUser()
        val design1 = layoutDesignDao.fetch(testDBService.createLayoutDesign())
        val design2 = layoutDesignDao.fetch(testDBService.createLayoutDesign())

        api.designCollection.get().suunnitelmat.let { results ->
            assertEquals(2, results.size)
            assertMatches(design1, results.single { it.suunnitelma_oid == design1.externalId.toString() })
            assertMatches(design2, results.single { it.suunnitelma_oid == design2.externalId.toString() })
        }
    }

    @Test
    fun `Single design metadata should be returned by oid`() {
        initUser()
        val design = layoutDesignDao.fetch(testDBService.createLayoutDesign())

        assertMatches(design, api.designs.get(design.externalId).suunnitelma)

        api.designs.getWithExpectedError("1.2.246.578.13.999.999", httpStatus = HttpStatus.NOT_FOUND)
        api.designs.getWithExpectedError("not-an-oid", httpStatus = HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Design creation and metadata changes should be returned between layout versions`() {
        val (designBranch, design, mainPublication, designPublication, tnId) = createDesignWithPublications()
        val designOid = design.externalId

        // The design's first publication makes it visible in the API and is reported as a design modification
        api.designs.getModifiedBetween(designOid, mainPublication.uuid, designPublication.uuid).let { result ->
            assertEquals(mainPublication.uuid.toString(), result.alkuversio)
            assertEquals(designPublication.uuid.toString(), result.loppuversio)
            assertMatches(design, result.suunnitelma)
        }
        api.designCollection.getModifiedBetween(mainPublication.uuid, designPublication.uuid).let { result ->
            assertMatches(design, result.suunnitelmat.single())
        }

        initUser()
        layoutDesignService.update(designBranch.designId, layoutDesign(name = testDBService.getUnusedDesignName()))
        val changePublication = publicationDao.list(LayoutBranchType.DESIGN).first()
        assertEquals(PublicationCause.LAYOUT_DESIGN_CHANGE, changePublication.cause)
        val renamedDesign = layoutDesignDao.fetch(designBranch.designId)

        api.designs.getModifiedSince(designOid, mainPublication.uuid).let { result ->
            assertEquals(mainPublication.uuid.toString(), result.alkuversio)
            assertEquals(changePublication.uuid.toString(), result.loppuversio)
            assertMatches(renamedDesign, result.suunnitelma)
        }
        api.designs.getModifiedBetween(designOid, designPublication.uuid, changePublication.uuid).let { result ->
            assertEquals(designPublication.uuid.toString(), result.alkuversio)
            assertEquals(changePublication.uuid.toString(), result.loppuversio)
            assertMatches(renamedDesign, result.suunnitelma)
        }
        api.designCollection.getModifiedSince(mainPublication.uuid).let { result ->
            assertEquals(mainPublication.uuid.toString(), result.alkuversio)
            assertEquals(changePublication.uuid.toString(), result.loppuversio)
            assertMatches(renamedDesign, result.suunnitelmat.single())
        }

        // Version ranges after the design's first publication with no metadata changes have no modifications
        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0003.000")) }
        val mainPublication2 = testDBService.publish(trackNumbers = listOf(tnId))
        api.designs.assertNoModificationBetween(designOid, changePublication.uuid, mainPublication2.uuid)
        api.designs.assertNoModificationBetween(designOid, mainPublication.uuid, mainPublication.uuid)
        api.designCollection.assertNoModificationBetween(changePublication.uuid, mainPublication2.uuid)

        api.designs.getModifiedWithExpectedError(
            "1.2.246.578.13.999.999",
            TRACK_LAYOUT_VERSION_FROM to mainPublication.uuid.toString(),
            httpStatus = HttpStatus.NOT_FOUND,
        )
    }

    @Test
    fun `Design without publications is not reported in modification listings`() {
        initUser()
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val mainPublication = testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val design = layoutDesignDao.fetch(designBranch.designId)

        // Metadata is fetchable by oid and listed in the collection even before the design's first publication...
        assertMatches(design, api.designs.get(design.externalId).suunnitelma)
        assertMatches(design, api.designCollection.get().suunnitelmat.single())

        // ...but with no publications, the design is not reported as modified
        api.designs.assertNoModificationSince(design.externalId, mainPublication.uuid)
        api.designCollection.assertNoModificationSince(mainPublication.uuid)
    }

    @Test
    fun `Design deletion should be reported and the deleted design should remain fetchable by oid`() {
        val (designBranch, design, mainPublication, designPublication) = createDesignWithPublications()
        val designOid = design.externalId

        initUser()
        val renamedName = testDBService.getUnusedDesignName()
        layoutDesignService.update(designBranch.designId, layoutDesign(name = renamedName))

        initUser()
        layoutDesignService.update(
            designBranch.designId,
            layoutDesign(name = renamedName, designState = DesignState.DELETED),
        )
        val deletePublication =
            publicationDao.list(LayoutBranchType.DESIGN).first { publication ->
                publication.cause == PublicationCause.LAYOUT_DESIGN_DELETE
            }
        val deletedDesign = layoutDesignDao.fetch(designBranch.designId)
        assertEquals(DesignState.DELETED, deletedDesign.designState)

        // A deleted design is no longer part of the design collection, but its metadata remains fetchable by oid
        assertEquals(emptyList<ExtTestDesignV1>(), api.designCollection.get().suunnitelmat)
        api.designs.get(designOid).suunnitelma.let { result ->
            assertMatches(deletedDesign, result)
            assertEquals("poistettu", result.tila)
        }

        // The deletion is reported as a design modification
        api.designs.getModifiedBetween(designOid, designPublication.uuid, deletePublication.uuid).let { result ->
            assertEquals(designPublication.uuid.toString(), result.alkuversio)
            assertEquals(deletePublication.uuid.toString(), result.loppuversio)
            assertMatches(deletedDesign, result.suunnitelma)
        }

        // Several metadata changes of one design within the range are reported as a single design entry
        api.designCollection.getModifiedSince(mainPublication.uuid).let { result ->
            assertMatches(deletedDesign, result.suunnitelmat.single())
        }
        api.designs.getModifiedSince(designOid, mainPublication.uuid).let { result ->
            assertMatches(deletedDesign, result.suunnitelma)
        }
    }

    @Test
    fun `Design location track collection lists the design's own tracks in every state with design content`() {
        val tn = insertMainTrackNumber()
        val openTrack = insertMainTrack(tn, "main open")
        val cancelledTrack = insertMainTrack(tn, "main cancelled")
        val completedTrack = insertMainTrack(tn, "main completed")
        val mainOnlyTrack = insertMainTrack(tn, "main only")
        testDBService.publish(
            trackNumbers = listOf(tn),
            locationTracks = listOf(openTrack.id, cancelledTrack.id, completedTrack.id, mainOnlyTrack.id),
        )

        val design = createDesign()
        editInDesign(design, openTrack, "design open")
        editInDesign(design, cancelledTrack, "design cancelled")
        editInDesign(design, completedTrack, "design completed")
        testDBService.publish(
            design.branch,
            locationTracks = listOf(openTrack.id, cancelledTrack.id, completedTrack.id),
        )
        cancelInDesign(design, cancelledTrack)
        completeInDesign(design, completedTrack)

        api.locationTrackCollectionInDesign(design.oid).get().sijaintiraiteet.also { tracks ->
            assertEquals(
                setOf(
                    "design open" to FI_DESIGN_ITEM_OPEN,
                    "design cancelled" to FI_DESIGN_ITEM_CANCELLED,
                    "design completed" to FI_DESIGN_ITEM_COMPLETED,
                ),
                tracks.map { track -> track.kuvaus to track.kohteen_tila_suunnitelmassa }.toSet(),
            )
        }
    }

    @Test
    fun `Design location track in layout state DELETED is listed and served`() {
        val tn = insertMainTrackNumber()
        val track = insertMainTrack(tn, "main")
        testDBService.publish(trackNumbers = listOf(tn), locationTracks = listOf(track.id))

        val design = createDesign()
        testDBService.generateOid(track.id, design.branch)
        design.draftContext.mutate(track.id) { lt -> lt.copy(state = LocationTrackState.DELETED) }
        testDBService.publish(design.branch, locationTracks = listOf(track.id))

        api.locationTrackCollectionInDesign(design.oid).get().sijaintiraiteet.single().also { lt ->
            assertEquals(FI_DELETED, lt.tila)
            assertEquals(FI_DESIGN_ITEM_OPEN, lt.kohteen_tila_suunnitelmassa)
        }
        api.locationTracksInDesign(design.oid).get(track.oid).sijaintiraide.also { lt ->
            assertEquals(FI_DELETED, lt.tila)
        }
    }

    @Test
    fun `Completed design location track keeps its design content after later main edits`() {
        val tn = insertMainTrackNumber()
        val track = insertMainTrack(tn, "main 1")
        testDBService.publish(trackNumbers = listOf(tn), locationTracks = listOf(track.id))

        val design = createDesign()
        editInDesign(design, track, "design")
        val designPublication = testDBService.publish(design.branch, locationTracks = listOf(track.id))
        val completionPublication = completeInDesign(design, track)

        mainDraftContext.mutate(track.id) { lt -> lt.copy(description = FreeText("main 2")) }
        testDBService.publish(locationTracks = listOf(track.id))

        val designApi = api.locationTracksInDesign(design.oid)
        designApi.get(track.oid).sijaintiraide.also { lt ->
            assertEquals("design", lt.kuvaus)
            assertEquals(FI_DESIGN_ITEM_COMPLETED, lt.kohteen_tila_suunnitelmassa)
        }
        // The completion itself is a modification of the design's track
        designApi.getModifiedBetween(track.oid, designPublication.uuid, completionPublication.uuid).also { response ->
            assertEquals("design", response.sijaintiraide.kuvaus)
            assertEquals(FI_DESIGN_ITEM_COMPLETED, response.sijaintiraide.kohteen_tila_suunnitelmassa)
        }
        api.locationTrackCollectionInDesign(design.oid)
            .getModifiedBetween(designPublication.uuid, completionPublication.uuid)
            .also { response ->
                assertEquals(FI_DESIGN_ITEM_COMPLETED, response.sijaintiraiteet.single().kohteen_tila_suunnitelmassa)
            }
        assertEquals("main 2", api.locationTracks.get(track.oid).sijaintiraide.kuvaus)
    }

    @Test
    fun `Cancelled design location track modification is reported with cancelled state`() {
        val tn = insertMainTrackNumber()
        val track = insertMainTrack(tn, "main")
        testDBService.publish(trackNumbers = listOf(tn), locationTracks = listOf(track.id))

        val design = createDesign()
        editInDesign(design, track, "design")
        val designPublication = testDBService.publish(design.branch, locationTracks = listOf(track.id))
        val cancellationPublication = cancelInDesign(design, track)

        api.locationTracksInDesign(design.oid)
            .getModifiedBetween(track.oid, designPublication.uuid, cancellationPublication.uuid)
            .also { response ->
                assertEquals("design", response.sijaintiraide.kuvaus)
                assertEquals(FI_DESIGN_ITEM_CANCELLED, response.sijaintiraide.kohteen_tila_suunnitelmassa)
            }
        api.locationTrackCollectionInDesign(design.oid)
            .getModifiedBetween(designPublication.uuid, cancellationPublication.uuid)
            .also { response ->
                assertEquals(FI_DESIGN_ITEM_CANCELLED, response.sijaintiraiteet.single().kohteen_tila_suunnitelmassa)
            }
    }

    @Test
    fun `Re-editing a completed design location track makes it open again with the new content`() {
        val tn = insertMainTrackNumber()
        val track = insertMainTrack(tn, "main")
        testDBService.publish(trackNumbers = listOf(tn), locationTracks = listOf(track.id))

        val design = createDesign()
        editInDesign(design, track, "design")
        testDBService.publish(design.branch, locationTracks = listOf(track.id))
        completeInDesign(design, track)

        design.draftContext.mutate(track.id) { lt -> lt.copy(description = FreeText("re-edited")) }
        testDBService.publish(design.branch, locationTracks = listOf(track.id))

        api.locationTracksInDesign(design.oid).get(track.oid).sijaintiraide.also { lt ->
            assertEquals("re-edited", lt.kuvaus)
            assertEquals(FI_DESIGN_ITEM_OPEN, lt.kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Cancelled design-created location track on a cancelled design-created track number is listed`() {
        initUser()
        val design = createDesign()
        val ltOid = insertCancelledDesignCreatedTrackOnCancelledTrackNumber(design)

        api.locationTrackCollectionInDesign(design.oid).get().sijaintiraiteet.single().also { lt ->
            assertEquals("design created", lt.kuvaus)
            assertEquals(FI_DESIGN_ITEM_CANCELLED, lt.kohteen_tila_suunnitelmassa)
            assertNull(lt.alkusijainti?.rataosoite)
            assertNull(lt.loppusijainti?.rataosoite)
        }
        api.locationTracksInDesign(design.oid).get(ltOid).sijaintiraide.also { lt ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, lt.kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design location track geometry has no addresses when its track number has no geocoding context`() {
        initUser()
        val design = createDesign()
        val ltOid = insertCancelledDesignCreatedTrackOnCancelledTrackNumber(design)

        api.locationTrackGeometryInDesign(design.oid).get(ltOid).also { response ->
            assertEquals(ltOid.toString(), response.sijaintiraide_oid)
            assertNull(response.osoitevali)
        }
    }

    @Test
    fun `Completed design location track follows main addressing changes through the design's own publications`() {
        initUser()
        val (tn, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(300.0, 0.0))),
            )
        val kmPostId = mainDraftContext.save(kmPost(tn, KmNumber(1), gkLocation = kmPostGkLocation(100.0, 0.0))).id
        val track =
            mainDraftContext
                .saveWithOid(
                    locationTrack(tn, description = "main"),
                    trackGeometryOfSegments(segment(Point(150.0, 0.0), Point(250.0, 0.0))),
                )
                .let { (id, oid) -> TrackIds(id, oid) }
        testDBService.publish(trackNumbers = listOf(tn), kmPosts = listOf(kmPostId), locationTracks = listOf(track.id))

        val design = createDesign()
        editInDesign(design, track, "design")
        testDBService.publish(design.branch, locationTracks = listOf(track.id))
        val completionPublication = completeInDesign(design, track)

        initUser()
        mainDraftContext.mutate(kmPostId) { post -> post.copy(gkLocation = kmPostGkLocation(120.0, 0.0)) }
        testDBService.publishAndValidate(kmPosts = listOf(kmPostId))
        val inheritedPublication = latestDesignPublication()
        assertNotEquals(completionPublication.id, inheritedPublication.id)
        assertEquals(PublicationCause.CALCULATED_CHANGE, inheritedPublication.cause)

        val designApi = api.locationTracksInDesign(design.oid)
        val addressesAt = { publication: Publication ->
            designApi.getAtVersion(track.oid, publication.uuid).sijaintiraide.let { lt ->
                lt.alkusijainti?.rataosoite to lt.loppusijainti?.rataosoite
            }
        }
        assertEquals("0001+0050.000" to "0001+0150.000", addressesAt(completionPublication))
        assertEquals("0001+0030.000" to "0001+0130.000", addressesAt(inheritedPublication))
        // Each version's addresses are fixed
        assertEquals("0001+0050.000" to "0001+0150.000", addressesAt(completionPublication))

        designApi.getModifiedBetween(track.oid, completionPublication.uuid, inheritedPublication.uuid).also { response
            ->
            assertEquals(FI_DESIGN_ITEM_COMPLETED, response.sijaintiraide.kohteen_tila_suunnitelmassa)
            assertEquals("0001+0030.000", response.sijaintiraide.alkusijainti?.rataosoite)
        }
    }

    @Test
    fun `Design location track sub-resources return the cancelled design row's geometry`() {
        val tn = insertMainTrackNumber()
        val track = insertMainTrack(tn, "main")
        testDBService.publish(trackNumbers = listOf(tn), locationTracks = listOf(track.id))

        val design = createDesign()
        testDBService.generateOid(track.id, design.branch)
        val (designTrack, _) = design.draftContext.fetchLocationTrackWithGeometry(track.id)!!
        design.draftContext.save(designTrack, trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(60.0, 0.0))))
        testDBService.publish(design.branch, locationTracks = listOf(track.id))
        cancelInDesign(design, track)

        val designEndAddress = "0000+0060.000"
        api.locationTrackGeometryInDesign(design.oid).get(track.oid).also { response ->
            assertEquals(designEndAddress, response.osoitevali?.loppuosoite)
        }
        api.locationTrackProfileInDesign(design.oid).get(track.oid).also { response ->
            assertEquals(designEndAddress, response.osoitevali.loppu)
        }
        api.locationTrackElementListingInDesign(design.oid).get(track.oid).also { response ->
            assertEquals(designEndAddress, response.osoitevalit.last().loppu)
        }
        assertEquals("0000+0100.000", api.locationTrackGeometry.get(track.oid).osoitevali?.loppuosoite)
    }

    @Test
    fun `Design item state is absent from main location track responses`() {
        val tn = insertMainTrackNumber()
        val track = insertMainTrack(tn, "main")
        testDBService.publish(trackNumbers = listOf(tn), locationTracks = listOf(track.id))

        listOf(
                "/geoviite/paikannuspohja/v1/sijaintiraiteet/${track.oid}",
                "/geoviite/paikannuspohja/v1/sijaintiraiteet",
            )
            .forEach { url ->
                val body = api.testApiConnection.doGetWithParams(url, emptyMap(), HttpStatus.OK)
                assertFalse(body.contains(DESIGN_ITEM_STATE), "$url should not contain $DESIGN_ITEM_STATE: $body")
            }
    }

    private data class TestDesign(
        val branch: DesignBranch,
        val oid: Oid<LayoutDesign>,
        val draftContext: TestLayoutContext,
    )

    private data class TrackIds(val id: IntId<LocationTrack>, val oid: Oid<LocationTrack>)

    private fun createDesign(): TestDesign {
        val branch = testDBService.createDesignBranch()
        return TestDesign(
            branch,
            layoutDesignDao.fetch(branch.designId).externalId,
            testDBService.testContext(branch, PublicationState.DRAFT),
        )
    }

    private fun insertMainTrackNumber(): IntId<LayoutTrackNumber> {
        initUser()
        return mainDraftContext
            .saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()), referenceLineGeometry(TRACK_NUMBER_SEGMENT))
            .first
    }

    private fun insertMainTrack(trackNumberId: IntId<LayoutTrackNumber>, description: String): TrackIds =
        mainDraftContext
            .saveWithOid(
                locationTrack(trackNumberId, description = description),
                trackGeometryOfSegments(TRACK_SEGMENT),
            )
            .let { (id, oid) -> TrackIds(id, oid) }

    private fun editInDesign(design: TestDesign, track: TrackIds, description: String) {
        testDBService.generateOid(track.id, design.branch)
        design.draftContext.mutate(track.id) { lt -> lt.copy(description = FreeText(description)) }
    }

    private fun cancelInDesign(design: TestDesign, track: TrackIds): Publication {
        locationTrackService.cancel(design.branch, track.id)
        return testDBService.publish(design.branch, locationTracks = listOf(track.id))
    }

    /**
     * Merges the design's track to main and publishes it there. The publication completes the track in the design
     * through an automatic design publication, which is returned.
     */
    private fun completeInDesign(design: TestDesign, track: TrackIds): Publication {
        locationTrackService.mergeToMainBranch(design.branch, track.id)
        testDBService.publish(locationTracks = listOf(track.id))
        return latestDesignPublication()
    }

    private fun insertCancelledDesignCreatedTrackOnCancelledTrackNumber(design: TestDesign): Oid<LocationTrack> {
        val tnId =
            design.draftContext
                .save(trackNumber(testDBService.getUnusedTrackNumber()), referenceLineGeometry(TRACK_SEGMENT))
                .id
        val ltId =
            design.draftContext
                .save(locationTrack(tnId, description = "design created"), trackGeometryOfSegments(TRACK_SEGMENT))
                .id
        testDBService.generateOid(tnId, design.branch)
        val ltOid = testDBService.generateOid(ltId, design.branch)
        testDBService.publish(design.branch, trackNumbers = listOf(tnId), locationTracks = listOf(ltId))

        trackNumberService.cancel(design.branch, tnId)
        locationTrackService.cancel(design.branch, ltId)
        testDBService.publish(design.branch, trackNumbers = listOf(tnId), locationTracks = listOf(ltId))
        return ltOid
    }

    private fun latestDesignPublication(): Publication = publicationDao.list(LayoutBranchType.DESIGN).first()

    private data class DesignSetup(
        val designBranch: DesignBranch,
        val design: LayoutDesign,
        val mainPublication: Publication,
        val designPublication: Publication,
        val tnId: IntId<LayoutTrackNumber>,
    )

    /**
     * Creates a main publication and a design with one published track number change, so that the design has
     * publications and subsequent metadata updates create automatic design change publications.
     */
    private fun createDesignWithPublications(): DesignSetup {
        initUser()
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val mainPublication = testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val design = layoutDesignDao.fetch(designBranch.designId)
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        val designPublication = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        return DesignSetup(designBranch, design, mainPublication, designPublication, tnId)
    }

    private fun assertMatches(design: LayoutDesign, result: ExtTestDesignV1) {
        assertEquals(design.externalId.toString(), result.suunnitelma_oid)
        assertEquals(design.name.toString(), result.nimi)
        assertEquals(design.estimatedCompletion.toString(), result.suunniteltu_valmistumispaiva)
        val expectedState =
            when (design.designState) {
                DesignState.ACTIVE -> FI_DESIGN_ACTIVE
                DesignState.DELETED -> FI_DESIGN_DELETED
                DesignState.COMPLETED -> FI_DESIGN_COMPLETED
            }
        assertEquals(expectedState, result.tila)
    }
}
