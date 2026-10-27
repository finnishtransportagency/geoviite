package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.DesignState
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
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
import fi.fta.geoviite.infra.tracklayout.switch
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
    private val switchService: LayoutSwitchService,
    private val extTestDataService: ExtApiTestDataServiceV1,
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
    fun `Design switch collection lists the design's own switches in every state with design content`() {
        val openSwitch = insertMainSwitch("MAIN V001")
        val cancelledSwitch = insertMainSwitch("MAIN V002")
        val completedSwitch = insertMainSwitch("MAIN V003")
        val mainOnlySwitch = insertMainSwitch("MAIN V004")
        val oidOnlySwitch = insertMainSwitch("MAIN V005")
        testDBService.publish(
            switches = listOf(openSwitch, cancelledSwitch, completedSwitch, mainOnlySwitch, oidOnlySwitch).map { it.id }
        )

        val design = createDesign()
        editSwitchInDesign(design, openSwitch, "DSGN V001")
        editSwitchInDesign(design, cancelledSwitch, "DSGN V002")
        editSwitchInDesign(design, completedSwitch, "DSGN V003")
        testDBService.generateOid(oidOnlySwitch.id, design.branch)
        testDBService.publish(design.branch, switches = listOf(openSwitch.id, cancelledSwitch.id, completedSwitch.id))
        cancelSwitchInDesign(design, cancelledSwitch)
        completeSwitchInDesign(design, completedSwitch)

        api.switchCollectionInDesign(design.oid).get().vaihteet.also { switches ->
            assertEquals(
                setOf(
                    "DSGN V001" to FI_DESIGN_ITEM_OPEN,
                    "DSGN V002" to FI_DESIGN_ITEM_CANCELLED,
                    "DSGN V003" to FI_DESIGN_ITEM_COMPLETED,
                ),
                switches.map { switch -> switch.vaihdetunnus to switch.kohteen_tila_suunnitelmassa }.toSet(),
            )
        }
    }

    @Test
    fun `Design switch in layout state NOT_EXISTING is listed and served`() {
        val switch = insertMainSwitch("MAIN V001")
        testDBService.publish(switches = listOf(switch.id))

        val design = createDesign()
        testDBService.generateOid(switch.id, design.branch)
        design.draftContext.mutate(switch.id) { s -> s.copy(stateCategory = LayoutStateCategory.NOT_EXISTING) }
        testDBService.publish(design.branch, switches = listOf(switch.id))

        api.switchCollectionInDesign(design.oid).get().vaihteet.single().also { s ->
            assertEquals(FI_NOT_EXISTING, s.tilakategoria)
            assertEquals(FI_DESIGN_ITEM_OPEN, s.kohteen_tila_suunnitelmassa)
        }
        api.switchInDesign(design.oid).get(switch.oid).vaihde.also { s ->
            assertEquals(FI_NOT_EXISTING, s.tilakategoria)
        }
    }

    @Test
    fun `Completed design switch keeps its design content after later main edits`() {
        val switch = insertMainSwitch("MAIN V001")
        testDBService.publish(switches = listOf(switch.id))

        val design = createDesign()
        editSwitchInDesign(design, switch, "DSGN V001")
        val designPublication = testDBService.publish(design.branch, switches = listOf(switch.id))
        val completionPublication = completeSwitchInDesign(design, switch)

        mainDraftContext.mutate(switch.id) { s -> s.copy(name = SwitchName("MAIN V002")) }
        testDBService.publish(switches = listOf(switch.id))

        val designApi = api.switchInDesign(design.oid)
        designApi.get(switch.oid).vaihde.also { s ->
            assertEquals("DSGN V001", s.vaihdetunnus)
            assertEquals(FI_DESIGN_ITEM_COMPLETED, s.kohteen_tila_suunnitelmassa)
        }
        // The completion itself is a modification of the design's switch
        designApi.getModifiedBetween(switch.oid, designPublication.uuid, completionPublication.uuid).also { response ->
            assertEquals("DSGN V001", response.vaihde.vaihdetunnus)
            assertEquals(FI_DESIGN_ITEM_COMPLETED, response.vaihde.kohteen_tila_suunnitelmassa)
        }
        api.switchCollectionInDesign(design.oid)
            .getModifiedBetween(designPublication.uuid, completionPublication.uuid)
            .also { response ->
                assertEquals(FI_DESIGN_ITEM_COMPLETED, response.vaihteet.single().kohteen_tila_suunnitelmassa)
            }
        assertEquals("MAIN V002", api.switch.get(switch.oid).vaihde.vaihdetunnus)
    }

    @Test
    fun `Cancelled design switch modification is reported with cancelled state`() {
        val switch = insertMainSwitch("MAIN V001")
        testDBService.publish(switches = listOf(switch.id))

        val design = createDesign()
        editSwitchInDesign(design, switch, "DSGN V001")
        val designPublication = testDBService.publish(design.branch, switches = listOf(switch.id))
        val cancellationPublication = cancelSwitchInDesign(design, switch)

        api.switchInDesign(design.oid)
            .getModifiedBetween(switch.oid, designPublication.uuid, cancellationPublication.uuid)
            .also { response ->
                assertEquals("DSGN V001", response.vaihde.vaihdetunnus)
                assertEquals(FI_DESIGN_ITEM_CANCELLED, response.vaihde.kohteen_tila_suunnitelmassa)
            }
        api.switchCollectionInDesign(design.oid)
            .getModifiedBetween(designPublication.uuid, cancellationPublication.uuid)
            .also { response ->
                assertEquals(FI_DESIGN_ITEM_CANCELLED, response.vaihteet.single().kohteen_tila_suunnitelmassa)
            }
    }

    @Test
    fun `Re-editing a completed design switch makes it open again with the new content`() {
        val switch = insertMainSwitch("MAIN V001")
        testDBService.publish(switches = listOf(switch.id))

        val design = createDesign()
        editSwitchInDesign(design, switch, "DSGN V001")
        testDBService.publish(design.branch, switches = listOf(switch.id))
        completeSwitchInDesign(design, switch)

        design.draftContext.mutate(switch.id) { s -> s.copy(name = SwitchName("DSGN V002")) }
        testDBService.publish(design.branch, switches = listOf(switch.id))

        api.switchInDesign(design.oid).get(switch.oid).vaihde.also { s ->
            assertEquals("DSGN V002", s.vaihdetunnus)
            assertEquals(FI_DESIGN_ITEM_OPEN, s.kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Cancelled design-created switch on cancelled design-created tracks is listed without track links`() {
        initUser()
        val design = createDesign()
        val setup = extTestDataService.insertSwitchAndTracks(design.draftContext)
        val trackIds = setup.tracks.map { track -> track.id }
        testDBService.publish(
            design.branch,
            trackNumbers = listOf(setup.trackNumber.id),
            locationTracks = trackIds,
            switches = listOf(setup.switch.id),
        )

        trackNumberService.cancel(design.branch, setup.trackNumber.id)
        trackIds.forEach { id -> locationTrackService.cancel(design.branch, id) }
        switchService.cancel(design.branch, setup.switch.id)
        testDBService.publish(
            design.branch,
            trackNumbers = listOf(setup.trackNumber.id),
            locationTracks = trackIds,
            switches = listOf(setup.switch.id),
        )

        // The design context no longer shows the cancelled tracks, so they give the switch no track links
        api.switchCollectionInDesign(design.oid).get().vaihteet.single().also { s ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, s.kohteen_tila_suunnitelmassa)
            assertEquals(emptyList<ExtTestSwitchTrackLinkV1>(), s.raidelinkit)
        }
        api.switchInDesign(design.oid).get(setup.switch.oid).vaihde.also { s ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, s.kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design track number collection lists the design's own track numbers in every state with design content`() {
        val openTn = insertMainTrackNumberWithOid()
        val cancelledTn = insertMainTrackNumberWithOid()
        val completedTn = insertMainTrackNumberWithOid()
        val mainOnlyTn = insertMainTrackNumberWithOid()
        val oidOnlyTn = insertMainTrackNumberWithOid()
        testDBService.publish(
            trackNumbers = listOf(openTn, cancelledTn, completedTn, mainOnlyTn, oidOnlyTn).map { it.id }
        )

        val design = createDesign()
        editTrackNumberInDesign(design, openTn, "design open")
        editTrackNumberInDesign(design, cancelledTn, "design cancelled")
        editTrackNumberInDesign(design, completedTn, "design completed")
        testDBService.generateOid(oidOnlyTn.id, design.branch)
        testDBService.publish(design.branch, trackNumbers = listOf(openTn.id, cancelledTn.id, completedTn.id))
        cancelTrackNumberInDesign(design, cancelledTn)
        completeTrackNumberInDesign(design, completedTn)

        api.trackNumberCollectionInDesign(design.oid).get().ratanumerot.also { tns ->
            assertEquals(
                setOf(
                    "design open" to FI_DESIGN_ITEM_OPEN,
                    "design cancelled" to FI_DESIGN_ITEM_CANCELLED,
                    "design completed" to FI_DESIGN_ITEM_COMPLETED,
                ),
                tns.map { tn -> tn.kuvaus to tn.kohteen_tila_suunnitelmassa }.toSet(),
            )
        }
    }

    @Test
    fun `Design track number in layout state DELETED is listed and served`() {
        val tn = insertMainTrackNumberWithOid()
        testDBService.publish(trackNumbers = listOf(tn.id))

        val design = createDesign()
        testDBService.generateOid(tn.id, design.branch)
        design.draftContext.mutate(tn.id) { t -> t.copy(state = LayoutState.DELETED) }
        testDBService.publish(design.branch, trackNumbers = listOf(tn.id))

        api.trackNumberCollectionInDesign(design.oid).get().ratanumerot.single().also { t ->
            assertEquals(FI_DELETED, t.tila)
            assertEquals(FI_DESIGN_ITEM_OPEN, t.kohteen_tila_suunnitelmassa)
        }
        api.trackNumbersInDesign(design.oid).get(tn.oid).ratanumero.also { t -> assertEquals(FI_DELETED, t.tila) }
    }

    @Test
    fun `Completed design track number keeps its design content after later main edits`() {
        val tn = insertMainTrackNumberWithOid()
        testDBService.publish(trackNumbers = listOf(tn.id))

        val design = createDesign()
        editTrackNumberInDesign(design, tn, "design")
        val designPublication = testDBService.publish(design.branch, trackNumbers = listOf(tn.id))
        val completionPublication = completeTrackNumberInDesign(design, tn)

        mainDraftContext.mutate(tn.id) { t -> t.copy(description = TrackNumberDescription("main 2")) }
        testDBService.publish(trackNumbers = listOf(tn.id))

        val designApi = api.trackNumbersInDesign(design.oid)
        designApi.get(tn.oid).ratanumero.also { t ->
            assertEquals("design", t.kuvaus)
            assertEquals(FI_DESIGN_ITEM_COMPLETED, t.kohteen_tila_suunnitelmassa)
        }
        // The completion itself is a modification of the design's track number
        designApi.getModifiedBetween(tn.oid, designPublication.uuid, completionPublication.uuid).also { response ->
            assertEquals("design", response.ratanumero.kuvaus)
            assertEquals(FI_DESIGN_ITEM_COMPLETED, response.ratanumero.kohteen_tila_suunnitelmassa)
        }
        api.trackNumberCollectionInDesign(design.oid)
            .getModifiedBetween(designPublication.uuid, completionPublication.uuid)
            .also { response ->
                assertEquals(FI_DESIGN_ITEM_COMPLETED, response.ratanumerot.single().kohteen_tila_suunnitelmassa)
            }
        assertEquals("main 2", api.trackNumbers.get(tn.oid).ratanumero.kuvaus)
    }

    @Test
    fun `Cancelled design track number modification is reported with cancelled state`() {
        val tn = insertMainTrackNumberWithOid()
        testDBService.publish(trackNumbers = listOf(tn.id))

        val design = createDesign()
        editTrackNumberInDesign(design, tn, "design")
        val designPublication = testDBService.publish(design.branch, trackNumbers = listOf(tn.id))
        val cancellationPublication = cancelTrackNumberInDesign(design, tn)

        api.trackNumbersInDesign(design.oid)
            .getModifiedBetween(tn.oid, designPublication.uuid, cancellationPublication.uuid)
            .also { response ->
                assertEquals("design", response.ratanumero.kuvaus)
                assertEquals(FI_DESIGN_ITEM_CANCELLED, response.ratanumero.kohteen_tila_suunnitelmassa)
            }
        api.trackNumberCollectionInDesign(design.oid)
            .getModifiedBetween(designPublication.uuid, cancellationPublication.uuid)
            .also { response ->
                assertEquals(FI_DESIGN_ITEM_CANCELLED, response.ratanumerot.single().kohteen_tila_suunnitelmassa)
            }
    }

    @Test
    fun `Re-editing a completed design track number makes it open again with the new content`() {
        val tn = insertMainTrackNumberWithOid()
        testDBService.publish(trackNumbers = listOf(tn.id))

        val design = createDesign()
        editTrackNumberInDesign(design, tn, "design")
        testDBService.publish(design.branch, trackNumbers = listOf(tn.id))
        completeTrackNumberInDesign(design, tn)

        design.draftContext.mutate(tn.id) { t -> t.copy(description = TrackNumberDescription("re-edited")) }
        testDBService.publish(design.branch, trackNumbers = listOf(tn.id))

        api.trackNumbersInDesign(design.oid).get(tn.oid).ratanumero.also { t ->
            assertEquals("re-edited", t.kuvaus)
            assertEquals(FI_DESIGN_ITEM_OPEN, t.kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Cancelled design track number serves addresses and kms from its design row`() {
        initUser()
        val tn =
            mainDraftContext
                .saveWithOid(
                    trackNumber(testDBService.getUnusedTrackNumber()),
                    referenceLineGeometry(TRACK_NUMBER_SEGMENT),
                )
                .let { (id, oid) -> TrackNumberIds(id, oid) }
        val kmPostId = mainDraftContext.save(kmPost(tn.id, KmNumber(1), gkLocation = kmPostGkLocation(100.0, 0.0))).id
        testDBService.publish(trackNumbers = listOf(tn.id), kmPosts = listOf(kmPostId))

        val design = createDesign()
        testDBService.generateOid(tn.id, design.branch)
        val (designTn, _) = design.draftContext.fetchTrackNumberWithGeometry(tn.id)!!
        design.draftContext.save(designTn, referenceLineGeometry(segment(Point(0.0, 0.0), Point(150.0, 0.0))))
        testDBService.publish(design.branch, trackNumbers = listOf(tn.id))
        cancelTrackNumberInDesign(design, tn)

        val designEndAddress = "0001+0050.000"
        api.trackNumbersInDesign(design.oid).get(tn.oid).ratanumero.also { t ->
            assertEquals("0000+0000.000", t.alkusijainti?.rataosoite)
            assertEquals(designEndAddress, t.loppusijainti?.rataosoite)
            assertEquals(FI_DESIGN_ITEM_CANCELLED, t.kohteen_tila_suunnitelmassa)
        }
        api.trackNumberCollectionInDesign(design.oid).get().ratanumerot.single().also { t ->
            assertEquals(designEndAddress, t.loppusijainti?.rataosoite)
        }
        api.trackNumberGeometryInDesign(design.oid).get(tn.oid).also { response ->
            assertEquals(designEndAddress, response.osoitevali?.loppuosoite)
        }
        api.trackNumberKmsInDesign(design.oid).get(tn.oid).ratanumeron_ratakilometrit.ratakilometrit.also { kms ->
            assertEquals(listOf("0000", "0001"), kms.map { km -> km.km_tunnus })
            assertEquals(150.0, kms.last().loppupaalu.toDouble())
        }
        api.trackNumberKmsCollectionInDesign(design.oid).get().ratanumeroiden_ratakilometrit.single().also { tnKms ->
            assertEquals(150.0, tnKms.ratakilometrit.last().loppupaalu.toDouble())
        }

        assertEquals("0001+0100.000", api.trackNumbers.get(tn.oid).ratanumero.loppusijainti?.rataosoite)
        assertEquals(
            200.0,
            api.trackNumberKms.get(tn.oid).ratanumeron_ratakilometrit.ratakilometrit.last().loppupaalu.toDouble(),
        )
    }

    @Test
    fun `Cancelled design-created track number is listed without addresses or kms`() {
        initUser()
        val design = createDesign()
        val tnOid = insertCancelledDesignCreatedTrackNumber(design)

        api.trackNumberCollectionInDesign(design.oid).get().ratanumerot.single().also { t ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, t.kohteen_tila_suunnitelmassa)
            assertNull(t.alkusijainti?.rataosoite)
            assertNull(t.loppusijainti?.rataosoite)
        }
        api.trackNumbersInDesign(design.oid).get(tnOid).ratanumero.also { t ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, t.kohteen_tila_suunnitelmassa)
            assertNull(t.alkusijainti?.rataosoite)
        }
        api.trackNumberGeometryInDesign(design.oid).get(tnOid).also { response -> assertNull(response.osoitevali) }
        api.trackNumberKmsInDesign(design.oid).get(tnOid).also { response ->
            assertEquals(emptyList<ExtTestTrackKmV1>(), response.ratanumeron_ratakilometrit.ratakilometrit)
        }
        api.trackNumberKmsCollectionInDesign(design.oid).get().ratanumeroiden_ratakilometrit.single().also { tnKms ->
            assertEquals(tnOid.toString(), tnKms.ratanumero_oid)
            assertEquals(emptyList<ExtTestTrackKmV1>(), tnKms.ratakilometrit)
        }
    }

    @Test
    fun `Design item state is absent from main responses`() {
        val tn = insertMainTrackNumberWithOid()
        val track = insertMainTrack(tn.id, "main")
        val switch = insertMainSwitch("MAIN V001")
        testDBService.publish(
            trackNumbers = listOf(tn.id),
            locationTracks = listOf(track.id),
            switches = listOf(switch.id),
        )

        listOf(
                "/geoviite/paikannuspohja/v1/sijaintiraiteet/${track.oid}",
                "/geoviite/paikannuspohja/v1/sijaintiraiteet",
                "/geoviite/paikannuspohja/v1/vaihteet/${switch.oid}",
                "/geoviite/paikannuspohja/v1/vaihteet",
                "/geoviite/paikannuspohja/v1/ratanumerot/${tn.oid}",
                "/geoviite/paikannuspohja/v1/ratanumerot",
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

    private data class SwitchIds(val id: IntId<LayoutSwitch>, val oid: Oid<LayoutSwitch>)

    private fun insertMainSwitch(name: String): SwitchIds {
        initUser()
        return mainDraftContext.saveWithOid(switch(name = name)).let { (id, oid) -> SwitchIds(id, oid) }
    }

    private fun editSwitchInDesign(design: TestDesign, switch: SwitchIds, name: String) {
        testDBService.generateOid(switch.id, design.branch)
        design.draftContext.mutate(switch.id) { s -> s.copy(name = SwitchName(name)) }
    }

    private fun cancelSwitchInDesign(design: TestDesign, switch: SwitchIds): Publication {
        switchService.cancel(design.branch, switch.id)
        return testDBService.publish(design.branch, switches = listOf(switch.id))
    }

    /** As [completeInDesign], for a switch. */
    private fun completeSwitchInDesign(design: TestDesign, switch: SwitchIds): Publication {
        switchService.mergeToMainBranch(design.branch, switch.id)
        testDBService.publish(switches = listOf(switch.id))
        return latestDesignPublication()
    }

    private data class TrackNumberIds(val id: IntId<LayoutTrackNumber>, val oid: Oid<LayoutTrackNumber>)

    private fun insertMainTrackNumberWithOid(): TrackNumberIds {
        initUser()
        return mainDraftContext
            .saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()), referenceLineGeometry(TRACK_NUMBER_SEGMENT))
            .let { (id, oid) -> TrackNumberIds(id, oid) }
    }

    private fun editTrackNumberInDesign(design: TestDesign, tn: TrackNumberIds, description: String) {
        testDBService.generateOid(tn.id, design.branch)
        design.draftContext.mutate(tn.id) { t -> t.copy(description = TrackNumberDescription(description)) }
    }

    private fun cancelTrackNumberInDesign(design: TestDesign, tn: TrackNumberIds): Publication {
        trackNumberService.cancel(design.branch, tn.id)
        return testDBService.publish(design.branch, trackNumbers = listOf(tn.id))
    }

    /** As [completeInDesign], for a track number. */
    private fun completeTrackNumberInDesign(design: TestDesign, tn: TrackNumberIds): Publication {
        trackNumberService.mergeToMainBranch(design.branch, tn.id)
        testDBService.publish(trackNumbers = listOf(tn.id))
        return latestDesignPublication()
    }

    private fun insertCancelledDesignCreatedTrackNumber(design: TestDesign): Oid<LayoutTrackNumber> {
        val tnId =
            design.draftContext
                .save(trackNumber(testDBService.getUnusedTrackNumber()), referenceLineGeometry(TRACK_NUMBER_SEGMENT))
                .id
        val tnOid = testDBService.generateOid(tnId, design.branch)
        testDBService.publish(design.branch, trackNumbers = listOf(tnId))

        trackNumberService.cancel(design.branch, tnId)
        testDBService.publish(design.branch, trackNumbers = listOf(tnId))
        return tnOid
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
