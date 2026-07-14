package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.PublishedInDesign
import fi.fta.geoviite.infra.publication.publicationRequest
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtTrackLayoutVersionIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val layoutDesignDao: LayoutDesignDao,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Track layout versions should be returned correctly`() {
        api.trackLayoutVersionCollection.getWithEmptyBody(httpStatus = HttpStatus.NO_CONTENT)

        initUser()
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val publication1 = testDBService.publish(trackNumbers = listOf(tnId))

        assertMatches(publication1, api.trackLayoutVersionLatest.get())
        assertMatches(publication1, api.trackLayoutVersion.get(publication1.uuid))
        api.trackLayoutVersionCollection.get().let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication1.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication1), result.rataverkon_versiot)
        }

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        val publication2 = testDBService.publish(trackNumbers = listOf(tnId))

        assertMatches(publication2, api.trackLayoutVersionLatest.get())
        assertMatches(publication1, api.trackLayoutVersion.get(publication1.uuid))
        assertMatches(publication2, api.trackLayoutVersion.get(publication2.uuid))
        api.trackLayoutVersionCollection.get().let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication2.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication1, publication2), result.rataverkon_versiot)
        }

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0003.000")) }
        val publication3 = testDBService.publish(trackNumbers = listOf(tnId))

        assertMatches(publication3, api.trackLayoutVersionLatest.get())
        assertMatches(publication1, api.trackLayoutVersion.get(publication1.uuid))
        assertMatches(publication2, api.trackLayoutVersion.get(publication2.uuid))
        assertMatches(publication3, api.trackLayoutVersion.get(publication3.uuid))
        api.trackLayoutVersionCollection.get().let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication3.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication1, publication2, publication3), result.rataverkon_versiot)
        }
    }

    @Test
    fun `Track layout version modifications should be returned correctly`() {
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val publication1 = testDBService.publish(trackNumbers = listOf(tnId))

        api.trackLayoutVersionCollection.assertNoModificationSince(publication1.uuid)

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        val publication2 = testDBService.publish(trackNumbers = listOf(tnId))

        api.trackLayoutVersionCollection.getModifiedSince(publication1.uuid).let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication2.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication2), result.rataverkon_versiot)
        }
        api.trackLayoutVersionCollection.getModifiedBetween(publication1.uuid, publication2.uuid).let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication2.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication2), result.rataverkon_versiot)
        }

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0003.000")) }
        val publication3 = testDBService.publish(trackNumbers = listOf(tnId))

        api.trackLayoutVersionCollection.getModifiedSince(publication1.uuid).let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication3.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication2, publication3), result.rataverkon_versiot)
        }
        api.trackLayoutVersionCollection.getModifiedBetween(publication1.uuid, publication3.uuid).let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication3.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication2, publication3), result.rataverkon_versiot)
        }
    }

    @Test
    fun `Invalid publication uuid results in a 400 Bad Request`() {
        api.trackLayoutVersion.getWithExpectedError(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            httpStatus = HttpStatus.BAD_REQUEST,
        )
    }

    @Test
    fun `Design publications should not be returned`() {
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val publication1 = testDBService.publish(trackNumbers = listOf(tnId))

        api.trackLayoutVersionCollection.assertNoModificationSince(publication1.uuid)

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designCtx = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designCtx.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        val designPublication = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        api.trackLayoutVersionCollection.assertNoModificationSince(publication1.uuid)
        api.trackLayoutVersion.getWithExpectedError(
            designPublication.uuid.toString(),
            httpStatus = HttpStatus.NOT_FOUND,
        )
        assertEquals(publication1.uuid.toString(), api.trackLayoutVersionLatest.get().rataverkon_versio)
    }

    @Test
    fun `Design publications should be returned as layout versions when designs are included`() {
        initUser()
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val mainPublication1 = testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designOid = layoutDesignDao.fetch(designBranch.designId).externalId
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        val designPublication1 = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0003.000")) }
        val mainPublication2 = testDBService.publish(trackNumbers = listOf(tnId))

        // A design publication is a layout version, but only visible when designs are included
        api.trackLayoutVersion.getWithExpectedError(
            designPublication1.uuid.toString(),
            httpStatus = HttpStatus.NOT_FOUND,
        )
        api.trackLayoutVersion.get(designPublication1.uuid, INCLUDE_DESIGNS to "true").let { result ->
            assertMatches(designPublication1, result)
            assertEquals(designOid.toString(), result.suunnitelma_oid?.toString())
        }
        assertMatches(mainPublication1, api.trackLayoutVersion.get(mainPublication1.uuid, INCLUDE_DESIGNS to "true"))

        // All publications form an ordered layout version listing, design versions carrying their design oid
        api.trackLayoutVersionCollection.get().let { result ->
            assertMatches(listOf(mainPublication1, mainPublication2), result.rataverkon_versiot)
        }
        api.trackLayoutVersionCollection.get(INCLUDE_DESIGNS to "true").let { result ->
            assertEquals(mainPublication1.uuid.toString(), result.alkuversio)
            assertEquals(mainPublication2.uuid.toString(), result.loppuversio)
            assertMatches(listOf(mainPublication1, designPublication1, mainPublication2), result.rataverkon_versiot)
        }

        // The latest layout version follows design inclusion
        assertMatches(mainPublication2, api.trackLayoutVersionLatest.get())
        assertMatches(mainPublication2, api.trackLayoutVersionLatest.get(INCLUDE_DESIGNS to "true"))

        initUser()
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0004.000")) }
        val designPublication2 = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        assertMatches(mainPublication2, api.trackLayoutVersionLatest.get())
        assertMatches(designPublication2, api.trackLayoutVersionLatest.get(INCLUDE_DESIGNS to "true"))
    }

    @Test
    fun `Design publication layout versions can be used as modification comparison bounds`() {
        initUser()
        val (tnId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber(), startAddress = TrackMeter("0001+0001.000")),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val mainPublication1 = testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0002.000")) }
        val designPublication1 = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0003.000")) }
        val mainPublication2 = testDBService.publish(trackNumbers = listOf(tnId))

        initUser()
        designContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0004.000")) }
        val designPublication2 = testDBService.publish(designBranch, trackNumbers = listOf(tnId))

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(startAddress = TrackMeter("0001+0005.000")) }
        val mainPublication3 = testDBService.publish(trackNumbers = listOf(tnId))

        // A design layout version as the start bound returns the main versions published after it
        api.trackLayoutVersionCollection.getModifiedSince(designPublication1.uuid).let { result ->
            assertEquals(designPublication1.uuid.toString(), result.alkuversio)
            assertEquals(mainPublication3.uuid.toString(), result.loppuversio)
            assertMatches(listOf(mainPublication2, mainPublication3), result.rataverkon_versiot)
        }
        api.trackLayoutVersionCollection.getModifiedBetween(designPublication1.uuid, mainPublication2.uuid).let { result
            ->
            assertEquals(designPublication1.uuid.toString(), result.alkuversio)
            assertEquals(mainPublication2.uuid.toString(), result.loppuversio)
            assertMatches(listOf(mainPublication2), result.rataverkon_versiot)
        }

        // Design layout versions as bounds only delimit the listing: the listed versions are main versions
        api.trackLayoutVersionCollection.getModifiedBetween(designPublication1.uuid, designPublication2.uuid).let {
            result ->
            assertEquals(designPublication1.uuid.toString(), result.alkuversio)
            assertEquals(designPublication2.uuid.toString(), result.loppuversio)
            assertMatches(listOf(mainPublication2), result.rataverkon_versiot)
        }
        api.trackLayoutVersionCollection.getModifiedBetween(mainPublication1.uuid, designPublication2.uuid).let { result
            ->
            assertMatches(listOf(mainPublication2), result.rataverkon_versiot)
        }

        // No main versions between a main version and the design version right after it
        api.trackLayoutVersionCollection.assertNoModificationBetween(mainPublication1.uuid, designPublication1.uuid)
    }

    @Test
    fun `Main publication with address changes should create an automatic design publication`() {
        initUser()
        val tnId =
            mainOfficialContext
                .save(
                    trackNumber(testDBService.getUnusedTrackNumber()),
                    referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                )
                .id
        testDBService.generateOid(tnId, LayoutBranch.main)
        val kmPostId =
            mainOfficialContext.save(kmPost(tnId, gkLocation = kmPostGkLocation(Point(5.0, 0.0)), km = KmNumber(1))).id

        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designDraftContext.copyFrom(mainOfficialContext.fetchVersion(tnId)!!)
        val designPublication = testDBService.publish(designBranch, trackNumbers = listOf(tnId))
        testDBService.generateOid(tnId, designBranch)

        initUser()
        mainDraftContext.mutate(kmPostId) { post -> post.copy(kmNumber = KmNumber(2)) }
        val mainPublicationId =
            requireNotNull(
                publicationService
                    .publishManualPublication(LayoutBranch.main, publicationRequest(kmPosts = listOf(kmPostId)))
                    .publicationId
            )
        val mainPublication = publicationDao.getPublication(mainPublicationId)

        // The main publication inherited an address change into the design, automatically creating a publication
        val inheritedPublication = publicationDao.list(LayoutBranchType.DESIGN).first()
        assertEquals(PublicationCause.CALCULATED_CHANGE, inheritedPublication.cause)
        assertEquals(mainPublicationId, (inheritedPublication.layoutBranch as PublishedInDesign).parentPublicationId)

        // The automatic design publication is a layout version like any other
        api.trackLayoutVersionCollection.get().let { result ->
            assertMatches(listOf(mainPublication), result.rataverkon_versiot)
        }
        api.trackLayoutVersionCollection.get(INCLUDE_DESIGNS to "true").let { result ->
            assertMatches(listOf(designPublication, mainPublication, inheritedPublication), result.rataverkon_versiot)
        }
        assertMatches(mainPublication, api.trackLayoutVersionLatest.get())
        assertMatches(inheritedPublication, api.trackLayoutVersionLatest.get(INCLUDE_DESIGNS to "true"))
        assertMatches(
            inheritedPublication,
            api.trackLayoutVersion.get(inheritedPublication.uuid, INCLUDE_DESIGNS to "true"),
        )
    }

    private fun assertMatches(publications: List<Publication>, results: List<ExtTestTrackLayoutVersionV1>) {
        assertEquals(publications.map { p -> p.uuid.toString() }, results.map { r -> r.rataverkon_versio })
        publications.zip(results).forEach { (pub, res) -> assertMatches(pub, res) }
    }

    private fun assertMatches(publication: Publication, result: ExtTestTrackLayoutVersionV1) {
        assertEquals(publication.uuid.toString(), result.rataverkon_versio)
        assertEquals(publication.publicationTime.toString(), result.aikaleima)
        assertEquals(publication.message.toString(), result.kuvaus)
        val designOid = publication.layoutBranch.branch.designId?.let { id -> layoutDesignDao.fetch(id).externalId }
        assertEquals(designOid?.toString(), result.suunnitelma_oid?.toString())
    }
}
