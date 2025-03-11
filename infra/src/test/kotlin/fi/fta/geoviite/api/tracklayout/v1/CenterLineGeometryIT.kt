package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.trackNumber
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

const val CENTER_LINE_GEOMETRY_URL = "/geoviite/paikannuspohja/v1/sijaintiraiteet"

private fun url(oid: Oid<LocationTrack>) = "$CENTER_LINE_GEOMETRY_URL/${oid}"

private fun url(oid: String) = "$CENTER_LINE_GEOMETRY_URL/${oid}"

// data class GeometryResponse(val virheet: List<ResponseError>)

private abstract class Response

// Purposefully different data structure for testing
private data class GeometryResponse(
    @JsonProperty("ratanumero") val trackNumberName: String,
    @JsonProperty("ratanumero_oid") val trackNumberOid: String,
    @JsonProperty("oid") val locationTrackOid: String,
    @JsonProperty("sijaintiraidetunnus") val locationTrackName: String,
    @JsonProperty("tyyppi") val locationTrackType: String,
    @JsonProperty("tila") val locationTrackState: String,
    @JsonProperty("kuvaus") val locationTrackDescription: String,
    @JsonProperty("omistaja") val locationTrackOwner: String,
    @JsonProperty("alkusijainti") val locationTrackStart: ResponseGeometryPoint,
    @JsonProperty("loppusijainti") val locationTrackEnd: ResponseGeometryPoint,
    @JsonProperty("koordinaatisto") val coordinateSystem: String,
    @JsonProperty("osoitepistevali") val addressPointIntervalMeters: String,
    @JsonProperty("muuttuneet_kilometrit") val geometry: Map<String, List<ResponseGeometryPoint>>,
)

private data class ResponseGeometryPoint(
    @JsonProperty("x") val x: Double,
    @JsonProperty("y") val y: Double,
    @JsonProperty("ratakilometri") val kmNumber: String,
    @JsonProperty("ratametri") val trackMeter: Int,
    @JsonProperty("ratametri_desimaalit") val trackMeterDecimals: Int,
)

private data class ErrorResponse(@JsonProperty("virheet") val errors: List<ResponseError>) : Response()

private data class ResponseError(@JsonProperty("koodi") val code: Int, @JsonProperty("viesti") val message: String)

