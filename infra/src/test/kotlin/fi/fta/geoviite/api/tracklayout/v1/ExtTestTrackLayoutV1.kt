import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackLayoutTestApiService
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
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

    private val assetEndpoints =
        listOf(
            api.locationTracks::getWithExpectedError,
            api.locationTracks::getGeometryWithExpectedError,
            api.locationTracks::getModifiedWithExpectedError,
        )

    @Test
    fun `Ext api asset endpoints should return HTTP 400 if the OID is invalid format`() {
        val invalidOid = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST

        assetEndpoints.forEach { apiCall -> apiCall(invalidOid, emptyArray(), expectedStatus) }
    }

    @Test
    fun `Ext api asset endpoints return HTTP 404 if the track layout version is not found`() {
        val expectedStatus = HttpStatus.NOT_FOUND

        assetEndpoints.forEach { apiCall -> apiCall(invalidOid, emptyArray(), expectedStatus) }
    }
}
