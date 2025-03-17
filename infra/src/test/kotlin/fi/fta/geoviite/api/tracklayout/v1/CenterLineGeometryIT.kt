package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.kmPost
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
    @JsonProperty("osoitevalit") val trackIntervals: List<ResponseGeometryInterval>,
)

private data class ResponseGeometryInterval(
    @JsonProperty("alku") val startAddress: String,
    @JsonProperty("loppu") val endAddress: String,
    @JsonProperty("pisteet") val geometryPoints: List<ResponseGeometryPoint>,
)

private data class ResponseGeometryPoint(
    @JsonProperty("x") val x: Double,
    @JsonProperty("y") val y: Double,
    @JsonProperty("ratakilometri") val kmNumber: String,
    @JsonProperty("ratametri") val trackMeter: Double,
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
    private val layoutKmPostDao: LayoutKmPostDao,
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
        insertGeocodableLocationTrackWithOid(oid, trackNumberId = trackNumberId)

        assertEquals(trackNumberName, api.get<GeometryResponse>(url(oid)).trackNumberName)
    }

    @Test
    fun `Track number oid exists in a valid response`() {
        val trackNumberOid = someOid<LayoutTrackNumber>()

        val oid = someOid<LocationTrack>()
        insertGeocodableLocationTrackWithOid(oid, trackNumberOid = trackNumberOid)

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
                        insertGeocodableLocationTrackWithOid(oid, locationTrackType = trackType)
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
                        insertGeocodableLocationTrackWithOid(oid, locationTrackState = trackState)
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
        insertGeocodableLocationTrackWithOid(oid, locationTrackName = trackName)

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
                        insertGeocodableLocationTrackWithOid(oid, locationTrackOwnerId = ownerId)
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
        insertGeocodableLocationTrackWithOid(oid, segments = segments)

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
        insertGeocodableLocationTrackWithOid(oid, segments = segments)

        val endPoint = api.get<GeometryResponse>(url(oid)).locationTrackEnd
        assertEquals(endSegmentX, endPoint.x)
        assertEquals(endSegmentY, endPoint.y)
    }

    @Test
    fun `Coordinate system exists in a valid response`() {
        val tests = listOf("EPSG:3500", "EPSG:4236", "EPSG:5111")

        val oid = someOid<LocationTrack>()
        insertGeocodableLocationTrackWithOid(oid)

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
        insertGeocodableLocationTrackWithOid(oid)

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
        insertGeocodableLocationTrackWithOid(oid)

        assertEquals(0, api.get<GeometryResponse>(url(oid)).trackIntervals.size)
    }

    @Test fun `Entire location track geometry is returned without other filters`() {}

    @Test fun `User provided change time is taken into account when returning geometry changes`() {}

    @Test
    fun `User provided start track km filters away preceding track kilometers`() {
        val oid = someOid<LocationTrack>()

        val geocodableTrack =
            extApiTestDataServiceV1.insertGeocodableTrack(
                layoutContext = mainOfficialContext,
                segments = listOf(segment(Point(0.0, 0.0), Point(3000.0, 0.0))),
            )

        locationTrackDao.insertExternalId(
            geocodableTrack.locationTrack.id as IntId,
            mainOfficialContext.context.branch,
            oid,
        )

        trackNumberDao.insertExternalId(
            geocodableTrack.trackNumber.id as IntId,
            mainOfficialContext.context.branch,
            someOid(),
        )

        listOf(
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(0),
                    roughLayoutLocation = Point(1000.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(1, "AB"),
                    roughLayoutLocation = Point(1100.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(1, "CD"),
                    roughLayoutLocation = Point(1500.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(2),
                    roughLayoutLocation = Point(2000.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(2, "EF"),
                    roughLayoutLocation = Point(2100.0, 0.0),
                    draft = false,
                ),
            )
            .forEach(layoutKmPostDao::save)

        val tests =
            listOf(
                // Using the first track km should result in the response containing every track km.
                "0000" to setOf("0000", "0001AB", "0001CD", "0002", "0002EF"),

                // Using the second track km should also include both of the track kms on the same track kilometer.
                "0001AB" to setOf("0001AB", "0001CD", "0002", "0002EF"),

                // Lexical filtering of track kms should work (0001AB is before 0001CD).
                "0001CD" to setOf("0001CD", "0002", "0002EF"),

                // Track km filter without extension should also work.
                "0002" to setOf("0002", "0002EF"),

                // Track km which does not exist should work as a filter.
                "1234" to emptySet(),
            )

        tests.forEach { (trackKmFilter, expectedTrackKms) ->
            val response =
                api.get<GeometryResponse>(
                    url(oid),
                    mapOf("geometriatiedot" to "true", "ratakilometri_alku" to trackKmFilter),
                )

            // TODO The response should also only contain track interval which is related to the asked track km
            // (Check the alku ja and loppu)
            assertEquals(
                expectedTrackKms,
                response.trackIntervals.first().geometryPoints.map { p -> p.kmNumber }.toSet(),
            )
        }
    }

    @Test
    fun `User provided end track km filter works as inclusive, but filters away succeeding track kms`() {
        val oid = someOid<LocationTrack>()

        val geocodableTrack =
            extApiTestDataServiceV1.insertGeocodableTrack(
                layoutContext = mainOfficialContext,
                segments = listOf(segment(Point(0.0, 0.0), Point(3000.0, 0.0))),
            )

        locationTrackDao.insertExternalId(
            geocodableTrack.locationTrack.id as IntId,
            mainOfficialContext.context.branch,
            oid,
        )

        trackNumberDao.insertExternalId(
            geocodableTrack.trackNumber.id as IntId,
            mainOfficialContext.context.branch,
            someOid(),
        )

        listOf(
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(0),
                    roughLayoutLocation = Point(1000.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(1, "AB"),
                    roughLayoutLocation = Point(1100.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(1, "CD"),
                    roughLayoutLocation = Point(1500.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(2),
                    roughLayoutLocation = Point(2000.0, 0.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(2, "EF"),
                    roughLayoutLocation = Point(2100.0, 0.0),
                    draft = false,
                ),
            )
            .forEach(layoutKmPostDao::save)

        val tests =
            listOf(
                // Track km "above" any of the actually created track kms should still work, but return the entire track
                "1234" to setOf("0000", "0001AB", "0001CD", "0002", "0002EF"),

                // Using the last track km should result in the response containing every track km (including itself).
                "0002EF" to setOf("0000", "0001AB", "0001CD", "0002", "0002EF"),

                // Using the second last track km should lexically filter away the last one.
                "0002" to setOf("0000", "0001AB", "0001CD", "0002"),

                // Lexical and inclusive filtering should work in the middle of the track.
                "0001CD" to setOf("0000", "0001AB", "0001CD"),

                // Lexical and inclusive filtering should work in the middle of the track.
                "0001AB" to setOf("0000", "0001AB"),

                // Using the first km as the end filter should only include itself.
                "0000" to setOf("0000"),
            )

        tests.forEach { (trackKmFilter, expectedTrackKms) ->
            val response =
                api.get<GeometryResponse>(
                    url(oid),
                    mapOf("geometriatiedot" to "true", "ratakilometri_loppu" to trackKmFilter),
                )

            assertEquals(
                expectedTrackKms,
                response.trackIntervals.first().geometryPoints.map { p -> p.kmNumber }.toSet(),
            )
        }
    }

    @Test
    fun `Deleted kilometers of a location track are returned as empty lists`() {
        // TODO this requires a modified track and change time as an argument
    }

    @Test
    fun `User provided coordinate system is used for returned geometry values`() {
        val basePoint = Point(x = 385782.89, y = 6672277.83) // Helsinki railway station in ETRS-TM35FIN (EPSG:3067)

        val oid = someOid<LocationTrack>()
        val geocodableTrack =
            extApiTestDataServiceV1.insertGeocodableTrack(
                layoutContext = mainOfficialContext,
                segments = listOf(segment(basePoint, basePoint + Point(3000.0, 2000.0))),
            )

        locationTrackDao.insertExternalId(
            geocodableTrack.locationTrack.id as IntId,
            mainOfficialContext.context.branch,
            oid,
        )

        trackNumberDao.insertExternalId(
            geocodableTrack.trackNumber.id as IntId,
            mainOfficialContext.context.branch,
            someOid(),
        )

        listOf(
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(0),
                    roughLayoutLocation = basePoint,
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(1),
                    roughLayoutLocation = basePoint + Point(1000.0, 500.0),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(2),
                    roughLayoutLocation = basePoint + Point(2000.0, 1000.0),
                    draft = false,
                ),
            )
            .forEach(layoutKmPostDao::save)

        val response =
            api.get<GeometryResponse>(url(oid), mapOf("geometriatiedot" to "true", "koordinaatisto" to EpsgWgs84))

        // Expected values were calculated externally using https://epsg.io/transform
        val requiredAccuracy = 0.0001

        assertEquals(24.9414003, response.locationTrackStart.x, requiredAccuracy)
        assertEquals(60.1713788, response.locationTrackStart.y, requiredAccuracy)

        assertEquals(24.9943374, response.locationTrackEnd.x, requiredAccuracy)
        assertEquals(60.1901543, response.locationTrackEnd.y, requiredAccuracy)

        val trackInterval = response.trackIntervals.first()

        assertEquals(24.9414003, filterByKmNumber(trackInterval, "0000").first().x, requiredAccuracy)
        assertEquals(60.1713788, filterByKmNumber(trackInterval, "0000").first().y, requiredAccuracy)

        assertEquals(24.9576822, filterByKmNumber(trackInterval, "0001").first().x, requiredAccuracy)
        assertEquals(60.1771581, filterByKmNumber(trackInterval, "0001").first().y, requiredAccuracy)

        assertEquals(24.9739698, filterByKmNumber(trackInterval, "0002").first().x, requiredAccuracy)
        assertEquals(60.1829354, filterByKmNumber(trackInterval, "0002").first().y, requiredAccuracy)
    }

    @Test
    fun `Default track address point interval value is 1 meter`() {
        val basePoint = Point(0.0, 0.0)

        val oid = someOid<LocationTrack>()
        val geocodableTrack =
            extApiTestDataServiceV1.insertGeocodableTrack(
                layoutContext = mainOfficialContext,
                segments = listOf(segment(basePoint, Point(1000.0, 0.0))),
            )

        locationTrackDao.insertExternalId(
            geocodableTrack.locationTrack.id as IntId,
            mainOfficialContext.context.branch,
            oid,
        )

        trackNumberDao.insertExternalId(
            geocodableTrack.trackNumber.id as IntId,
            mainOfficialContext.context.branch,
            someOid(),
        )

        listOf(
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(0),
                    roughLayoutLocation = basePoint,
                    draft = false,
                )
            )
            .forEach(layoutKmPostDao::save)

        val requiredAccuracy = 0.001

        val response = api.get<GeometryResponse>(url(oid), mapOf("geometriatiedot" to "true"))
        val firstTrackKmPoints = filterByKmNumber(response.trackIntervals.first(), "0000")

        assertEquals(0.0, firstTrackKmPoints[0].x, requiredAccuracy)
        assertEquals(1.0, firstTrackKmPoints[1].x, requiredAccuracy)
        assertEquals(2.0, firstTrackKmPoints[2].x, requiredAccuracy)
    }

    @Test
    fun `User provided quarter meter address point interval value is used for returned geometry values`() {
        val basePoint = Point(0.0, 0.0)

        val oid = someOid<LocationTrack>()
        val geocodableTrack =
            extApiTestDataServiceV1.insertGeocodableTrack(
                layoutContext = mainOfficialContext,
                segments = listOf(segment(basePoint, Point(1000.0, 0.0))),
            )

        locationTrackDao.insertExternalId(
            geocodableTrack.locationTrack.id as IntId,
            mainOfficialContext.context.branch,
            oid,
        )

        trackNumberDao.insertExternalId(
            geocodableTrack.trackNumber.id as IntId,
            mainOfficialContext.context.branch,
            someOid(),
        )

        listOf(
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(0),
                    roughLayoutLocation = basePoint,
                    draft = false,
                )
            )
            .forEach(layoutKmPostDao::save)

        val requiredAccuracy = 0.001

        val response =
            api.get<GeometryResponse>(url(oid), mapOf("geometriatiedot" to "true", "osoitepistevali" to "0.25"))

        val firstTrackKmPoints = filterByKmNumber(response.trackIntervals.first(), "0000")

        assertEquals(0.0, firstTrackKmPoints[0].x, requiredAccuracy)
        assertEquals(0.25, firstTrackKmPoints[1].x, requiredAccuracy)
        assertEquals(0.5, firstTrackKmPoints[2].x, requiredAccuracy)
        assertEquals(0.75, firstTrackKmPoints[3].x, requiredAccuracy)
        assertEquals(1.0, firstTrackKmPoints[4].x, requiredAccuracy)
    }

    @Test
    fun `Invalid coordinates should result in coordinate transformation error`() {
        // TODO
    }

    private fun insertGeocodableLocationTrackWithOid(
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

        // TODO This might not always be necessary if the api allows for non-geocodable track processing (better errors)
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

private fun filterByKmNumber(trackInterval: ResponseGeometryInterval, km: String): List<ResponseGeometryPoint> {
    return trackInterval.geometryPoints.filter { p -> p.kmNumber == km }
}
