import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackLayoutTestApiService
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
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
class ExtTestTrackLayoutV1
@Autowired
constructor(
    mockMvc: MockMvc,
    private val locationTrackService: LocationTrackService,
    private val extTestDataService: ExtApiTestDataServiceV1,
    @Autowired private val publicationService: PublicationService,
    service: PublicationService,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    private val errorTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getWithExpectedError,
            ::setupValidLocationTrack to api.locationTracks::getGeometryWithExpectedError,
        )

    private val modificationErrorTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getModifiedWithExpectedError,
        )

    private val noContentTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getWithEmptyBody,
            ::setupValidLocationTrack to api.locationTracks::getGeometryWithEmptyBody,
        )

    private val noContentModificationTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getModifiedWithEmptyBody,
        )

    private val modificationSuccessTests = listOf(::setupValidLocationTrack to api.locationTracks::getModified)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 400 if the OID is invalid format`() {
        val invalidOid = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST
        val validButEmptyPublication = extTestDataService.publishInMain()

        errorTests.forEach { (_, apiCall) -> apiCall(invalidOid, emptyArray(), expectedStatus) }
        modificationErrorTests.forEach { (_, apiCall) ->
            apiCall(invalidOid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()), expectedStatus)
        }
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 404 if the OID is not found`() {
        val expectedStatus = HttpStatus.NOT_FOUND
        val nonExistingOid = someOid<Nothing>().toString()
        val validButEmptyPublication = extTestDataService.publishInMain()

        errorTests.forEach { (_, apiCall) -> apiCall(nonExistingOid, emptyArray(), expectedStatus) }
        modificationErrorTests.forEach { (_, apiCall) ->
            apiCall(nonExistingOid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()), expectedStatus)
        }
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 400 if the track layout version is invalid format`() {
        val invalidTrackLayoutVersion = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST

        errorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                val response = apiCall(oid, arrayOf("rataverkon_versio" to invalidTrackLayoutVersion), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset endpoints return HTTP 404 if the track layout version is not found`() {
        val validButNonExistingUuid = "00000000-0000-0000-0000-000000000000"
        val expectedStatus = HttpStatus.NOT_FOUND

        errorTests
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

        modificationErrorTests.forEach { (oidSetup, apiCall) ->
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

        modificationErrorTests.forEach { (oidSetup, apiCall) ->
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

    @Test
    fun `Ext api asset endpoints should return HTTP 204 when the asset does not exist in the specified track layout version`() {
        val expectedStatus = HttpStatus.NO_CONTENT
        val validButEmptyPublication = extTestDataService.publishInMain()

        noContentTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid, arrayOf("rataverkon_versio" to validButEmptyPublication.uuid.toString()), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 204 when the asset has no modifications`() {
        val expectedStatus = HttpStatus.NO_CONTENT

        noContentModificationTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                val newestPublication =
                    publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, publicationUuid = null)

                apiCall(oid, arrayOf("alkuversio" to newestPublication.uuid.toString()), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 204 when the asset does not exist between track layout versions`() {
        val expectedStatus = HttpStatus.NO_CONTENT
        val firstPublication = extTestDataService.publishInMain()
        val secondPublication = extTestDataService.publishInMain()

        noContentModificationTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                // oidSetup call has added the asset and created a new publication: the asset exists, but not within the
                // publications before it.
                apiCall(
                    oid,
                    arrayOf(
                        "alkuversio" to firstPublication.uuid.toString(),
                        "loppuversio" to secondPublication.uuid.toString(),
                    ),
                    expectedStatus,
                )
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 200 when the asset has been created after the start track layout version`() {
        val validButEmptyPublication = extTestDataService.publishInMain()

        modificationSuccessTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                val response = apiCall(oid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()))
                assertEquals(oid.toString(), response.sijaintiraide.sijaintiraide_oid)
            }
    }

    private fun setupValidLocationTrack(): Oid<LocationTrack> {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment),
            )

        val trackId = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id

        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
            }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        return oid
    }
}
