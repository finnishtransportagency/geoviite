package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
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
        val tnOid = testDBService.generateTrackNumberOid(tnId, LayoutBranch.main)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter(10, 100)), rlGeom).id
        val kmp13Id = mainDraftContext.save(kmPost(tnId, KmNumber(13), gkLocation = kmPostGkLocation(300.0, 2.0))).id
        val kmp14Id = mainDraftContext.save(kmPost(tnId, KmNumber(14), gkLocation = kmPostGkLocation(400.0, -1.0))).id
        val deletedKmp15Id =
            mainDraftContext
                .save(
                    kmPost(tnId, KmNumber(15), gkLocation = kmPostGkLocation(500.0, 0.0), state = LayoutState.DELETED)
                )
                .id
        val kmp16Id = mainDraftContext.save(kmPost(tnId, KmNumber(16), gkLocation = kmPostGkLocation(600.0, 0.0))).id

        val publication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp13Id, kmp14Id, deletedKmp15Id, kmp16Id),
            )
        val response = api.trackNumberKms.get(tnOid)
        assertEquals(publication.uuid, response.trackLayoutVersion)
        assertEquals(LAYOUT_SRID, response.coordinateSystem)
        assertEquals(trackNumber, response.trackNumberKms.trackNumber)
        assertEquals(tnOid, response.trackNumberKms.trackNumberOid)
        assertKmsMatch(
            response.trackNumberKms.trackKms,
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
                officialLocation = ExtSridCoordinateV1(transformFromLayoutToGKCoordinate(Point(300.0, 2.0))),
                location = ExtCoordinateV1(300.0, 2.0),
            ),
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.KM_POST,
                kmNumber = KmNumber(14),
                startM = BigDecimal("400.000"),
                endM = BigDecimal("600.000"), // Since km 15 is deleted, the next one is km 16
                officialLocation = ExtSridCoordinateV1(transformFromLayoutToGKCoordinate(Point(400.0, -1.0))),
                location = ExtCoordinateV1(400.0, -1.0),
            ),
            ExtTrackKmV1(
                type = ExtTrackKmTypeV1.KM_POST,
                kmNumber = KmNumber(16),
                startM = BigDecimal("600.000"),
                endM = BigDecimal("1000.000"), // Reference line end
                officialLocation = ExtSridCoordinateV1(transformFromLayoutToGKCoordinate(Point(600.0, 0.0))),
                location = ExtCoordinateV1(600.0, 0.0),
            ),
        )

        // Ensure that the collection also includes the exact same response
        val kmsInCollection = api.trackNumberKmsCollection.get().trackNumberKms.find { it.trackNumber == trackNumber }
        assertEquals(response.trackNumberKms, kmsInCollection)
    }

    @Test
    fun `Track number kms API respects the coordinate system argument`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val tnId = mainDraftContext.createLayoutTrackNumber(trackNumber).id
        val tnOid = testDBService.generateTrackNumberOid(tnId, LayoutBranch.main)
        val rlGeom = alignment(segment(Point(1000.0, 1000.0), Point(2000.0, 1000.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter.ZERO), rlGeom).id
        val kmp1Id = mainDraftContext.save(kmPost(tnId, KmNumber(1), gkLocation = kmPostGkLocation(1500.0, 1002.0))).id

        val publication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp1Id),
            )
        val response = api.trackNumberKms.get(tnOid, COORDINATE_SYSTEM to "EPSG:4326")
        assertEquals(publication.uuid, response.trackLayoutVersion)
        assertEquals(Srid(4326), response.coordinateSystem)
        assertEquals(trackNumber, response.trackNumberKms.trackNumber)
        assertEquals(tnOid, response.trackNumberKms.trackNumberOid)
        assertKmsMatch(
            response.trackNumberKms.trackKms,
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
                officialLocation = ExtSridCoordinateV1(transformFromLayoutToGKCoordinate(Point(1500.0, 1002.0))),
                // KM Post location in EPSG:4326 == WGS84, converted using https://epsg.io/transform
                location = ExtCoordinateV1(22.5246947, 0.0090375),
            ),
        )

        // Ensure that the collection also includes the exact same response
        val kmsInCollection =
            api.trackNumberKmsCollection.get(COORDINATE_SYSTEM to "EPSG:4326").trackNumberKms.find {
                it.trackNumber == trackNumber
            }
        assertEquals(response.trackNumberKms, kmsInCollection)
    }

    @Test
    fun `A deleted track number has no kms`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val tnOid = testDBService.generateTrackNumberOid(tnId, LayoutBranch.main)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter.ZERO), rlGeom).id
        val kmp1Id = mainDraftContext.save(kmPost(tnId, KmNumber(1), gkLocation = kmPostGkLocation(500.0, 0.0))).id
        val basePublication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp1Id),
            )

        api.trackNumberKms.get(tnOid).also { response ->
            assertEquals(basePublication.uuid, response.trackLayoutVersion)
            assertEquals(tnOid, response.trackNumberKms.trackNumberOid)
            assertEquals(2, response.trackNumberKms.trackKms.size)
            assertEquals(
                response.trackNumberKms,
                api.trackNumberKmsCollection.get().trackNumberKms.find { it.trackNumberOid == tnOid },
            )
        }

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(state = LayoutState.DELETED) }
        val deletePublication = extTestDataService.publishInMain(trackNumbers = listOf(tnId))
        api.trackNumberKms.getWithEmptyBody(tnOid, httpStatus = HttpStatus.NO_CONTENT)
        api.trackNumberKms.assertDoesntExist(tnOid)
        assertNull(api.trackNumberKmsCollection.get().trackNumberKms.find { it.trackNumberOid == tnOid })

        // Ensure history-fetches by specific version return the same results
        api.trackNumberKms.getAtVersion(tnOid, basePublication.uuid).also { response ->
            assertEquals(basePublication.uuid, response.trackLayoutVersion)
            assertEquals(tnOid, response.trackNumberKms.trackNumberOid)
            assertEquals(2, response.trackNumberKms.trackKms.size)
            assertEquals(
                response.trackNumberKms,
                api.trackNumberKmsCollection.getAtVersion(basePublication.uuid).trackNumberKms.find {
                    it.trackNumberOid == tnOid
                },
            )
        }
        api.trackNumberKms.assertDoesntExistAtVersion(tnOid, deletePublication.uuid)
        assertNull(
            api.trackNumberKmsCollection.getAtVersion(deletePublication.uuid).trackNumberKms.find {
                it.trackNumberOid == tnOid
            }
        )
    }

    fun assertKmsMatch(actual: List<ExtTrackKmV1>, vararg expected: ExtTrackKmV1) {
        expected.forEachIndexed { index, expectedKm ->
            val actualKm = actual[index]
            assertNotNull(actual, "A km is missing in the response: index=$index expected=$expectedKm")
            assertEquals(expectedKm.type, actualKm.type)
            assertEquals(expectedKm.kmNumber, actualKm.kmNumber)
            assertEquals(expectedKm.startM, actualKm.startM)
            assertEquals(expectedKm.endM, actualKm.endM)
            assertEquals(expectedKm.endM, expectedKm.startM + expectedKm.kmLength)
            assertEquals(expectedKm.officialLocation != null, actualKm.officialLocation != null)
            if (expectedKm.officialLocation != null && actualKm.officialLocation != null) {
                assertLocationsMatch(expectedKm.officialLocation, actualKm.officialLocation)
            }
            assertLocationsMatch(expectedKm.location, actualKm.location)
        }
        assertEquals(
            expected.size,
            actual.size,
            "Result had extra kms (after the expected ones): expected=${expected.size} actual=${actual.size} extra=${actual.subList(expected.size, actual.size)}",
        )
    }

    fun assertLocationsMatch(expected: ExtSridCoordinateV1, actual: ExtSridCoordinateV1) {
        assertEquals(expected.srid, actual.srid)
        assertEquals(expected.x, actual.x, LAYOUT_M_DELTA)
        assertEquals(expected.y, actual.y, LAYOUT_M_DELTA)
    }

    fun assertLocationsMatch(expected: ExtCoordinateV1, actual: ExtCoordinateV1) {
        assertEquals(expected.x, actual.x, LAYOUT_M_DELTA)
        assertEquals(expected.y, actual.y, LAYOUT_M_DELTA)
    }
}
