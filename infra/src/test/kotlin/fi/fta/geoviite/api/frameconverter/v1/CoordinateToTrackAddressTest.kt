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
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.ui.UI_TEST_USER
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import java.util.UUID
import kotlin.test.assertEquals

const val API_URL = "/frame-converter/v1"
const val COORDINATE_TO_TRACK_ADDRESS_URL = "$API_URL/coordinate-to-track-address"

// Purposefully different data structure as the actual logic to imitate a user
private data class TestCoordinateToTrackAddressRequest(
    val tunniste: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val sade: Double? = null,
    val ratanumero: String? = null,
    val sijaintiraide: String? = null,
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
    fun `Invalid requests should result in HTTP 400`() {
        testApi.doGet(COORDINATE_TO_TRACK_ADDRESS_URL, HttpStatus.BAD_REQUEST)
        testApi.doPost(COORDINATE_TO_TRACK_ADDRESS_URL, null, HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Invalid requests with JSON should result in HTTP 400`() {
        val params = mapOf("json" to "")

        testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.BAD_REQUEST)
        testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Valid requests with request params should succeed`() {
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
    fun `Valid requests with JSON should succeed`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val request = TestCoordinateToTrackAddressRequest(
            x = 0.0,
            y = 0.0,
        )

        val params = mapOf("json" to mapper.writeValueAsString(request))

        testApi.doGetWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
        testApi.doPostWithParams(COORDINATE_TO_TRACK_ADDRESS_URL, params, HttpStatus.OK)
    }

    @Test
    fun `Requests with both request params and JSON should prioritize JSON`() {
        TODO()
    }

    @Test
    fun `Response feature should include identifier of the request if submitted`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val identifier = "some-identifier-${UUID.randomUUID()}"

        val params = mapOf(
            "json" to mapper.writeValueAsString(
                TestCoordinateToTrackAddressRequest(
                    tunniste = identifier,
                    x = 0.0,
                    y = 0.0,
                )
            )
        )

        val featureCollection = fetchFeatureCollection(COORDINATE_TO_TRACK_ADDRESS_URL, params)
        assertEquals(identifier, featureCollection.features[0].properties?.get("tunniste"), "key=tunniste")
    }

    @Test
    fun `Basic request should default to return data with responseSettings 1 and 10`() {
        val geocodableTrack = insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val yDifferenceToTargetLocation = 5.0

        val params = mapOf(
            "json" to mapper.writeValueAsString(
                TestCoordinateToTrackAddressRequest(
                    x = 0.0,
                    y = yDifferenceToTargetLocation,
                )
            )
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
            "sijaintiraide_tyyppi" to "sivuraide",
            "ratakilometri" to 0,
            "ratametri" to 10,
            "ratametri_desimaalit" to 0,
        )

        val featureCollection = fetchFeatureCollection(COORDINATE_TO_TRACK_ADDRESS_URL, params)

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
    fun `Request with empty responseSettings should not return any actual data`() {
        insertGeocodableTrack(
            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
        )

        val params = mapOf(
            "json" to mapper.writeValueAsString(
                TestCoordinateToTrackAddressRequest(
                    x = 0.0,
                    y = 0.0,
                    palautusarvot = emptyList(),
                )
            )
        )

        val featureCollection = fetchFeatureCollection(COORDINATE_TO_TRACK_ADDRESS_URL, params)

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)
        assertEquals(emptyMap<String, String>(), featureCollection.features[0].properties)
    }

    @Test
    fun `Filtering with radius works`() {
        TODO()
//        insertGeocodableTrack(
//            segments = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0)))
//        )
//
//        val identifier = "some-identifier-${UUID.randomUUID()}"
//
//        val params = mapOf(
//            "json" to mapper.writeValueAsString(
//                TestCoordinateToTrackAddressRequest(
//                    tunniste = identifier,
//                    x = 0.0,
//                    y = 0.0,
//                )
//            )
//        )
//
//        val featureCollection = fetchFeatureCollection(COORDINATE_TO_TRACK_ADDRESS_URL, params)
//        assertEquals(identifier, featureCollection.features[0].properties?.get("tunniste"), "key=tunniste")
    }

    @Test
    fun `Filtering with track number works`() {
        TODO()
    }

    @Test
    fun `Filtering with location track name works`() {
        TODO()
    }

    @Test
    fun `Filtering with location track type works`() {
        TODO()
    }

    @Test
    fun `Response output data setting 1 works`() {
        TODO()
    }

    @Test
    fun `Response output data setting 5 works`() {
        TODO()
    }

    @Test
    fun `Response output data setting 10 works`() {
        TODO()
    }

    @Test
    fun `Response output data setting combination works`() {
        TODO() // For example use 1, 5, 10 as the combination
    }

    private fun insertGeocodableTrack(
        layoutContext: TestLayoutContext = mainOfficialContext,
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        locationTrackName: String = "Test location track",
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
            .let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java)}
    }
}

