import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackLayoutTestApiService
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
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
class ExtTestTrackLayoutV1
@Autowired
constructor(
    mockMvc: MockMvc,
    private val locationTrackService: LocationTrackService,
    private val extTestDataService: ExtApiTestDataServiceV1,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    // Valid OIDs are not required to be available for non-existing/OID format tests.
    private val assetEndpointOidErrorTests =
        listOf(
            api.locationTracks::getWithExpectedError,
            api.locationTracks::getGeometryWithExpectedError,
        )

    // Valid OIDs are not required to check for start/end track layout version errors,
    // but modifications APIs require the base comparison version as a parameter.
    private val assetEndpointModificationOidErrorTests =
        listOf(
            api.locationTracks::getModifiedWithExpectedError,
        )

    // Valid OIDs are required to check for track layout version errors.
    private val assetEndpointTrackLayoutVersionErrorTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getWithExpectedError,
            ::setupValidLocationTrack to api.locationTracks::getGeometryWithExpectedError,
        )

    // Valid OIDs are required to check for start/end track layout version errors.
    private val assetEndpointModificationTrackLayoutVersionErrorTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getModifiedWithExpectedError,
        )

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 400 if the OID is invalid format`() {
        val invalidOid = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST
        val validButEmptyPublication = extTestDataService.publishInMain()

        assetEndpointOidErrorTests.forEach { apiCall -> apiCall(invalidOid, emptyArray(), expectedStatus) }
        assetEndpointModificationOidErrorTests.forEach { apiCall ->
            apiCall(invalidOid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()), expectedStatus)
        }
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 404 if the OID is not found`() {
        val expectedStatus = HttpStatus.NOT_FOUND
        val nonExistingOid = someOid<Nothing>().toString()
        val validButEmptyPublication = extTestDataService.publishInMain()

        assetEndpointOidErrorTests.forEach { apiCall -> apiCall(nonExistingOid, emptyArray(), expectedStatus) }
        assetEndpointModificationOidErrorTests.forEach { apiCall ->
            apiCall(nonExistingOid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()), expectedStatus)
        }
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 400 if the track layout version is invalid format`() {
        val invalidTrackLayoutVersion = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST

        assetEndpointTrackLayoutVersionErrorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                val response = apiCall(oid, arrayOf("rataverkon_versio" to invalidTrackLayoutVersion), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset endpoints return HTTP 404 if the track layout version is not found`() {
        val validButNonExistingUuid = "00000000-0000-0000-0000-000000000000"
        val expectedStatus = HttpStatus.NOT_FOUND

        assetEndpointTrackLayoutVersionErrorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid, arrayOf("rataverkon_versio" to validButNonExistingUuid), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 400 if either the start or end track layout version is invalid format`() {
        val invalidTrackLayoutVersion = "asd"
        val validButNonExistingUuid = "00000000-0000-0000-0000-000000000000"

        val expectedStatus = HttpStatus.BAD_REQUEST

        assetEndpointModificationTrackLayoutVersionErrorTests.forEach { (oidSetup, apiCall) ->
            val oid = oidSetup().toString()

            apiCall(oid, arrayOf("alkuversio" to invalidTrackLayoutVersion), expectedStatus)
            apiCall(
                oid,
                arrayOf("alkuversio" to validButNonExistingUuid, "loppuversio" to invalidTrackLayoutVersion),
                expectedStatus,
            )
        }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 404 if either the start or end track layout version is not found`() {
        val emptyButExistingPublication = extTestDataService.publishInMain()
        val validButNonExistingUuid = "00000000-0000-0000-0000-000000000000"

        val expectedStatus = HttpStatus.NOT_FOUND

        assetEndpointModificationTrackLayoutVersionErrorTests.forEach { (oidSetup, apiCall) ->
            val oid = oidSetup().toString()

            apiCall(oid, arrayOf("alkuversio" to validButNonExistingUuid), expectedStatus)
            apiCall(
                oid,
                arrayOf(
                    "alkuversio" to emptyButExistingPublication.uuid.toString(),
                    "loppuversio" to validButNonExistingUuid,
                ),
                expectedStatus,
            )
        }
    }

    private fun setupValidLocationTrack(): Oid<LocationTrack> {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceWithOid(
                mainDraftContext,
                segments = listOf(segment),
            )

        val (track, geometry) =
            mainDraftContext
                .saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment))
                .let(locationTrackService::getWithGeometry)

        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, track.id as IntId, oid)
            }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(track.id as IntId),
        )

        return oid
    }
}
