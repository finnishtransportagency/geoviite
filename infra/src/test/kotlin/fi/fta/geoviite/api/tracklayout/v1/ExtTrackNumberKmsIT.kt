package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import kotlin.math.abs

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtTrackNumberKmsIT
@Autowired
constructor(mockMvc: MockMvc, private val extTestDataService: ExtApiTestDataServiceV1) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Track number kms API should return correct kms`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val tnId = mainDraftContext.createLayoutTrackNumber(trackNumber).id
        val tnOid = mainDraftContext.generateOid(tnId)
        val rlGeom = referenceLineGeometry(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter(10, 100)), rlGeom).id
        val kmp13Id =
            mainDraftContext.save(kmPost(tnId, KmNumber(13), gkLocation = kmPostGkLocation(300.0, 2.0, true))).id
        val kmp14Id =
            mainDraftContext.save(kmPost(tnId, KmNumber(14), gkLocation = kmPostGkLocation(400.0, -1.0, false))).id
        val deletedKmp15Id =
            mainDraftContext
                .save(
                    kmPost(tnId, KmNumber(15), gkLocation = kmPostGkLocation(500.0, 0.0), state = LayoutState.DELETED)
                )
                .id
        val kmp16Id =
            mainDraftContext.save(kmPost(tnId, KmNumber(16), gkLocation = kmPostGkLocation(600.0, 0.0, true))).id

        val publication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp13Id, kmp14Id, deletedKmp15Id, kmp16Id),
            )
        val response = api.trackNumberKms.get(tnOid)
        assertEquals(publication.uuid.toString(), response.rataverkon_versio)
        assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)
        assertEquals(trackNumber.toString(), response.ratanumeron_ratakilometrit.ratanumero)
        assertEquals(tnOid.toString(), response.ratanumeron_ratakilometrit.ratanumero_oid)
        assertKmsMatch(
            response.ratanumeron_ratakilometrit.ratakilometrit,
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.TRACK_NUMBER_START,
                kmNumber = KmNumber(10),
                // The first km "length" is calculated from the KMs 0m location
                // In this case, that's 100m before the start of the reference line + 300m up until the first post
                startM = BigDecimal("-100.000"),
                endM = BigDecimal("300.000"),
                officialLocation = null,
                location = ExtCoordinateV1(0.0, 0.0),
            ),
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.KM_POST,
                kmNumber = KmNumber(13),
                startM = BigDecimal("300.000"),
                endM = BigDecimal("400.000"),
                officialLocation =
                    ExtKmPostOfficialLocationV1(transformFromLayoutToGKCoordinate(Point(300.0, 2.0)), true),
                location = ExtCoordinateV1(300.0, 2.0),
            ),
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.KM_POST,
                kmNumber = KmNumber(14),
                startM = BigDecimal("400.000"),
                endM = BigDecimal("600.000"), // Since km 15 is deleted, the next one is km 16
                officialLocation =
                    ExtKmPostOfficialLocationV1(transformFromLayoutToGKCoordinate(Point(400.0, -1.0)), false),
                location = ExtCoordinateV1(400.0, -1.0),
            ),
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.KM_POST,
                kmNumber = KmNumber(16),
                startM = BigDecimal("600.000"),
                endM = BigDecimal("1000.000"), // Reference line end
                officialLocation =
                    ExtKmPostOfficialLocationV1(transformFromLayoutToGKCoordinate(Point(600.0, 0.0)), true),
                location = ExtCoordinateV1(600.0, 0.0),
            ),
        )

        // Ensure that the collection also includes the exact same response
        val kmsInCollection =
            api.trackNumberKmsCollection.get().ratanumeroiden_ratakilometrit.find {
                it.ratanumero == trackNumber.toString()
            }
        assertEquals(response.ratanumeron_ratakilometrit, kmsInCollection)
    }

    @Test
    fun `Track number kms API respects the coordinate system argument`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val tnId = mainDraftContext.createLayoutTrackNumber(trackNumber).id
        val tnOid = mainDraftContext.generateOid(tnId)
        val rlGeom = referenceLineGeometry(segment(Point(1000.0, 1000.0), Point(2000.0, 1000.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter.ZERO), rlGeom).id
        val kmp1Id = mainDraftContext.save(kmPost(tnId, KmNumber(1), gkLocation = kmPostGkLocation(1500.0, 1002.0))).id

        val publication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp1Id),
            )
        val response = api.trackNumberKms.get(tnOid, COORDINATE_SYSTEM to "EPSG:4326")
        assertEquals(publication.uuid.toString(), response.rataverkon_versio)
        assertEquals(Srid(4326).toString(), response.koordinaatisto)
        assertEquals(trackNumber.toString(), response.ratanumeron_ratakilometrit.ratanumero)
        assertEquals(tnOid.toString(), response.ratanumeron_ratakilometrit.ratanumero_oid)
        assertKmsMatch(
            response.ratanumeron_ratakilometrit.ratakilometrit,
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.TRACK_NUMBER_START,
                kmNumber = KmNumber(0),
                startM = BigDecimal("0.000"),
                endM = BigDecimal("500.000"),
                officialLocation = null,
                // Start location in EPSG:4326 == WGS84, converted using https://epsg.io/transform
                location = ExtCoordinateV1(22.5202151, 0.0090195),
            ),
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.KM_POST,
                kmNumber = KmNumber(1),
                startM = BigDecimal("500.000"),
                endM = BigDecimal("1000.000"),
                officialLocation =
                    ExtKmPostOfficialLocationV1(transformFromLayoutToGKCoordinate(Point(1500.0, 1002.0)), false),
                // KM Post location in EPSG:4326 == WGS84, converted using https://epsg.io/transform
                location = ExtCoordinateV1(22.5246947, 0.0090375),
            ),
        )

        // Ensure that the collection also includes the exact same response
        val kmsInCollection =
            api.trackNumberKmsCollection.get(COORDINATE_SYSTEM to "EPSG:4326").ratanumeroiden_ratakilometrit.find {
                it.ratanumero == trackNumber.toString()
            }
        assertEquals(response.ratanumeron_ratakilometrit, kmsInCollection)
    }

    @Test
    fun `A deleted track number has no kms`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val tnOid = mainDraftContext.generateOid(tnId)
        val rlGeom = referenceLineGeometry(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter.ZERO), rlGeom).id
        val kmp1Id = mainDraftContext.save(kmPost(tnId, KmNumber(1), gkLocation = kmPostGkLocation(500.0, 0.0))).id
        val basePublication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp1Id),
            )

        api.trackNumberKms.get(tnOid).also { response ->
            assertEquals(basePublication.uuid.toString(), response.rataverkon_versio)
            assertEquals(tnOid.toString(), response.ratanumeron_ratakilometrit.ratanumero_oid)
            assertEquals(2, response.ratanumeron_ratakilometrit.ratakilometrit.size)
            assertEquals(
                response.ratanumeron_ratakilometrit,
                api.trackNumberKmsCollection.get().ratanumeroiden_ratakilometrit.find {
                    it.ratanumero_oid == tnOid.toString()
                },
            )
        }

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(state = LayoutState.DELETED) }
        val deletePublication = extTestDataService.publishInMain(trackNumbers = listOf(tnId))
        api.trackNumberKms.getWithEmptyBody(tnOid, httpStatus = HttpStatus.NO_CONTENT)
        api.trackNumberKms.assertDoesntExist(tnOid)
        assertNull(
            api.trackNumberKmsCollection.get().ratanumeroiden_ratakilometrit.find {
                it.ratanumero_oid == tnOid.toString()
            }
        )

        // Ensure history-fetches by specific version return the same results
        api.trackNumberKms.getAtVersion(tnOid, basePublication.uuid).also { response ->
            assertEquals(basePublication.uuid.toString(), response.rataverkon_versio)
            assertEquals(tnOid.toString(), response.ratanumeron_ratakilometrit.ratanumero_oid)
            assertEquals(2, response.ratanumeron_ratakilometrit.ratakilometrit.size)
            assertEquals(
                response.ratanumeron_ratakilometrit,
                api.trackNumberKmsCollection.getAtVersion(basePublication.uuid).ratanumeroiden_ratakilometrit.find {
                    it.ratanumero_oid == tnOid.toString()
                },
            )
        }
        api.trackNumberKms.assertDoesntExistAtVersion(tnOid, deletePublication.uuid)
        assertNull(
            api.trackNumberKmsCollection.getAtVersion(deletePublication.uuid).ratanumeroiden_ratakilometrit.find {
                it.ratanumero_oid == tnOid.toString()
            }
        )
    }

    fun assertKmsMatch(actual: List<ExtTestTrackKmV1>, vararg expected: ExtTrackKmV1) {
        expected.forEachIndexed { index, expectedKm ->
            val actualKm = actual[index]
            assertNotNull(actual, "A km is missing in the response: index=$index expected=$expectedKm")
            assertEquals(expectedKm.type.value, actualKm.tyyppi)
            assertEquals(expectedKm.kmNumber.toString(), actualKm.km_tunnus)
            assertEquals(expectedKm.startM.toString(), actualKm.alkupaalu)
            assertEquals(expectedKm.endM.toString(), actualKm.loppupaalu)
            assertEquals(expectedKm.endM, expectedKm.startM + expectedKm.kmLength)
            assertEquals(expectedKm.officialLocation != null, actualKm.virallinen_sijainti != null)
            if (expectedKm.officialLocation != null && actualKm.virallinen_sijainti != null) {
                assertTrue(
                    isSame(expectedKm.officialLocation, actualKm.virallinen_sijainti),
                    "KMs do not match (official location): expected=$expectedKm actualKm=$actualKm",
                )
            }
            assertTrue(
                isSame(expectedKm.location, actualKm.sijainti),
                "KMs do not match (layout location): expected=$expectedKm actualKm=$actualKm",
            )
        }
        assertEquals(
            expected.size,
            actual.size,
            "Result had extra kms (after the expected ones): expected=${expected.size} actual=${actual.size} extra=${actual.subList(expected.size, actual.size)}",
        )
    }

    fun isSame(expected: ExtKmPostOfficialLocationV1, actual: ExtTestKmPostOfficialLocationV1): Boolean =
        // X & Y checked with a tolerance
        abs(expected.x - actual.x) < LAYOUT_M_DELTA &&
            abs(expected.y - actual.y) < LAYOUT_M_DELTA &&
            // Confirm other fields
            expected.srid.toString() == actual.koordinaatisto &&
            expected.confirmed.value == actual.vahvistettu

    fun isSame(expected: ExtCoordinateV1, actual: ExtTestCoordinateV1): Boolean =
        abs(expected.x - actual.x) < LAYOUT_M_DELTA && abs(expected.y - actual.y) < LAYOUT_M_DELTA
}
