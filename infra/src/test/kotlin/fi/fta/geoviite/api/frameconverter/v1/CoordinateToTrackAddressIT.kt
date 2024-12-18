package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.pointDistanceToLine
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.util.*
import kotlin.math.hypot
import kotlin.test.assertEquals

private const val API_TRACK_ADDRESSES: FrameConverterUrl = "/rata-vkm/v1/rataosoitteet"

// Purposefully different data structure as the actual logic to imitate a user.
private data class TestCoordinateToTrackAddressRequest(
    val tunniste: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val sade: Double? = null,
    val ratanumero: String? = null,
    val sijaintiraide: String? = null,
    val sijaintiraide_tyyppi: String? = null,
) : FrameConverterTestRequest()

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class CoordinateToTrackAddressIT
@Autowired
constructor(
    mockMvc: MockMvc,
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
    val layoutKmPostDao: LayoutKmPostDao,
    val geocodingService: GeocodingService,
) : DBTestBase() {

    private val api = FrameConverterTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBServiceIn?.clearAllTables()
    }

    @Test
    fun `Partially valid request should result in a descriptive error`() {
        val params = mapOf("x" to "0.0")
        val featureCollection = api.fetchFeatureCollectionSingle(API_TRACK_ADDRESSES, params)

        assertNotNull(featureCollection.features[0].properties?.get("virheet"))
    }

    @Test
    fun `Missing x-coordinate in a request should result in an error`() {
        val request = TestCoordinateToTrackAddressRequest(y = 0.0)
        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)

        assertContainsErrorMessage(
            "Pyyntö ei sisältänyt x-koordinaattia.",
            featureCollection.features[0].properties?.get("virheet"),
        )
    }

    @Test
    fun `Missing y-coordinate in a request should result in an error`() {
        val request = TestCoordinateToTrackAddressRequest(x = 0.0)
        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)

        assertContainsErrorMessage(
            "Pyyntö ei sisältänyt y-koordinaattia.",
            featureCollection.features[0].properties?.get("virheet"),
        )
    }

    @Test
    fun `Valid single request without any matching tracks should succeed but return error feature`() {
        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0)
        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)

        val properties = featureCollection.features[0].properties

        assertContainsErrorMessage(
            "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Valid multi request without any matches should succeed but return error features`() {
        val requests =
            listOf(
                TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0),
                TestCoordinateToTrackAddressRequest(x = 15.0, y = 15.0),
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, requests)

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(2, featureCollection.features.size)

        featureCollection.features.forEach { feature ->
            assertEquals("Feature", feature.type)
            assertEquals("Point", feature.geometry?.type)
            assertEquals(emptyList<Double>(), feature.geometry?.coordinates)

            val properties = feature.properties

            assertContainsErrorMessage(
                "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
                properties?.get("virheet"),
            )
        }
    }

    @Test
    fun `Valid multi request with some matches should succeed but return error features`() {
        insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val requests =
            listOf(
                TestCoordinateToTrackAddressRequest(tunniste = "unmatching request", x = -15.0, y = 0.0, sade = 2.0),
                TestCoordinateToTrackAddressRequest(tunniste = "matching request", x = 0.0, y = 0.0),
                TestCoordinateToTrackAddressRequest(tunniste = "unmatching request 2", x = 15.0, y = 0.0, sade = 3.0),
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, requests)

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(3, featureCollection.features.size)

        val expectedErrorMessage = "Annetun (alku)pisteen parametreilla ei löytynyt tietoja."

        assertEquals(requests[0].tunniste, featureCollection.features[0].properties?.get("tunniste"))
        assertContainsErrorMessage(expectedErrorMessage, featureCollection.features[0].properties?.get("virheet"))

        assertEquals(requests[1].tunniste, featureCollection.features[1].properties?.get("tunniste"))
        assertEquals(null, featureCollection.features[1].properties?.get("virheet"))

        assertEquals(requests[2].tunniste, featureCollection.features[2].properties?.get("tunniste"))
        assertContainsErrorMessage(expectedErrorMessage, featureCollection.features[2].properties?.get("virheet"))
    }

    @Test
    fun `Valid single request using request params should succeed`() {
        insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val params = mapOf("x" to "0.0", "y" to "0.0")
        api.fetchFeatureCollectionSingle(API_TRACK_ADDRESSES, params)
    }

    @Test
    fun `Response feature should include identifier of the request if submitted`() {
        insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val identifiers = listOf("some-identifier-${UUID.randomUUID()}", "some-identifier-${UUID.randomUUID()}")

        val requests =
            listOf(
                TestCoordinateToTrackAddressRequest(tunniste = identifiers[0], x = 0.0, y = 0.0),
                TestCoordinateToTrackAddressRequest(tunniste = identifiers[1], x = 1.0, y = 1.0),
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, requests)

        assertEquals(identifiers[0], featureCollection.features[0].properties?.get("tunniste"), "key=tunniste")
        assertEquals(identifiers[1], featureCollection.features[1].properties?.get("tunniste"), "key=tunniste")
    }

    @Test
    fun `Basic request should default to return feature with basic and detailed data`() {
        val geocodableTrack = insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val yDifferenceToTargetLocation = 5.0

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = yDifferenceToTargetLocation)

        val expectedProperties =
            mapOf(
                "x" to 0.0,
                "y" to 0.0,
                "valimatka" to yDifferenceToTargetLocation,
                "ratanumero" to geocodableTrack.trackNumber.number.toString(),
                "sijaintiraide" to geocodableTrack.locationTrack.name.toString(),
                "sijaintiraide_kuvaus" to
                    locationTrackService
                        .getFullDescription(
                            layoutContext = geocodableTrack.layoutContext,
                            locationTrack = geocodableTrack.locationTrack,
                            lang = LocalizationLanguage.FI,
                        )
                        .toString(),
                "sijaintiraide_tyyppi" to "pääraide",
                "ratakilometri" to 0,
                "ratametri" to 10,
                "ratametri_desimaalit" to 0,
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

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
    fun `Request with all-false response data settings should succeed but not return any actual data`() {
        insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0)
        val params =
            mapOf(
                RESPONSE_GEOMETRY_KEY to false,
                RESPONSE_BASIC_FEATURE_KEY to false,
                RESPONSE_DETAILED_FEATURE_KEY to false,
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request, params)

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)
        assertEquals(emptyMap<String, String>(), featureCollection.features[0].properties)
    }

    @Test
    fun `Basic filtering with radius works`() {
        insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val requestsToExpectedError =
            mapOf(
                TestCoordinateToTrackAddressRequest(tunniste = "requestWithoutRadius", x = 0.0, y = 0.0) to null,
                TestCoordinateToTrackAddressRequest(tunniste = "requestInsideRadius", x = 0.0, y = 0.0, sade = 1.0) to
                    null,
                TestCoordinateToTrackAddressRequest(tunniste = "requestOutsideRadius", x = 0.0, y = 3.0, sade = 2.0) to
                    "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
            )

        requestsToExpectedError.forEach { (request, expectedError) ->
            val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

            assertEquals(1, featureCollection.features.size, "request=${request.tunniste}")

            val properties = featureCollection.features[0].properties
            if (expectedError == null) {
                assertEquals(expectedError, properties?.get("virheet"), "request=${request.tunniste}")
            } else {
                assertContainsErrorMessage(expectedError, properties?.get("virheet"), "request=${request.tunniste}")
            }
        }
    }

    @Test
    fun `Filtering with track number works`() {
        val trackNumberIds = (0..2).map { _ -> mainOfficialContext.createLayoutTrackNumber().id }

        // Purposefully uses the same segments for overlap in order to determine
        // that the filtering works based on the track number.
        val segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        val tracks =
            trackNumberIds.map { trackNumberId ->
                insertGeocodableTrack(trackNumberId = trackNumberId, segments = segments)
            }

        tracks.forEach { geocodableTrack ->
            val trackNumberName = geocodableTrack.trackNumber.number.toString()

            val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0, ratanumero = trackNumberName)
            val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

            val properties = featureCollection.features[0].properties
            assertEquals(trackNumberName, properties?.get("ratanumero"))
        }
    }

    @Test
    fun `Filtering with location track name works`() {
        // Purposefully uses the same segments for overlap in order to determine
        // that the filtering works based on the location track name.
        val segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        val tracks =
            (0..2).map { _ ->
                insertGeocodableTrack(locationTrackName = "Test track-${UUID.randomUUID()}", segments = segments)
            }

        tracks.forEach { geocodableTrack ->
            val locationTrackName = geocodableTrack.locationTrack.name.toString()

            val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0, sijaintiraide = locationTrackName)
            val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

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
            insertGeocodableTrack(segments = segments, locationTrackType = locationTrackType)
        }

        listOf("pääraide", "sivuraide", "kujaraide", "turvaraide").forEach { trackType ->
            val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0, sijaintiraide_tyyppi = trackType)
            val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

            val properties = featureCollection.features[0].properties
            assertEquals(trackType, properties?.get("sijaintiraide_tyyppi"))
        }
    }

    @Test
    fun `Response output data can be set to only return basic feature data`() {
        insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val yDifference = 1.0
        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = yDifference)

        // This is the thing being tested.
        val params = mapOf(RESPONSE_DETAILED_FEATURE_KEY to false)

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request, params)

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(0, (featureCollection.features[0].geometry?.coordinates as List<*>).size)

        val properties = featureCollection.features[0].properties!!

        assertEquals(0.0, properties["x"])
        assertEquals(0.0, properties["y"])
        assertEquals(yDifference, ((properties["valimatka"] as Double)), 0.001)

        assertNullDetailedProperties(properties)
    }

    @Test
    fun `Response output data can be set to only return feature geometry data`() {
        insertGeocodableTrack(segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))))

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 1.0)
        val params =
            mapOf(
                RESPONSE_BASIC_FEATURE_KEY to false,
                RESPONSE_DETAILED_FEATURE_KEY to false,
                RESPONSE_GEOMETRY_KEY to true,
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request, params)

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)

        val coordinateList = (featureCollection.features[0].geometry?.coordinates as List<*>)
        assertEquals(2, coordinateList.size)

        val coordinatesOnTrack = coordinateList.map { value -> value as Double }
        assertEquals(0.0, coordinatesOnTrack[0])
        assertEquals(0.0, coordinatesOnTrack[1])

        val properties = featureCollection.features[0].properties!!

        assertNullSimpleProperties(properties)
        assertNullDetailedProperties(properties)
    }

    @Test
    fun `Response output data can be set to only return detailed feature data`() {
        val geocodableTrack =
            insertGeocodableTrack(
                segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
                locationTrackType = LocationTrackType.CHORD,
            )

        val trackDescription =
            locationTrackService
                .getFullDescription(
                    layoutContext = geocodableTrack.layoutContext,
                    locationTrack = geocodableTrack.locationTrack,
                    lang = LocalizationLanguage.FI,
                )
                .toString()

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 1.0)

        // This is the thing being tested.
        val params =
            mapOf(
                RESPONSE_BASIC_FEATURE_KEY to false,
                RESPONSE_GEOMETRY_KEY to false,
                RESPONSE_DETAILED_FEATURE_KEY to true,
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request, params)

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(0, (featureCollection.features[0].geometry?.coordinates as List<*>).size)

        val properties = featureCollection.features[0].properties!!

        assertNullSimpleProperties(properties)

        assertEquals(geocodableTrack.trackNumber.number.toString(), properties["ratanumero"])
        assertEquals(geocodableTrack.locationTrack.name.toString(), properties["sijaintiraide"])
        assertEquals(trackDescription, properties["sijaintiraide_kuvaus"])
        assertEquals("kujaraide", properties["sijaintiraide_tyyppi"])
        assertEquals(0, properties["ratakilometri"])
        assertEquals(10, properties["ratametri"] as Int)
        assertEquals(0, properties["ratametri_desimaalit"] as Int)
    }

    @Test
    fun `Response output data setting combination works`() {
        val layoutContext = mainOfficialContext

        val geocodableTrack =
            insertGeocodableTrack(
                layoutContext = layoutContext,
                segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                locationTrackType = LocationTrackType.TRAP,
            )

        val trackDescription =
            locationTrackService
                .getFullDescription(
                    layoutContext = geocodableTrack.layoutContext,
                    locationTrack = geocodableTrack.locationTrack,
                    lang = LocalizationLanguage.FI,
                )
                .toString()

        val xPositionOnTrack = 3.0
        val yPositionOnTrack = 0.0

        val yDifference = 5.0
        val request = TestCoordinateToTrackAddressRequest(x = xPositionOnTrack, y = yPositionOnTrack + yDifference)

        // This is the thing being tested.
        val params =
            mapOf(
                RESPONSE_BASIC_FEATURE_KEY to true,
                RESPONSE_GEOMETRY_KEY to true,
                RESPONSE_DETAILED_FEATURE_KEY to true,
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request, params)

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
        assertEquals(0, properties["ratakilometri"])
        assertEquals(xPositionOnTrack.toInt(), properties["ratametri"] as? Int)
        assertEquals(0, properties["ratametri_desimaalit"] as? Int)
    }

    @Test
    fun `Track km position is returned correctly`() {
        val layoutContext = mainOfficialContext

        val geocodableTrack =
            insertGeocodableTrack(
                layoutContext = layoutContext,
                segments = listOf(segment(Point(0.0, 0.0), Point(3000.0, 0.0))),
                locationTrackType = LocationTrackType.TRAP,
            )

        val secondKmNumberXLocation = 2000.0 - 50

        val xPositionOnTrack = 2123.456
        val yPositionOnTrack = 0.0
        val yRequestDifference = 50.0

        val expectedKmNumber = 2
        val expectedTrackMeters = xPositionOnTrack - secondKmNumberXLocation
        val expectedTrackDecimals = 456

        listOf(Point(900.0, 0.0), Point(secondKmNumberXLocation, 0.0))
            .mapIndexed { index, kmPostLocation ->
                kmPost(
                    trackNumberId = geocodableTrack.trackNumber.id as IntId,
                    km = KmNumber(index + 1),
                    roughLayoutLocation = kmPostLocation,
                    draft = false,
                )
            }
            .forEach(layoutKmPostDao::save)

        val request =
            TestCoordinateToTrackAddressRequest(x = xPositionOnTrack, y = yPositionOnTrack + yRequestDifference)

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        val properties = featureCollection.features[0].properties

        assertEquals(xPositionOnTrack, ((properties?.get("x") as? Double)!!), 0.001)
        assertEquals(yPositionOnTrack, ((properties["y"] as? Double)!!), 0.001)
        assertEquals(yRequestDifference, ((properties["valimatka"] as? Double)!!), 0.001)

        assertEquals(expectedKmNumber, properties["ratakilometri"])
        assertEquals(expectedTrackMeters.toInt(), properties["ratametri"] as? Int)
        assertEquals(expectedTrackDecimals, properties["ratametri_desimaalit"] as? Int)
    }

    @Test
    fun `Search radius under supported range should result in an error`() {
        insertGeocodableTrack(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0, sade = 0.9)

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        val properties = featureCollection.features[0].properties

        assertContainsErrorMessage(
            "Pyyntö sisälsi pienemmän hakusäteen kuin sallittu minimiarvo (1.0).",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Search radius over supported range should result in an error`() {
        insertGeocodableTrack(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0, sade = 1000.1)

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        val properties = featureCollection.features[0].properties

        assertContainsErrorMessage(
            "Pyyntö sisälsi suuremman hakusäteen kuin sallittu maksimiarvo (1000.0).",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Invalid location track type should result in an error`() {
        insertGeocodableTrack(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0, sijaintiraide_tyyppi = "something")

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        val properties = featureCollection.features[0].properties

        assertContainsErrorMessage(
            "Pyyntö sisälsi virheellisen sijaintiraide_tyyppi-asetuksen. Sallitut arvot ovat pääraide, sivuraide, kujaraide, turvaraide.",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Invalid location track name should result in an error`() {
        insertGeocodableTrack(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0, sijaintiraide = "@")

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        val properties = featureCollection.features[0].properties

        assertContainsErrorMessage("Pyyntö sisälsi virheellisen sijaintiraide-asetuksen.", properties?.get("virheet"))
    }

    @Test
    fun `Invalid track number should result in an error`() {
        insertGeocodableTrack(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val request =
            TestCoordinateToTrackAddressRequest(
                x = 0.0,
                y = 0.0,
                ratanumero = "alsjkdhkajlshdljkahsdljkahsldjkhaslkjdhalkjsdhlkjashdjklasdhj",
            )

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)
        val properties = featureCollection.features[0].properties

        assertContainsErrorMessage("Pyyntö sisälsi virheellisen ratanumero-asetuksen.", properties?.get("virheet"))
    }

    @Test
    fun `Deleted track is not found`() {
        val track = insertGeocodableTrack(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
        testDBService.update(track.locationTrack.version!!) { t -> t.copy(state = LocationTrackState.DELETED) }

        val request = TestCoordinateToTrackAddressRequest(x = 0.0, y = 0.0)

        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)
        val properties = featureCollection.features[0].properties

        assertContainsErrorMessage(
            "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Reverse geocoded address should match the returned coordinate`() {
        val referenceLineSegments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        val trackNumberVersion =
            mainOfficialContext.createLayoutTrackNumberAndReferenceLine(alignment(referenceLineSegments))
        val trackNumberId = trackNumberVersion.id
        val trackNumber = trackNumberDao.fetch(trackNumberVersion)

        // Track is offset from the reference line and with a slightly different angle
        val trackStart = Point(-5.0, 1.0)
        val trackEnd = Point(5.0, 2.0)
        val trackSegments = listOf(segment(trackStart, trackEnd))
        val (track, _) =
            mainOfficialContext.insertAndFetch(locationTrackAndAlignment(trackNumberId, segments = trackSegments))

        // Seek a point that is offset from both the track and the reference line - perpendicular
        // from track point x=0 y=1.5
        val expectedTrackPoint = Point(0.0, 1.5)
        val searchPoint = Point(-0.1, 2.5)

        // Verify that the above comments about offsets hold true
        assertEquals(0.0, pointDistanceToLine(trackStart, trackEnd, expectedTrackPoint), 0.001)
        assertEquals(hypot(0.1, 1.0), pointDistanceToLine(trackStart, trackEnd, searchPoint), 0.001)

        val request = TestCoordinateToTrackAddressRequest(x = searchPoint.x, y = searchPoint.y)
        val featureCollection = api.fetchFeatureCollectionBatch(API_TRACK_ADDRESSES, request)

        val properties = featureCollection.features[0].properties!!
        assertEquals(trackNumber.number.toString(), properties["ratanumero"])
        assertEquals(track.name.toString(), properties["sijaintiraide"])

        // Verify that we got the expected coordinate
        assertEquals(expectedTrackPoint.x, ((properties["x"] as? Double)!!), 0.001)
        assertEquals(expectedTrackPoint.y, ((properties["y"] as? Double)!!), 0.001)
        assertEquals(
            pointDistanceToLine(trackStart, trackEnd, searchPoint),
            ((properties["valimatka"] as? Double)!!),
            0.001,
        )

        // The address should match the track point, as that's the coordinate that is returned
        val expectedAddress =
            geocodingService.getAddress(MainLayoutContext.official, trackNumberId, expectedTrackPoint)!!.first
        assertEquals(expectedAddress.kmNumber.number, properties["ratakilometri"])
        assertEquals(expectedAddress.meters.toInt(), properties["ratametri"] as? Int)
        assertEquals(
            expectedAddress.meters.let { it.remainder(BigDecimal.ONE).movePointRight(it.scale()).toInt() },
            properties["ratametri_desimaalit"] as? Int,
        )
    }

    private fun insertGeocodableTrack(
        layoutContext: TestLayoutContext = mainOfficialContext,
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        locationTrackName: String = "Test location track",
        locationTrackType: LocationTrackType = LocationTrackType.MAIN,
        segments: List<LayoutSegment> = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
    ): GeocodableTrack {
        val referenceLineId =
            layoutContext.insert(referenceLineAndAlignment(trackNumberId = trackNumberId, segments = segments)).id

        val locationTrackId =
            layoutContext
                .insert(
                    locationTrackAndAlignment(
                        trackNumberId = trackNumberId,
                        name = locationTrackName,
                        type = locationTrackType,
                        segments = segments,
                    )
                )
                .id

        return GeocodableTrack(
            layoutContext = layoutContext.context,
            trackNumber = trackNumberDao.get(layoutContext.context, trackNumberId)!!,
            referenceLine = referenceLineDao.get(layoutContext.context, referenceLineId)!!,
            locationTrack = locationTrackDao.get(layoutContext.context, locationTrackId)!!,
        )
    }
}
