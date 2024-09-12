package fi.fta.geoviite.api.frameconverter.v1

import TestGeoJsonFeatureCollection
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackNumber
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

private const val API_URL = "/rata-vkm/v1"

// Purposefully different data structure as the actual logic to imitate a user.
private data class TestTrackAddressToCoordinateRequest(
    val tunniste: String? = null,
    val ratanumero: String? = null,
    val ratakilometri: Int? = null,
    val ratametri: Int? = null,
    val sijaintiraide: String? = null,
    val sijaintiraide_tyyppi: String? = null,
    val palautusarvot: List<Int>? = null,
)

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class TrackAddressToCoordinateIT
@Autowired
constructor(
    mockMvc: MockMvc,
    val frameConverterTestDataService: FrameConverterTestDataServiceV1,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
    val locationTrackService: LocationTrackService,
) : DBTestBase() {

    private val mapper = ObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }

    val testApi = TestApi(mapper, mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBServiceIn?.clearAllTables()
    }

    @Test
    fun `Partially valid request should result in a descriptive error`() {
        val params = mapOf("ratanumero" to "123", "ratakilometri" to "0")

        val requests =
            listOf(
                testApi.doGetWithParams(API_URL, params, HttpStatus.OK),
                testApi.doPostWithParams(API_URL, params, HttpStatus.OK),
            )

        requests.forEach { request ->
            val featureCollection =
                request.let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java) }

            val errorString = featureCollection.features[0].properties?.get("virheet")!! as String
            assertTrue("Pyynnön ratanumeroa ei löydetty" in errorString)
            assertTrue("Pyyntö ei sisältänyt ratametriä" in errorString)
        }
    }

    @Test
    fun `Invalid request with JSON should result in an error`() {
        val params = mapOf("json" to "")

        testApi.doGetWithParams(API_URL, params, HttpStatus.BAD_REQUEST)
        testApi.doPostWithParams(API_URL, params, HttpStatus.BAD_REQUEST)
    }

    private fun assertSimpleFeatureCollection(featureCollection: TestGeoJsonFeatureCollection) {
        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)
    }

    @Test
    fun `Missing track kilometer in a request should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        frameConverterTestDataService.insertGeocodableTrack(trackNumberId = trackNumber.id as IntId)

        val request = TestTrackAddressToCoordinateRequest(ratametri = 0, ratanumero = trackNumberName)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertSimpleFeatureCollection(featureCollection)
        assertEquals("Pyyntö ei sisältänyt ratakilometriä.", featureCollection.features[0].properties?.get("virheet"))
    }

    //    TODO Enable after GVT-2757?
    //    @Test
    //    fun `Track kilometer under range in a request should result in an error`() {
    //        val layoutContext = mainOfficialContext
    //        val trackNumberName = testDBService.getUnusedTrackNumber().value
    //
    //        val trackNumber =
    // layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id
    //            .let { trackNumberId ->
    //                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
    //            }
    //
    //        frameConverterTestDataService.insertGeocodableTrack(trackNumberId = trackNumber.id as
    // IntId)
    //
    //        val request = TestTrackAddressToCoordinateRequest(
    //            ratakilometri = -1,
    //            ratametri = 0,
    //            ratanumero = trackNumberName,
    //        )
    //
    //        val featureCollection = fetchFeatureCollection(API_URL,
    // createJsonRequestParams(request))
    //
    //        assertSimpleFeatureCollection(featureCollection)
    //        assertEquals(
    //            "Pyyntö sisälsi virheellisen rataosoitteen (eli ratakilometri+ratemetri yhdistelmä
    // oli virheellinen).",
    //            featureCollection.features[0].properties?.get("virheet"),
    //        )
    //    }

    //    TODO Enable after GVT-2757?
    //    @Test
    //    fun `Track kilometer over range in a request should result in an error`() {
    //        val layoutContext = mainOfficialContext
    //        val trackNumberName = testDBService.getUnusedTrackNumber().value
    //
    //        val trackNumber =
    // layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id
    //            .let { trackNumberId ->
    //                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
    //            }
    //
    //        frameConverterTestDataService.insertGeocodableTrack(trackNumberId = trackNumber.id as
    // IntId)
    //
    //        val request = TestTrackAddressToCoordinateRequest(
    //            ratakilometri = 10000,
    //            ratametri = 0,
    //            ratanumero = trackNumberName,
    //        )
    //
    //        val featureCollection = fetchFeatureCollection(API_URL,
    // createJsonRequestParams(request))
    //
    //        assertSimpleFeatureCollection(featureCollection)
    //        assertEquals(
    //            "Pyyntö sisälsi virheellisen rataosoitteen (eli ratakilometri+ratemetri yhdistelmä
    // oli virheellinen)",
    //            featureCollection.features[0].properties?.get("virheet"),
    //        )
    //    }

    @Test
    fun `Missing track meter in a request should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        frameConverterTestDataService.insertGeocodableTrack(trackNumberId = trackNumber.id as IntId)

        val request = TestTrackAddressToCoordinateRequest(ratakilometri = 123, ratanumero = trackNumberName)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertSimpleFeatureCollection(featureCollection)
        assertEquals("Pyyntö ei sisältänyt ratametriä.", featureCollection.features[0].properties?.get("virheet"))
    }

    @Test
    fun `Track meter under range in a request should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        frameConverterTestDataService.insertGeocodableTrack(trackNumberId = trackNumber.id as IntId)

        val request =
            TestTrackAddressToCoordinateRequest(ratakilometri = 123, ratametri = -10001, ratanumero = trackNumberName)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertSimpleFeatureCollection(featureCollection)
        assertEquals(
            "Pyyntö sisälsi virheellisen rataosoitteen (eli ratakilometri+ratemetri yhdistelmä oli virheellinen).",
            featureCollection.features[0].properties?.get("virheet"),
        )
    }

    @Test
    fun `Track meter over range in a request should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        frameConverterTestDataService.insertGeocodableTrack(trackNumberId = trackNumber.id as IntId)

        val request =
            TestTrackAddressToCoordinateRequest(ratakilometri = 123, ratametri = 10000, ratanumero = trackNumberName)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertSimpleFeatureCollection(featureCollection)
        assertEquals(
            "Pyyntö sisälsi virheellisen rataosoitteen (eli ratakilometri+ratemetri yhdistelmä oli virheellinen).",
            featureCollection.features[0].properties?.get("virheet"),
        )
    }

    @Test
    fun `Location track filter should work`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value
        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val referenceLineId =
            layoutContext
                .insert(referenceLineAndAlignment(trackNumberId = trackNumber.id as IntId, segments = segments))
                .id

        val tracksUnderTest =
            (0..3).map { _ ->
                frameConverterTestDataService.insertGeocodableTrack(
                    trackNumberId = trackNumber.id as IntId,
                    referenceLineId = referenceLineId,
                    segments = segments,
                )
            }

        tracksUnderTest.forEach { trackUnderTest ->
            val request =
                TestTrackAddressToCoordinateRequest(
                    ratakilometri = 0,
                    ratametri = 500,
                    ratanumero = trackNumberName,
                    sijaintiraide = trackUnderTest.locationTrack.name.toString(),
                )

            val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))
            val properties = featureCollection.features[0].properties

            assertSimpleFeatureCollection(featureCollection)
            assertEquals(trackUnderTest.locationTrack.name.toString(), properties?.get("sijaintiraide"))
        }
    }

    @Test
    fun `Location track type filter should work`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        val referenceLineId =
            layoutContext
                .insert(referenceLineAndAlignment(trackNumberId = trackNumber.id as IntId, segments = segments))
                .id

        val tracksUnderTest =
            listOf(
                    "pääraide" to LocationTrackType.MAIN,
                    "sivuraide" to LocationTrackType.SIDE,
                    "kujaraide" to LocationTrackType.CHORD,
                    "turvaraide" to LocationTrackType.TRAP,
                )
                .map { (locationTrackTypeName, locationTrackType) ->
                    val track =
                        frameConverterTestDataService.insertGeocodableTrack(
                            trackNumberId = trackNumber.id as IntId,
                            referenceLineId = referenceLineId,
                            locationTrackType = locationTrackType,
                            segments = segments,
                        )

                    locationTrackTypeName to track
                }

        tracksUnderTest.forEach { (locationTrackTypeName, trackUnderTest) ->
            val request =
                TestTrackAddressToCoordinateRequest(
                    ratakilometri = 0,
                    ratametri = 500,
                    ratanumero = trackNumberName,
                    sijaintiraide_tyyppi = locationTrackTypeName,
                )

            val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))
            val properties = featureCollection.features[0].properties

            assertSimpleFeatureCollection(featureCollection)

            assertEquals(trackUnderTest.locationTrack.name.toString(), properties?.get("sijaintiraide"))
            assertEquals(locationTrackTypeName, properties?.get("sijaintiraide_tyyppi"))
        }
    }

    @Test
    fun `Request with matching track number but without any matching tracks should succeed but return error feature`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        // Valid track number which can be geocoded, but no location tracks use it.
        layoutTrackNumberDao
            .insert(trackNumber(TrackNumber(trackNumberName)))
            .id
            .let { trackNumberId -> layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!! }
            .let { trackNumber ->
                layoutContext
                    .insert(
                        referenceLineAndAlignment(
                            trackNumberId = trackNumber.id as IntId,
                            segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0))),
                        )
                    )
                    .id
            }

        val request =
            TestTrackAddressToCoordinateRequest(ratakilometri = 0, ratametri = 0, ratanumero = trackNumberName)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))
        val properties = featureCollection.features[0].properties

        assertSimpleFeatureCollection(featureCollection)
        assertEquals("Annetun (alku)pisteen parametreilla ei löytynyt tietoja.", properties?.get("virheet"))
    }

    @Test
    fun `Request matching multiple track addresses should return coordinates for all of them`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val referenceLineSegments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        val amountOfLocationTracks = 4
        val positionedTrackSegments =
            (1..amountOfLocationTracks).map { index ->
                listOf(segment(Point(0.0, index * 100.0), Point(1000.0, index * 100.0)))
            }

        val referenceLineId =
            layoutContext
                .insert(
                    referenceLineAndAlignment(trackNumberId = trackNumber.id as IntId, segments = referenceLineSegments)
                )
                .id

        val tracksUnderTest =
            positionedTrackSegments.map { trackSegments ->
                frameConverterTestDataService.insertGeocodableTrack(
                    layoutContext = layoutContext,
                    trackNumberId = trackNumber.id as IntId,
                    referenceLineId = referenceLineId,
                    segments = trackSegments,
                )
            }

        val request =
            TestTrackAddressToCoordinateRequest(ratakilometri = 0, ratametri = 500, ratanumero = trackNumberName)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))
        assertEquals(amountOfLocationTracks, featureCollection.features.size)

        val locationTrackNamesUnderTest = tracksUnderTest.map { track -> track.locationTrack.name.toString() }
        val locationTrackNamesReturned =
            featureCollection.features.map { feature -> feature.properties?.get("sijaintiraide") }

        locationTrackNamesUnderTest.forEach { trackNameUnderTest ->
            assertTrue(
                trackNameUnderTest in locationTrackNamesReturned,
                "track name $trackNameUnderTest not found in $locationTrackNamesReturned",
            )
        }
    }

    @Test
    fun `Request matching the address of some location tracks should succeed and return data only for the matches`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value
        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val referenceLineSegments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))
        val referenceLineId =
            layoutContext
                .insert(
                    referenceLineAndAlignment(trackNumberId = trackNumber.id as IntId, segments = referenceLineSegments)
                )
                .id

        val tracksUnderTest =
            listOf(
                    listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0))), // Entire length of refline
                    listOf(segment(Point(200.0, 0.0), Point(800.0, 0.0))), // Shorten than refline from start/end
                    listOf(segment(Point(600.0, 0.0), Point(1000.0, 0.0))), // Shouldn't be in the response
                    listOf(segment(Point(0.0, 0.0), Point(499.1, 0.0))), // Shouldn't be in the response
                    listOf(segment(Point(100.0, 0.0), Point(501.0, 0.0))), // Should be in the response
                )
                .map { trackSegments ->
                    frameConverterTestDataService.insertGeocodableTrack(
                        layoutContext = layoutContext,
                        trackNumberId = trackNumber.id as IntId,
                        referenceLineId = referenceLineId,
                        segments = trackSegments,
                    )
                }

        val request =
            TestTrackAddressToCoordinateRequest(ratakilometri = 0, ratametri = 500, ratanumero = trackNumberName)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))
        assertEquals(3, featureCollection.features.size)

        assertEquals(
            tracksUnderTest[0].locationTrack.name.toString(),
            featureCollection.features[0].properties?.get("sijaintiraide"),
        )

        assertEquals(
            tracksUnderTest[1].locationTrack.name.toString(),
            featureCollection.features[1].properties?.get("sijaintiraide"),
        )

        assertEquals(
            tracksUnderTest[4].locationTrack.name.toString(),
            featureCollection.features[2].properties?.get("sijaintiraide"),
        )
    }

    @Test
    fun `Valid multi request with some matches should partially succeed`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))
        val segments2 = listOf(segment(Point(0.0, 100.0), Point(1000.0, 100.0)))

        val referenceLineId =
            layoutContext
                .insert(referenceLineAndAlignment(trackNumberId = trackNumber.id as IntId, segments = segments))
                .id

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            referenceLineId = referenceLineId,
            segments = segments,
        )

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            referenceLineId = referenceLineId,
            segments = segments2,
        )

        val requests =
            listOf(
                // Should not be geocodable -> error feature.
                TestTrackAddressToCoordinateRequest(
                    tunniste = "second-${UUID.randomUUID()}",
                    ratakilometri = 0,
                    ratametri = 1200,
                    ratanumero = trackNumberName,
                ),

                // Should match two tracks -> 2 result features.
                TestTrackAddressToCoordinateRequest(
                    tunniste = "first-${UUID.randomUUID()}",
                    ratakilometri = 0,
                    ratametri = 500,
                    ratanumero = trackNumberName,
                ),

                // Shouldn't match any track numbers -> error feature.
                TestTrackAddressToCoordinateRequest(
                    tunniste = "third-${UUID.randomUUID()}",
                    ratakilometri = 0,
                    ratametri = 1200,
                    ratanumero = UUID.randomUUID().toString(),
                ),
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(requests))

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(4, featureCollection.features.size)

        assertEquals(requests[0].tunniste, featureCollection.features[0].properties?.get("tunniste"))
        assertEquals(
            "Annetun (alku)pisteen parametreilla ei löytynyt tietoja.",
            featureCollection.features[0].properties?.get("virheet"),
        )

        assertEquals(requests[1].tunniste, featureCollection.features[1].properties?.get("tunniste"))
        assertNull(featureCollection.features[1].properties?.get("virheet"))

        assertEquals(requests[1].tunniste, featureCollection.features[2].properties?.get("tunniste"))
        assertNull(featureCollection.features[2].properties?.get("virheet"))

        assertEquals(requests[2].tunniste, featureCollection.features[3].properties?.get("tunniste"))
        assertEquals(
            "Pyyntö sisälsi virheellisen ratanumero-asetuksen.",
            featureCollection.features[3].properties?.get("virheet"),
        )
    }

    @Test
    fun `Valid single request using request params should succeed`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            segments = segments,
        )

        val params = mapOf("ratanumero" to trackNumberName, "ratakilometri" to "0", "ratametri" to "400")

        testApi.doGetWithParams(API_URL, params, HttpStatus.OK)
        testApi.doPostWithParams(API_URL, params, HttpStatus.OK)
    }

    @Test
    fun `Valid single request with JSON should succeed`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(ratanumero = trackNumberName, ratakilometri = 0, ratametri = 400)

        testApi.doGetWithParams(API_URL, createJsonRequestParams(request), HttpStatus.OK)
        testApi.doPostWithParams(API_URL, createJsonRequestParams(request), HttpStatus.OK)
    }

    @Test
    fun `Valid JSON request with multiple requests should fully succeed with both GET and POST method types`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            segments = segments,
        )

        val requests =
            (0..1000 step 100).map { trackMeter ->
                TestTrackAddressToCoordinateRequest(
                    ratanumero = trackNumberName,
                    ratakilometri = 0,
                    ratametri = trackMeter,
                )
            }

        listOf(
                testApi.doGetWithParams(API_URL, createJsonRequestParams(requests), HttpStatus.OK).let { body ->
                    mapper.readValue(body, TestGeoJsonFeatureCollection::class.java)
                },
                testApi.doPostWithParams(API_URL, createJsonRequestParams(requests), HttpStatus.OK).let { body ->
                    mapper.readValue(body, TestGeoJsonFeatureCollection::class.java)
                },
            )
            .forEach { featureCollection ->
                assertEquals(featureCollection.features.size, requests.size)

                featureCollection.features.forEach { feature -> assertNull(feature.properties?.get("virheet")) }
            }
    }

    @Test
    fun `Response feature should include identifier of the request if submitted`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
            layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
        }

        val identifiers = listOf("some-identifier-${UUID.randomUUID()}", "some-identifier-${UUID.randomUUID()}")

        val requests =
            listOf(
                TestTrackAddressToCoordinateRequest(
                    tunniste = identifiers[0],
                    ratakilometri = 0,
                    ratametri = 500,
                    ratanumero = trackNumberName,
                ),
                TestTrackAddressToCoordinateRequest(
                    tunniste = identifiers[1],
                    ratakilometri = 0,
                    ratametri = 500,
                    ratanumero = trackNumberName,
                ),
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(requests))

        assertEquals(identifiers[0], featureCollection.features[0].properties?.get("tunniste"), "key=tunniste")
        assertEquals(identifiers[1], featureCollection.features[1].properties?.get("tunniste"), "key=tunniste")
    }

    @Test
    fun `Basic request should default to return data with responseSettings 1 and 10`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        val geocodableTrack =
            frameConverterTestDataService.insertGeocodableTrack(
                trackNumberId = trackNumber.id as IntId,
                segments = segments,
                locationTrackType = LocationTrackType.SIDE,
            )

        val request =
            TestTrackAddressToCoordinateRequest(ratanumero = trackNumberName, ratakilometri = 0, ratametri = 400)

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        val expectedProperties =
            mapOf(
                "x" to 400.0,
                "y" to 0.0,
                "valimatka" to 0.0,
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
                "sijaintiraide_tyyppi" to "sivuraide",
                "ratakilometri" to 0,
                "ratametri" to 400,
                "ratametri_desimaalit" to 0,
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
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 400,
                palautusarvot = listOf(), // The responseSettings under test.
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertEquals("FeatureCollection", featureCollection.type)
        assertEquals(1, featureCollection.features.size)

        assertEquals("Feature", featureCollection.features[0].type)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(emptyList<Double>(), featureCollection.features[0].geometry?.coordinates)
        assertEquals(emptyMap<String, String>(), featureCollection.features[0].properties)
    }

    @Test
    fun `Response output data setting 1 works`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 400,
                palautusarvot = listOf(1), // The responseSettings under test.
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)
        assertEquals(0, (featureCollection.features[0].geometry?.coordinates as List<*>).size)

        val properties = featureCollection.features[0].properties!!

        assertEquals(400.0, properties.get("x"))
        assertEquals(0.0, properties.get("y"))
        assertEquals(0.0, properties.get("valimatka"))

        assertNullDetailedProperties(properties)
    }

    @Test
    fun `Response output data setting 5 works`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 400,
                palautusarvot = listOf(5), // The responseSettings under test.
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)

        val coordinateList = (featureCollection.features[0].geometry?.coordinates as List<*>)
        assertEquals(2, coordinateList.size)

        val coordinatesOnTrack = coordinateList.map { value -> value as Double }
        assertEquals(400.0, coordinatesOnTrack[0])
        assertEquals(0.0, coordinatesOnTrack[1])

        val properties = featureCollection.features[0].properties!!

        assertNullSimpleProperties(properties)
        assertNullDetailedProperties(properties)
    }

    @Test
    fun `Response output data setting 10 works`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        val geocodableTrack =
            frameConverterTestDataService.insertGeocodableTrack(
                trackNumberId = trackNumber.id as IntId,
                locationTrackType = LocationTrackType.CHORD,
                segments = segments,
            )

        val trackDescription =
            locationTrackService
                .getFullDescription(
                    layoutContext = geocodableTrack.layoutContext,
                    locationTrack = geocodableTrack.locationTrack,
                    lang = LocalizationLanguage.FI,
                )
                .toString()

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 300,
                palautusarvot = listOf(10), // The responseSettings under test.
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

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
        assertEquals(300, properties["ratametri"] as Int)
        assertEquals(0, properties["ratametri_desimaalit"] as Int)
    }

    @Test
    fun `Response output data setting combination works`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        val geocodableTrack =
            frameConverterTestDataService.insertGeocodableTrack(
                trackNumberId = trackNumber.id as IntId,
                locationTrackType = LocationTrackType.TRAP,
                segments = segments,
            )

        val trackDescription =
            locationTrackService
                .getFullDescription(
                    layoutContext = geocodableTrack.layoutContext,
                    locationTrack = geocodableTrack.locationTrack,
                    lang = LocalizationLanguage.FI,
                )
                .toString()

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 275,
                palautusarvot = listOf(10, 1, 5), // The responseSettings under test.
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        assertEquals(1, featureCollection.features.size)
        assertEquals("Point", featureCollection.features[0].geometry?.type)

        val coordinateList = (featureCollection.features[0].geometry?.coordinates as List<*>)
        assertEquals(2, coordinateList.size)

        val coordinatesOnTrack = coordinateList.map { value -> value as Double }
        assertEquals(275.0, coordinatesOnTrack[0])
        assertEquals(0.0, coordinatesOnTrack[1])

        val properties = featureCollection.features[0].properties

        assertEquals(275.0, ((properties?.get("x") as? Double)!!), 0.001)
        assertEquals(0.0, ((properties["y"] as? Double)!!), 0.001)
        assertEquals(0.0, ((properties["valimatka"] as? Double)!!), 0.001)

        assertEquals(geocodableTrack.trackNumber.number.toString(), properties["ratanumero"])
        assertEquals(geocodableTrack.locationTrack.name.toString(), properties["sijaintiraide"])
        assertEquals(trackDescription, properties["sijaintiraide_kuvaus"])
        assertEquals("turvaraide", properties["sijaintiraide_tyyppi"])
        assertEquals(0, properties["ratakilometri"])
        assertEquals(request.ratametri, properties["ratametri"] as? Int)
        assertEquals(0, properties["ratametri_desimaalit"] as? Int)
    }

    @Test
    fun `Invalid responseSettings should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            locationTrackType = LocationTrackType.CHORD,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 275,
                palautusarvot = listOf(3), // The responseSettings under test.
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        val properties = featureCollection.features[0].properties

        assertEquals(
            "Pyyntö sisälsi virheellisen palautusarvo-asetuksen. Sallitut arvot ovat (1, 5, 10).",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Invalid location track type should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            locationTrackType = LocationTrackType.CHORD,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 275,
                sijaintiraide_tyyppi = "something",
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        val properties = featureCollection.features[0].properties

        assertEquals(
            "Pyyntö sisälsi virheellisen sijaintiraide_tyyppi-asetuksen. Sallitut arvot ovat pääraide, sivuraide, kujaraide, turvaraide.",
            properties?.get("virheet"),
        )
    }

    @Test
    fun `Invalid location track name should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            locationTrackType = LocationTrackType.CHORD,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = trackNumberName,
                ratakilometri = 0,
                ratametri = 275,
                sijaintiraide = "@",
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        val properties = featureCollection.features[0].properties

        assertEquals("Pyyntö sisälsi virheellisen sijaintiraide-asetuksen.", properties?.get("virheet"))
    }

    @Test
    fun `Invalid track number should result in an error`() {
        val layoutContext = mainOfficialContext
        val trackNumberName = testDBService.getUnusedTrackNumber().value

        val trackNumber =
            layoutTrackNumberDao.insert(trackNumber(TrackNumber(trackNumberName))).id.let { trackNumberId ->
                layoutTrackNumberDao.get(layoutContext.context, trackNumberId)!!
            }

        val segments = listOf(segment(Point(0.0, 0.0), Point(1000.0, 0.0)))

        frameConverterTestDataService.insertGeocodableTrack(
            trackNumberId = trackNumber.id as IntId,
            locationTrackType = LocationTrackType.CHORD,
            segments = segments,
        )

        val request =
            TestTrackAddressToCoordinateRequest(
                ratanumero = "alsjkdhkajlshdljkahsdljkahsldjkhaslkjdhalkjsdhlkjashdjklasdhj",
                ratakilometri = 0,
                ratametri = 275,
            )

        val featureCollection = fetchFeatureCollection(API_URL, createJsonRequestParams(request))

        val properties = featureCollection.features[0].properties

        assertEquals("Pyyntö sisälsi virheellisen ratanumero-asetuksen.", properties?.get("virheet"))
    }

    // TODO Pull to somewhere else
    private fun fetchFeatureCollection(url: String, params: Map<String, String>): TestGeoJsonFeatureCollection {
        return testApi.doPostWithParams(url, params, HttpStatus.OK).let { body ->
            mapper.readValue(body, TestGeoJsonFeatureCollection::class.java)
        }
    }

    private fun createJsonRequestParams(request: TestTrackAddressToCoordinateRequest): Map<String, String> {
        return mapOf("json" to mapper.writeValueAsString(listOf(request)))
    }

    private fun createJsonRequestParams(requests: List<TestTrackAddressToCoordinateRequest>): Map<String, String> {
        return mapOf("json" to mapper.writeValueAsString(requests))
    }
}
