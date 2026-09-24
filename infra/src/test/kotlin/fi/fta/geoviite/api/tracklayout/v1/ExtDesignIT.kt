package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
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
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

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
    fun `Design item state is suunnittelussa for OPEN track number in design`() {
        initUser()
        val (tnId, tnOid) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designOid = layoutDesignDao.fetch(designBranch.designId).externalId
        testDBService.generateOid(tnId, designBranch)
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        val designPublication = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        api.trackNumbersInDesign(designOid).getAtVersion(tnOid, designPublication.uuid).ratanumero.also { tn ->
            assertEquals(FI_DESIGN_ITEM_IN_PROGRESS, tn.kohteen_tila_suunnitelmassa)
        }
        api.trackNumberCollectionInDesign(designOid).getAtVersion(designPublication.uuid).ratanumerot.also { tns ->
            assertEquals(FI_DESIGN_ITEM_IN_PROGRESS, tns.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is peruttu for cancelled track number in design`() {
        initUser()
        val (tnId, tnOid) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designOid = layoutDesignDao.fetch(designBranch.designId).externalId
        testDBService.generateOid(tnId, designBranch)
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        trackNumberService.cancel(designBranch, tnId)
        val cancellationPublication = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        api.trackNumbersInDesign(designOid).getAtVersion(tnOid, cancellationPublication.uuid).ratanumero.also { tn ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, tn.kohteen_tila_suunnitelmassa)
        }
        api.trackNumberCollectionInDesign(designOid).getAtVersion(cancellationPublication.uuid).ratanumerot.also { tns
            ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, tns.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is valmis for completed track number after publish to main`() {
        initUser()
        val (tnId, tnOid) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designOid = layoutDesignDao.fetch(designBranch.designId).externalId
        testDBService.generateOid(tnId, designBranch)
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        trackNumberService.mergeToMainBranch(designBranch, tnId)
        val mainPublication = testDBService.publish(trackNumbers = listOf(tnId))

        api.trackNumbersInDesign(designOid).getAtVersion(tnOid, mainPublication.uuid).ratanumero.also { tn ->
            assertEquals(FI_DESIGN_ITEM_COMPLETED, tn.kohteen_tila_suunnitelmassa)
        }
        api.trackNumberCollectionInDesign(designOid).getAtVersion(mainPublication.uuid).ratanumerot.also { tns ->
            assertEquals(FI_DESIGN_ITEM_COMPLETED, tns.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is absent from main context track number responses`() {
        initUser()
        val (tnId, tnOid) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val publication = testDBService.publish(trackNumbers = listOf(tnId))

        api.trackNumbers.getAtVersion(tnOid, publication.uuid).ratanumero.also { tn ->
            assertEquals(null, tn.kohteen_tila_suunnitelmassa)
        }
        api.trackNumberCollection.getAtVersion(publication.uuid).ratanumerot.also { tns ->
            assertEquals(null, tns.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is suunnittelussa for OPEN switch in design`() {
        val (_, designOid, _, switchOid, designPublication) = setupSwitchInDesign()

        api.switchInDesign(designOid).getAtVersion(switchOid, designPublication.uuid).vaihde.also { sw ->
            assertEquals(FI_DESIGN_ITEM_IN_PROGRESS, sw.kohteen_tila_suunnitelmassa)
        }
        api.switchCollectionInDesign(designOid).getAtVersion(designPublication.uuid).vaihteet.also { switches ->
            assertEquals(FI_DESIGN_ITEM_IN_PROGRESS, switches.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is peruttu for cancelled switch in design`() {
        val (designBranch, designOid, switchId, switchOid, _) = setupSwitchInDesign()

        switchService.cancel(designBranch, switchId)
        val cancellationPublication = testDBService.publish(designBranch, switches = listOf(switchId))

        api.switchInDesign(designOid).getAtVersion(switchOid, cancellationPublication.uuid).vaihde.also { sw ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, sw.kohteen_tila_suunnitelmassa)
        }
        api.switchCollectionInDesign(designOid).getAtVersion(cancellationPublication.uuid).vaihteet.also { switches ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, switches.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is valmis for completed switch after publish to main`() {
        val (designBranch, designOid, switchId, switchOid, _) = setupSwitchInDesign()

        switchService.mergeToMainBranch(designBranch, switchId)
        val mainPublication = testDBService.publish(switches = listOf(switchId))

        api.switchInDesign(designOid).getAtVersion(switchOid, mainPublication.uuid).vaihde.also { sw ->
            assertEquals(FI_DESIGN_ITEM_COMPLETED, sw.kohteen_tila_suunnitelmassa)
        }
        api.switchCollectionInDesign(designOid).getAtVersion(mainPublication.uuid).vaihteet.also { switches ->
            assertEquals(FI_DESIGN_ITEM_COMPLETED, switches.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is absent from main context switch responses`() {
        initUser()
        val (switchId, switchOid) = mainDraftContext.saveWithOid(switch())
        val publication = testDBService.publish(switches = listOf(switchId))

        api.switch.getAtVersion(switchOid, publication.uuid).vaihde.also { sw ->
            assertEquals(null, sw.kohteen_tila_suunnitelmassa)
        }
        api.switchCollection.getAtVersion(publication.uuid).vaihteet.also { switches ->
            assertEquals(null, switches.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is suunnittelussa for OPEN location track in design`() {
        val (_, designOid, _, ltOid, designPublication) = setupLocationTrackInDesign()

        api.locationTracksInDesign(designOid).getAtVersion(ltOid, designPublication.uuid).sijaintiraide.also { lt ->
            assertEquals(FI_DESIGN_ITEM_IN_PROGRESS, lt.kohteen_tila_suunnitelmassa)
        }
        api.locationTrackCollectionInDesign(designOid).getAtVersion(designPublication.uuid).sijaintiraiteet.also { lts
            ->
            assertEquals(FI_DESIGN_ITEM_IN_PROGRESS, lts.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is peruttu for cancelled location track in design`() {
        val (designBranch, designOid, ltId, ltOid, _) = setupLocationTrackInDesign()

        locationTrackService.cancel(designBranch, ltId)
        val cancellationPublication = testDBService.publish(designBranch, locationTracks = listOf(ltId))

        api.locationTracksInDesign(designOid).getAtVersion(ltOid, cancellationPublication.uuid).sijaintiraide.also { lt
            ->
            assertEquals(FI_DESIGN_ITEM_CANCELLED, lt.kohteen_tila_suunnitelmassa)
        }
        api.locationTrackCollectionInDesign(designOid)
            .getAtVersion(cancellationPublication.uuid)
            .sijaintiraiteet
            .also { lts -> assertEquals(FI_DESIGN_ITEM_CANCELLED, lts.single().kohteen_tila_suunnitelmassa) }
    }

    @Test
    fun `Design item state is valmis for completed location track after publish to main`() {
        val (designBranch, designOid, ltId, ltOid, _) = setupLocationTrackInDesign()

        locationTrackService.mergeToMainBranch(designBranch, ltId)
        val mainPublication = testDBService.publish(locationTracks = listOf(ltId))

        api.locationTracksInDesign(designOid).getAtVersion(ltOid, mainPublication.uuid).sijaintiraide.also { lt ->
            assertEquals(FI_DESIGN_ITEM_COMPLETED, lt.kohteen_tila_suunnitelmassa)
        }
        api.locationTrackCollectionInDesign(designOid).getAtVersion(mainPublication.uuid).sijaintiraiteet.also { lts ->
            assertEquals(FI_DESIGN_ITEM_COMPLETED, lts.single().kohteen_tila_suunnitelmassa)
        }
    }

    @Test
    fun `Design item state is absent from main context location track responses`() {
        initUser()
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val (ltId, ltOid) =
            mainDraftContext.saveWithOid(
                locationTrack(tnId),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val publication = testDBService.publish(trackNumbers = listOf(tnId), locationTracks = listOf(ltId))

        api.locationTrackCollection.getAtVersion(publication.uuid).sijaintiraiteet.also { lts ->
            assertEquals(null, lts.single().kohteen_tila_suunnitelmassa)
        }
        api.locationTracks.getAtVersion(ltOid, publication.uuid).sijaintiraide.also { lt ->
            assertEquals(null, lt.kohteen_tila_suunnitelmassa)
        }
    }

    private data class SwitchSetup(
        val designBranch: DesignBranch,
        val designOid: Oid<LayoutDesign>,
        val switchId: IntId<LayoutSwitch>,
        val switchOid: Oid<LayoutSwitch>,
        val designPublication: Publication,
    )

    private fun setupSwitchInDesign(): SwitchSetup {
        initUser()
        val (switchId, switchOid) = mainDraftContext.saveWithOid(switch())
        testDBService.publish(switches = listOf(switchId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designOid = layoutDesignDao.fetch(designBranch.designId).externalId
        testDBService.generateOid(switchId, designBranch)
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(switchId) { sw -> sw.copy(trapPoint = true) }
        val designPublication = testDBService.publish(designBranch, switches = listOf(switchId))

        return SwitchSetup(designBranch, designOid, switchId, switchOid, designPublication)
    }

    private data class LocationTrackSetup(
        val designBranch: DesignBranch,
        val designOid: Oid<LayoutDesign>,
        val ltId: IntId<LocationTrack>,
        val ltOid: Oid<LocationTrack>,
        val designPublication: Publication,
    )

    private fun setupLocationTrackInDesign(): LocationTrackSetup {
        initUser()
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val (ltId, ltOid) =
            mainDraftContext.saveWithOid(
                locationTrack(tnId),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        testDBService.publish(trackNumbers = listOf(tnId), locationTracks = listOf(ltId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designOid = layoutDesignDao.fetch(designBranch.designId).externalId
        testDBService.generateOid(ltId, designBranch)
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(ltId) { lt -> lt.copy(type = LocationTrackType.CHORD) }
        val designPublication = testDBService.publish(designBranch, locationTracks = listOf(ltId))

        return LocationTrackSetup(designBranch, designOid, ltId, ltOid, designPublication)
    }

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
