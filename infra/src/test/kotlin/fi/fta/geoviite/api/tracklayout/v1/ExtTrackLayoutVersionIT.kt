package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
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
constructor(mockMvc: MockMvc, private val extTestDataService: ExtApiTestDataServiceV1) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Track layout versions should be returned correctly`() {
        api.trackLayoutVersionCollection.getWithEmptyBody(httpStatus = HttpStatus.NO_CONTENT)

        initUser()
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val rlGeom = referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter("0001+0001.000")), rlGeom).id
        val publication1 = extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))

        assertMatches(publication1, api.trackLayoutVersionLatest.get())
        assertMatches(publication1, api.trackLayoutVersion.get(publication1.uuid))
        api.trackLayoutVersionCollection.get().let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication1.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication1), result.rataverkon_versiot)
        }

        initUser()
        mainDraftContext.mutate(rlId) { rl -> rl.copy(startAddress = TrackMeter("0001+0002.000")) }
        val publication2 = extTestDataService.publishInMain(referenceLines = listOf(rlId))

        assertMatches(publication2, api.trackLayoutVersionLatest.get())
        assertMatches(publication1, api.trackLayoutVersion.get(publication1.uuid))
        assertMatches(publication2, api.trackLayoutVersion.get(publication2.uuid))
        api.trackLayoutVersionCollection.get().let { result ->
            assertEquals(publication1.uuid.toString(), result.alkuversio)
            assertEquals(publication2.uuid.toString(), result.loppuversio)
            assertMatches(listOf(publication1, publication2), result.rataverkon_versiot)
        }

        initUser()
        mainDraftContext.mutate(rlId) { rl -> rl.copy(startAddress = TrackMeter("0001+0003.000")) }
        val publication3 = extTestDataService.publishInMain(referenceLines = listOf(rlId))

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
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val rlGeom = referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter("0001+0001.000")), rlGeom).id
        val publication1 = extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))

        api.trackLayoutVersionCollection.assertNoModificationSince(publication1.uuid)

        initUser()
        mainDraftContext.mutate(rlId) { rl -> rl.copy(startAddress = TrackMeter("0001+0002.000")) }
        val publication2 = extTestDataService.publishInMain(referenceLines = listOf(rlId))

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
        mainDraftContext.mutate(rlId) { rl -> rl.copy(startAddress = TrackMeter("0001+0003.000")) }
        val publication3 = extTestDataService.publishInMain(referenceLines = listOf(rlId))

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
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val rlGeom = referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter("0001+0001.000")), rlGeom).id
        val publication1 = extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))

        api.trackLayoutVersionCollection.assertNoModificationSince(publication1.uuid)

        initUser()
        val designBranch = testDBService.createDesignBranch()
        val designCtx = testDBService.testContext(designBranch, PublicationState.DRAFT)
        designCtx.mutate(rlId) { rl -> rl.copy(startAddress = TrackMeter("0001+0002.000")) }
        val designPublication = extTestDataService.publishInBranch(designBranch, referenceLines = listOf(rlId))

        api.trackLayoutVersionCollection.assertNoModificationSince(publication1.uuid)
        api.trackLayoutVersion.getWithExpectedError(
            designPublication.uuid.toString(),
            httpStatus = HttpStatus.NOT_FOUND,
        )
        assertEquals(publication1.uuid.toString(), api.trackLayoutVersionLatest.get().rataverkon_versio)
    }

    private fun assertMatches(publications: List<Publication>, results: List<ExtTestTrackLayoutVersionV1>) {
        assertEquals(publications.map { p -> p.uuid.toString() }, results.map { r -> r.rataverkon_versio })
        publications.zip(results).forEach { (pub, res) -> assertMatches(pub, res) }
    }

    private fun assertMatches(publication: Publication, result: ExtTestTrackLayoutVersionV1) {
        assertEquals(publication.uuid.toString(), result.rataverkon_versio)
        assertEquals(publication.publicationTime.toString(), result.aikaleima)
        assertEquals(publication.message.toString(), result.kuvaus)
    }
}
