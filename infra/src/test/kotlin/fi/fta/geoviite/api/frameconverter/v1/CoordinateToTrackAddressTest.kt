import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

const val API_URL = "/frame-converter/v1"
const val COORDINATE_TO_TRACK_ADDRESS_URL = "$API_URL/todo-url" // TODO What's the correct URL?

// Purposefully different data structure as the actual logic to imitate a user
private data class TestCoordinateToTrackAddressRequest(
    val tunniste: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val sade: Double? = null,
    val ratanumero: String? = null,
    val sijaintiraide: String? = null,
    val sijaintiraide_tyyppi: String? = null,
    val palautusarvot: List<Int>? = null,
)

private data class GeocodableTrack(
    val layoutContext: LayoutContext,
    val trackNumber: TrackLayoutTrackNumber,
    val referenceLine: ReferenceLine,
    val locationTrack: LocationTrack,
)

@GeoviiteIntegrationApiTest
class CoordinateToTrackAddressTest @Autowired constructor(
    mockMvc: MockMvc,
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
) : DBTestBase() {

    private val mapper = ObjectMapper().apply {
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    val testApi = TestApi(mapper, mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBServiceIn?.clearAllTables()
    }

    @Test
    fun `Completely invalid request should result in a generic error`() {
        testApi.doGet(COORDINATE_TO_TRACK_ADDRESS_URL, HttpStatus.BAD_REQUEST)
        testApi.doPost(COORDINATE_TO_TRACK_ADDRESS_URL, null, HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Partially valid request should result in a descriptive error`() {
        val params = mapOf("x" to "0.0")

        val featureCollections = listOf(
            testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
                .let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java) },

            testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
                .let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java) }
        )

        featureCollections.forEach { collection ->
            assertNotNull(collection.features[0].properties?.get("virheet"))
        }
    }

    @Test
    fun `Invalid request with JSON should result in an error`() {
        val params = mapOf("json" to "")

        testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.BAD_REQUEST)
        testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Missing x-coordinate in a request should result in an error`() {
        val request = TestCoordinateToTrackAddressRequest(
            y = 0.0,
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)

        assertEquals(
            "Pyyntö ei sisältänyt x-koordinaattia.",
            featureCollection.features[0].properties?.get("virheet"),
        )
    }

    @Test
    fun `Missing y-coordinate in a request should result in an error`() {
        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)

        assertEquals(
            "Pyyntö ei sisältänyt y-koordinaattia.",
            featureCollection.features[0].properties?.get("virheet"),
        )
    }

    @Test
    fun `Valid single request without any matching tracks should succeed but return error feature`() {
        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = 0.0,
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)

        val properties = featureCollection.features[0].properties

        assertEquals(
            "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Valid multi request without any matches should succeed but return error features`() {
        val requests = listOf(
            TestCoordinateToTrackAddressRequest(
                x = 0.0,
                y = 0.0,
            ),
            TestCoordinateToTrackAddressRequest(
                x = 15.0,
                y = 15.0,
            )
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(requests),
        )

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(2, featureCollection.features.size)

        featureCollection.features.forEach { feature ->
            assertEquals("Feature", feature.type)
            assertEquals("Point", feature.geometry?.type)
            assertEquals(emptyList<Double>(), feature.geometry?.coordinates)

            val properties = feature.properties

            assertEquals(
                "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
                properties?.get("virheet"),
            )
        }
    }

    @Test
    fun `Valid multi request with some matches should succeed but return error features`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val requests = listOf(
            TestCoordinateToTrackAddressRequest(
                tunniste = "unmatching request",
                x = -15.0,
                y = 0.0,
                sade = 2.0,
            ),
            TestCoordinateToTrackAddressRequest(
                tunniste = "matching request",
                x = 0.0,
                y = 0.0,
            ),
            TestCoordinateToTrackAddressRequest(
                tunniste = "unmatching request 2",
                x = 15.0,
                y = 0.0,
                sade = 3.0,
            ),
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(requests),
        )

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(3, featureCollection.features.size)

        val expectedErrorMessage = "Annetun (alku)pisteen parametreilla ei löytynyt tietoja."

        assertEquals(requests[0].tunniste, featureCollection.features[0].properties?.get("tunniste"))
        assertEquals(expectedErrorMessage, featureCollection.features[0].properties?.get("virheet"))

        assertEquals(requests[1].tunniste, featureCollection.features[1].properties?.get("tunniste"))
        assertEquals(null, featureCollection.features[1].properties?.get("virheet"))

        assertEquals(requests[2].tunniste, featureCollection.features[2].properties?.get("tunniste"))
        assertEquals(expectedErrorMessage, featureCollection.features[2].properties?.get("virheet"))
    }

    @Test
    fun `Valid single request using request params should succeed`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val params = mapOf(
            "x" to "0.0",
            "y" to "0.0",
        )

        testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
        testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
    }

    @Test
    fun `Valid single request with JSON should succeed`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = 0.0,
        )

        testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, createJsonRequestParams(request), HttpStatus.OK)
        testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, createJsonRequestParams(request), HttpStatus.OK)
    }

    @Test
    fun `Valid JSON request with multiple transformations should succeed`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val requests = listOf(
            TestCoordinateToTrackAddressRequest(
                x = 0.0,
                y = 0.0,
            ),
            TestCoordinateToTrackAddressRequest(
                x = 1.0,
                y = 1.0,
            ),
        )

        testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, createJsonRequestParams(requests), HttpStatus.OK)
        testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, createJsonRequestParams(requests), HttpStatus.OK)
    }

    @Test
    fun `Response feature should include identifier of the request if submitted`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val identifiers = listOf(
            "some-identifier-${UUID.randomUUID()}",
            "some-identifier-${UUID.randomUUID()}"
        )

        val requests = listOf(
            TestCoordinateToTrackAddressRequest(
                tunniste = identifiers[0],
                x = 0.0,
                y = 0.0,
            ),
            TestCoordinateToTrackAddressRequest(
                tunniste = identifiers[1],
                x = 1.0,
                y = 1.0,
            )
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(requests)
        )

        assertEquals(identifiers[0], featureCollection.features[0].properties?.get("tunniste"), "key=tunniste")
        assertEquals(identifiers[1], featureCollection.features[1].properties?.get("tunniste"), "key=tunniste")
    }

    @Test
    fun `Requests with both request params and JSON should prioritize JSON`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val identifiers = (0..2).map { _ ->
            "some-identifier-${UUID.randomUUID()}"
        }

        val jsonRequests = listOf(
            TestCoordinateToTrackAddressRequest(
                tunniste = identifiers[0],
                x = 0.0,
                y = 0.0,
            ),
            TestCoordinateToTrackAddressRequest(
                tunniste = identifiers[1],
                x = 1.0,
                y = 1.0,
            )
        )

        val params = mapOf(
            "tunniste" to identifiers[2],
            "x" to "-5.0",
            "y" to "-5.0",
            "json" to mapper.writeValueAsString(jsonRequests),
        )

        val featureCollections = listOf(
            testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
                .let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java) },

            testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
                .let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java) }
        )

        featureCollections.forEach { collection ->
            assertEquals(identifiers[0], collection.features[0].properties?.get("tunniste"), "key=tunniste")
            assertEquals(identifiers[1], collection.features[1].properties?.get("tunniste"), "key=tunniste")
        }
    }

    @Test
    fun `Basic request should default to return data with responseSettings 1 and 10`() {
        val geocodableTrack = insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val yDifferenceToTargetLocation = 5.0

        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = yDifferenceToTargetLocation,
        )

        val expectedProperties = mapOf(
            "x" to 0.0,
            "y" to 0.0,
            "valimatka" to yDifferenceToTargetLocation,
            "ratanumero" to geocodableTrack.trackNumber.number.toString(),
            "sijaintiraide" to geocodableTrack.locationTrack.name.toString(),
            "sijaintiraide_kuvaus" to locationTrackService.getFullDescription(
                layoutContext = geocodableTrack.layoutContext,
                locationTrack = geocodableTrack.locationTrack,
                lang = LocalizationLanguage.FI,
            ).toString(),
            "sijaintiraide_tyyppi" to "pääraide",
            "ratakilometri" to "0000",
            "ratametri" to 10,
            "ratametri_desimaalit" to 0,
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)

        expectedProperties.forEach { (key, value) ->
            assertEquals(value, featureCollection.features[0].properties?.get(key), "key=$key")
        }
    }

    @Test
    fun `Request with empty but assigned responseSettings should succeed but not return any actual data`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = 0.0,
            palautusarvot = emptyList(),
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)
        assertEquals(emptyMap<String, String>(), featureCollection.features[0].properties)
    }

    @Test
    fun `Basic filtering with radius works`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val requestsToExpectedError = mapOf(
            TestCoordinateToTrackAddressRequest(
                tunniste = "requestWithoutRadius",
                x = 0.0,
                y = 0.0,
            ) to null,

            TestCoordinateToTrackAddressRequest(
                tunniste = "requestInsideRadius",
                x = 0.0,
                y = 0.0,
                sade = 1.0,
            ) to null,

            TestCoordinateToTrackAddressRequest(
                tunniste = "requestOutsideRadius",
                x = 0.0,
                y = 3.0,
                sade = 2.0,
            ) to "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
        )

        requestsToExpectedError.forEach { (request, expectedError) ->
            val featureCollection = fetchFeatureCollection(
                COORDINATE_TO_TRACK_ADDRESS_URL,
                createJsonRequestParams(request),
            )

            assertEquals(1, featureCollection.features.size, "request=${request.tunniste}")

            val properties = featureCollection.features[0].properties
            assertEquals(expectedError, properties?.get("virheet"), "request=${request.tunniste}")
        }
    }

    @Test
    fun `Filtering with track number works`() {
        val trackNumberIds = (0..2).map { _ ->
            mainOfficialContext.createLayoutTrackNumber().id
        }

        // Purposefully uses the same segments for overlap in order to determine
        // that the filtering works based on the track number.
        val segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        val tracks = trackNumberIds.map { trackNumberId ->
            insertGeocodableTrack(
                trackNumberId = trackNumberId,
                segments = segments,
            )
        }

        tracks.forEach { geocodableTrack ->
            val trackNumberName = geocodableTrack.trackNumber.number.toString()

            val request = TestCoordinateToTrackAddressRequest(
                x = 0.0,
                y = 0.0,
                ratanumero = trackNumberName,
            )

            val featureCollection = fetchFeatureCollection(
                COORDINATE_TO_TRACK_ADDRESS_URL,
                createJsonRequestParams(request),
            )

            val properties = featureCollection.features[0].properties
            assertEquals(trackNumberName, properties?.get("ratanumero"))
        }
    }

    @Test
    fun `Filtering with location track name works`() {
        // Purposefully uses the same segments for overlap in order to determine
        // that the filtering works based on the location track name.
        val segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        val tracks = (0..2).map { _ ->
            insertGeocodableTrack(
                locationTrackName = "Test track-${UUID.randomUUID()}",
                segments = segments,
            )
        }

        tracks.forEach { geocodableTrack ->
            val locationTrackName = geocodableTrack.locationTrack.name.toString()

            val request = TestCoordinateToTrackAddressRequest(
                x = 0.0,
                y = 0.0,
                sijaintiraide = locationTrackName,
            )

            val featureCollection = fetchFeatureCollection(
                COORDINATE_TO_TRACK_ADDRESS_URL,
                createJsonRequestParams(request),
            )

            val properties = featureCollection.features[0].properties
            assertEquals(locationTrackName, properties?.get("sijaintiraide"))
        }
    }

    @Test
    fun `Filtering with location track type works`() {
        // Purposefully uses the same segments for overlap in order to determine
        // that the filtering works based on the location track type.
        val segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))

        LocationTrackType.entries.forEach { locationTrackType ->
            insertGeocodableTrack(
                segments = segments,
                locationTrackType = locationTrackType,
            )
        }

        listOf(
            "pääraide",
            "sivuraide",
            "kujaraide",
            "turvaraide"
        ).forEach { trackType ->
            val request = TestCoordinateToTrackAddressRequest(
                x = 0.0,
                y = 0.0,
                sijaintiraide_tyyppi = trackType,
            )

            val featureCollection = fetchFeatureCollection(
                COORDINATE_TO_TRACK_ADDRESS_URL,
                createJsonRequestParams(request),
            )

            val properties = featureCollection.features[0].properties
            assertEquals(trackType, properties?.get("sijaintiraide_tyyppi"))
        }
    }

    @Test
    fun `Response output data setting 1 works`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val yDifference = 1.0
        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = yDifference,
            palautusarvot = listOf(1), // This is the parameter being tested.
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(0, (featureCollection.features[0].geometry?.coordinates as List<*>).size)

        val properties = featureCollection.features[0].properties

        assertEquals(0.0, properties?.get("x"))
        assertEquals(0.0, properties?.get("y"))
        assertEquals(yDifference, ((properties?.get("valimatka") as? Double)!!), 0.001)

        assertEquals(null, properties.get("ratanumero"))
        assertEquals(null, properties.get("sijaintiraide"))
        assertEquals(null, properties.get("sijaintiraide_kuvaus"))
        assertEquals(null, properties.get("sijaintiraide_tyyppi"))
        assertEquals(null, properties.get("ratakilometri"))
        assertEquals(null, properties.get("ratametri"))
        assertEquals(null, properties.get("ratametri_desimaalit"))
    }

    @Test
    fun `Response output data setting 5 works`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = 1.0,
            palautusarvot = listOf(5), // This is the parameter being tested.
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)

        val coordinateList = (featureCollection.features[0].geometry?.coordinates as List<*>)
        assertEquals(2, coordinateList.size)

        val coordinatesOnTrack = coordinateList.map { value -> value as Double }
        assertEquals(0.0, coordinatesOnTrack[0])
        assertEquals(0.0, coordinatesOnTrack[1])

        val properties = featureCollection.features[0].properties

        assertEquals(null, properties?.get("x"))
        assertEquals(null, properties?.get("y"))
        assertEquals(null, properties?.get("valimatka"))

        assertEquals(null, properties?.get("ratanumero"))
        assertEquals(null, properties?.get("sijaintiraide"))
        assertEquals(null, properties?.get("sijaintiraide_kuvaus"))
        assertEquals(null, properties?.get("sijaintiraide_tyyppi"))
        assertEquals(null, properties?.get("ratakilometri"))
        assertEquals(null, properties?.get("ratametri"))
        assertEquals(null, properties?.get("ratametri_desimaalit"))
    }

    @Test
    fun `Response output data setting 10 works`() {
        val geocodableTrack = insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
            locationTrackType = LocationTrackType.CHORD,
        )

        val trackDescription = locationTrackService.getFullDescription(
            layoutContext = geocodableTrack.layoutContext,
            locationTrack = geocodableTrack.locationTrack,
            lang = LocalizationLanguage.FI,
        ).toString()

        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = 1.0,
            palautusarvot = listOf(10), // This is the parameter being tested.
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(0, (featureCollection.features[0].geometry?.coordinates as List<*>).size)

        val properties = featureCollection.features[0].properties

        assertEquals(null, properties?.get("x"))
        assertEquals(null, properties?.get("y"))
        assertEquals(null, properties?.get("valimatka"))

        assertEquals(geocodableTrack.trackNumber.number.toString(), properties?.get("ratanumero"))
        assertEquals(geocodableTrack.locationTrack.name.toString(), properties?.get("sijaintiraide"))
        assertEquals(trackDescription, properties?.get("sijaintiraide_kuvaus"))
        assertEquals("kujaraide", properties?.get("sijaintiraide_tyyppi"))
        assertEquals("0000", properties?.get("ratakilometri"))
        assertEquals(10, properties?.get("ratametri") as? Int)
        assertEquals(0, properties?.get("ratametri_desimaalit") as? Int)
    }

    @Test
    fun `Response output data setting combination works`() {
        val geocodableTrack = insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
            locationTrackType = LocationTrackType.TRAP,
        )

        val trackDescription = locationTrackService.getFullDescription(
            layoutContext = geocodableTrack.layoutContext,
            locationTrack = geocodableTrack.locationTrack,
            lang = LocalizationLanguage.FI,
        ).toString()

        val xPositionOnTrack = 3.0
        val yPositionOnTrack = 0.0

        val yDifference = 5.0
        val request = TestCoordinateToTrackAddressRequest(
            x = xPositionOnTrack,
            y = yPositionOnTrack + yDifference,
            palautusarvot = listOf(10, 1, 5), // This is the parameter being tested.
        )

        val featureCollection = fetchFeatureCollection(
            COORDINATE_TO_TRACK_ADDRESS_URL,
            createJsonRequestParams(request),
        )

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)

        val coordinateList = (featureCollection.features[0].geometry?.coordinates as List<*>)
        assertEquals(2, coordinateList.size)

        val coordinatesOnTrack = coordinateList.map { value -> value as Double }
        assertEquals(xPositionOnTrack, coordinatesOnTrack[0])
        assertEquals(yPositionOnTrack, coordinatesOnTrack[1])

        val properties = featureCollection.features[0].properties

        assertEquals(xPositionOnTrack, ((properties?.get("x") as? Double)!!), 0.001)
        assertEquals(yPositionOnTrack, ((properties["y"] as? Double)!!), 0.001)
        assertEquals(yDifference, ((properties["valimatka"] as? Double)!!), 0.001)

        assertEquals(geocodableTrack.trackNumber.number.toString(), properties["ratanumero"])
        assertEquals(geocodableTrack.locationTrack.name.toString(), properties["sijaintiraide"])
        assertEquals(trackDescription, properties["sijaintiraide_kuvaus"])
        assertEquals("turvaraide", properties["sijaintiraide_tyyppi"])
        assertEquals("0000", properties["ratakilometri"])
        assertEquals(10 + xPositionOnTrack.toInt(), properties["ratametri"] as? Int)
        assertEquals(0, properties["ratametri_desimaalit"] as? Int)
    }

    private fun insertGeocodableTrack(
        layoutContext: TestLayoutContext = mainOfficialContext,
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        locationTrackName: String = "Test location track",
        locationTrackType: LocationTrackType = LocationTrackType.MAIN,
        segments: List<LayoutSegment> = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
    ): GeocodableTrack {
        val referenceLineId = layoutContext.insert(
            referenceLineAndAlignment(
                trackNumberId = trackNumberId,
                segments = segments,
            )
        ).id

        val locationTrackId = layoutContext.insert(
            locationTrackAndAlignment(
                trackNumberId = trackNumberId,
                name = locationTrackName,
                type = locationTrackType,
                segments = segments,
            )
        ).id

        return GeocodableTrack(
            layoutContext = layoutContext.context,
            trackNumber = trackNumberDao.get(layoutContext.context, trackNumberId)!!,
            referenceLine = referenceLineDao.get(layoutContext.context, referenceLineId)!!,
            locationTrack = locationTrackDao.get(layoutContext.context, locationTrackId)!!,
        )
    }

    private fun fetchFeatureCollection(url: String, params: Map<String, String>): TestGeoJsonFeatureCollection {
        return testApi
            .doPostWithParams(url, params, HttpStatus.OK)
            .let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java) }
    }

    private fun createJsonRequestParams(request: TestCoordinateToTrackAddressRequest): Map<String, String> {
        return mapOf("json" to mapper.writeValueAsString(listOf(request)))
    }

    private fun createJsonRequestParams(requests: List<TestCoordinateToTrackAddressRequest>): Map<String, String> {
        return mapOf("json" to mapper.writeValueAsString(requests))
    }
}