const val EpsgWgs84 = "EPSG:4326"

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class CenterLineGeometryIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val trackNumberDao: LayoutTrackNumberDao,
    //    val referenceLineDao: ReferenceLineDao,
    //    val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    //    val layoutKmPostDao: LayoutKmPostDao,
    //    val geocodingService: GeocodingService,
    private val extApiTestDataServiceV1: ExtApiTestDataServiceV1,
) : DBTestBase() {

    private val api = TrackLayoutTestApiService(mockMvc)
    private val invalidOidUrl = url("foo")

    @BeforeEach
    fun cleanup() {
        testDBServiceIn?.clearAllTables()
    }

    @Test
    fun `Invalid location track oid path param causes an error`() {
        api.get<ErrorResponse>(invalidOidUrl, emptyMap()).let { response ->
            assertTrue(containsErrorCode(response.errors, 1))
        }
    }

    @Test
    fun `Missing location track oid causes an error`() {
        api.get<ErrorResponse>(url(Oid("123.123.123")), emptyMap()).let { response ->
            assertTrue(containsErrorCode(response.errors, 2))
        }
    }

    @Test
    fun `Invalid SRID causes an error`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("koordinaatisto" to "foobar")).let { response ->
            assertTrue(containsErrorCode(response.errors, 3))
        }
    }

    @Test
    fun `Valid SRID value passes`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("koordinaatisto" to EpsgWgs84)).let { response ->
            assertFalse(containsErrorCode(response.errors, 3))
        }
    }

    @Test
    fun `Invalid kilometer start value causes an error`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("ratakilometri_alku" to "foobar")).let { response ->
            assertTrue(containsErrorCode(response.errors, 4))
        }
    }

    @Test
    fun `Valid kilometer start value passes`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("ratakilometri_alku" to "1234AB")).let { response ->
            assertFalse(containsErrorCode(response.errors, 4))
        }
    }

    @Test
    fun `Invalid kilometer end value causes an error`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("ratakilometri_loppu" to "foobar")).let { response ->
            assertTrue(containsErrorCode(response.errors, 5))
        }
    }

    @Test
    fun `Valid kilometer end value passes`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("ratakilometri_loppu" to "4321AB")).let { response ->
            assertFalse(containsErrorCode(response.errors, 5))
        }
    }

    @Test
    fun `Invalid change time value causes an error`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("muutosaika" to "foobar")).let { response ->
            assertTrue(containsErrorCode(response.errors, 6))
        }
    }

    @Test
    fun `Valid change time value passes`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("muutosaika" to "2025-03-11T10:16:41Z")).let { response ->
            assertFalse(containsErrorCode(response.errors, 6))
        }
    }

    @Test
    fun `Invalid address point interval value causes an error`() {
        api.get<ErrorResponse>(invalidOidUrl, mapOf("osoitepistevali" to "foobar")).let { response ->
            assertTrue(containsErrorCode(response.errors, 7))
        }
    }

    @Test
    fun `Track number exists in a valid response`() {
        val trackNumberName = "some track number name"
        val trackNumberId = mainOfficialContext.insert(trackNumber(TrackNumber(trackNumberName))).id

        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid, trackNumberId = trackNumberId)

        assertEquals(trackNumberName, api.get<GeometryResponse>(url(oid)).trackNumberName)
    }

    @Test
    fun `Track number oid exists in a valid response`() {
        val trackNumberOid = someOid<LayoutTrackNumber>()

        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid, trackNumberOid = trackNumberOid)

        assertEquals(trackNumberOid.toString(), api.get<GeometryResponse>(url(oid)).trackNumberOid)
    }

    @Test
    fun `Location track type exists in a valid response`() {
        val tests =
            mapOf(
                    "pääraide" to LocationTrackType.MAIN,
                    "sivuraide" to LocationTrackType.SIDE,
                    "turvaraide" to LocationTrackType.TRAP,
                    "kujaraide" to LocationTrackType.CHORD,
                )
                .mapValues { (_, trackType) ->
                    someOid<LocationTrack>().also { oid ->
                        insertLocationTrackWithOid(oid, locationTrackType = trackType)
                    }
                }

        tests.forEach { (trackTypeTranslation, oid) ->
            assertEquals(trackTypeTranslation, api.get<GeometryResponse>(url(oid)).locationTrackType)
        }
    }

    @Test
    fun `Location track state exists in a valid response`() {
        val tests =
            mapOf(
                    "rakennettu" to LocationTrackState.BUILT,
                    "käytössä" to LocationTrackState.IN_USE,
                    "käytöstä poistettu" to LocationTrackState.NOT_IN_USE,
                    "poistettu" to LocationTrackState.DELETED,
                )
                .mapValues { (_, trackState) ->
                    someOid<LocationTrack>().also { oid ->
                        insertLocationTrackWithOid(oid, locationTrackState = trackState)
                    }
                }

        tests.forEach { (trackStateTranslation, oid) ->
            assertEquals(trackStateTranslation, api.get<GeometryResponse>(url(oid)).locationTrackState)
        }
    }

    @Test
    fun `Location track name exists in a valid response`() {
        val trackName = "some location track name"

        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid, locationTrackName = trackName)

        assertEquals(trackName, api.get<GeometryResponse>(url(oid)).locationTrackName)
    }

    @Test fun `Location track description exists in a valid response`() {}

    @Test
    fun `Location track owner exists in a valid response`() {
        val tests =
            mapOf(
                    // Unfortunately these are not added dynamically in the business logic, and creating a new dao just
                    // for this test seems a little unnecessary.
                    "Väylävirasto" to IntId<LocationTrackOwner>(1),
                    "Väylävirasto / yksityinen" to IntId(2),
                    "Muu yksityinen" to IntId(3),
                )
                .mapValues { (_, ownerId) ->
                    someOid<LocationTrack>().also { oid ->
                        insertLocationTrackWithOid(oid, locationTrackOwnerId = ownerId)
                    }
                }

        tests.forEach { (ownerName, oid) ->
            assertEquals(ownerName, api.get<GeometryResponse>(url(oid)).locationTrackOwner)
        }
    }

    @Test
    fun `Location track start position exists in a valid response`() {
        val startSegmentX = -5.0
        val startSegmentY = 7.5

        val segments = listOf(segment(Point(startSegmentX, startSegmentY), Point(5.0, 0.0)))

        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid, segments = segments)

        val startPoint = api.get<GeometryResponse>(url(oid)).locationTrackStart
        assertEquals(startSegmentX, startPoint.x)
        assertEquals(startSegmentY, startPoint.y)
    }

    @Test
    fun `Location track end position exists in a valid response`() {
        val endSegmentX = 15.0
        val endSegmentY = 25.0

        val segments = listOf(segment(Point(0.0, 0.0), Point(endSegmentX, endSegmentY)))

        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid, segments = segments)

        val endPoint = api.get<GeometryResponse>(url(oid)).locationTrackEnd
        assertEquals(endSegmentX, endPoint.x)
        assertEquals(endSegmentY, endPoint.y)
    }

    @Test
    fun `Coordinate system exists in a valid response`() {
        val tests = listOf("EPSG:3500", "EPSG:4236", "EPSG:5111")

        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid)

        tests.forEach { epsgCode ->
            assertEquals(
                epsgCode,
                api.get<GeometryResponse>(url(oid), mapOf("koordinaatisto" to epsgCode)).coordinateSystem,
            )
        }
    }

    @Test
    fun `Address point interval exists in a valid response`() {
        val addressPointIntervals = listOf("0.25", "1.0")

        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid)

        addressPointIntervals.forEach { interval ->
            assertEquals(
                interval,
                api.get<GeometryResponse>(url(oid), mapOf("osoitepistevali" to interval)).addressPointIntervalMeters,
            )
        }
    }

    @Test
    fun `Location track geometry points are not returned by default`() {
        val oid = someOid<LocationTrack>()
        insertLocationTrackWithOid(oid)

        assertEquals(0, api.get<GeometryResponse>(url(oid)).geometry.size)
    }

    @Test fun `Entire location track geometry is returned without other filters`() {}

    @Test fun `User provided change time is taken into account when returning geometry changes`() {}

    @Test fun `User provided start track km filter works`() {}

    @Test fun `User provided end track km filter works as inclusive`() {}

    @Test
    fun `Deleted kilometers of a location track are returned as empty lists`() {
        // TODO this requires a modified track and change time as an argument
    }

    @Test fun `User provided coordinate system is used for returned geometry values`() {}

    @Test fun `User provided address point interval value is used for returned geometry values`() {}

    private fun insertLocationTrackWithOid(
        oid: Oid<LocationTrack>,
        trackNumberId: IntId<LayoutTrackNumber> =
            mainOfficialContext.insert(trackNumber(TrackNumber(testDBService.getUnusedTrackNumber().value))).id,
        trackNumberOid: Oid<LayoutTrackNumber> = someOid(),
        locationTrackName: String = "Test track-${UUID.randomUUID()}",
        locationTrackType: LocationTrackType = LocationTrackType.MAIN,
        locationTrackState: LocationTrackState = LocationTrackState.IN_USE,
        locationTrackOwnerId: IntId<LocationTrackOwner> = IntId(1),
        segments: List<LayoutSegment> = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
    ): IntId<LocationTrack> {
        val geocodableTrack =
            extApiTestDataServiceV1.insertGeocodableTrack(
                mainOfficialContext,
                trackNumberId = trackNumberId,
                locationTrackName = locationTrackName,
                locationTrackType = locationTrackType,
                state = locationTrackState,
                owner = locationTrackOwnerId,
                segments = segments,
            )

        locationTrackDao.insertExternalId(
            geocodableTrack.locationTrack.id as IntId,
            mainOfficialContext.context.branch,
            oid,
        )

        // TODO This might now always be necessary if the api allows for non-geocodable track processing (better errors)
        trackNumberDao.insertExternalId(
            geocodableTrack.trackNumber.id as IntId,
            mainOfficialContext.context.branch,
            trackNumberOid,
        )

        return geocodableTrack.locationTrack.id as IntId
    }
}

private fun containsErrorCode(errors: List<ResponseError>, code: Int): Boolean {
    return errors.any { error -> error.code == code }
}
