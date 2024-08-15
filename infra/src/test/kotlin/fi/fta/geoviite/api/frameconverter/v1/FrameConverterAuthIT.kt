package fi.fta.geoviite.api.frameconverter.v1

import TestGeoJsonFeatureCollection
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.InfraApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val API_URI = "/rata-vkm/v1"

@ActiveProfiles("dev", "test", "integration-api")
@SpringBootTest(
    classes = [InfraApplication::class],
    properties = [
        "geoviite.skip-auth=false"
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class FrameConverterAuthIT @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val restTemplate: TestRestTemplate
) {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `Unset api host should not work`() {
        val headers = HttpHeaders()

        val (status, _) = fetchFeatureCollection(headers)
        assertEquals(HttpStatus.UNAUTHORIZED, status)
    }

    @Test
    fun `Empty api host should not work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "")

        val (status, _) = fetchFeatureCollection(headers)
        assertEquals(HttpStatus.UNAUTHORIZED, status)
    }

    @Test
    fun `Unrecognized api host should not work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "asd.something.test")

        val (status, _) = fetchFeatureCollection(headers)
        assertEquals(HttpStatus.UNAUTHORIZED, status)
    }

    @Test
    fun `Invalid api host should not work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "http://asd.example.test")

        val (status, _) = fetchFeatureCollection(headers)
        assertEquals(HttpStatus.UNAUTHORIZED, status)
    }

    @Test
    fun `Public api access should work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "avoinapi.example.test")

        val (status, featureCollection) = fetchFeatureCollection(headers)

        assertEquals(HttpStatus.OK, status)
        assertNotNull(featureCollection!!.features[0].properties?.get("virheet"))
    }

    @Test
    fun `Private api access should work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "api.example.test")

        val (status, featureCollection) = fetchFeatureCollection(headers)

        assertEquals(HttpStatus.OK, status)
        assertNotNull(featureCollection!!.features[0].properties?.get("virheet"))
    }

    private fun fetchFeatureCollection(headers: HttpHeaders): Pair<HttpStatusCode, TestGeoJsonFeatureCollection?> {
        val url = "http://localhost:$port/$API_URI"
        val entity = HttpEntity<String>(null, headers)

        val response = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)

        return when (response.statusCode) {
            HttpStatus.OK ->
                response.statusCode to objectMapper.readValue(response.body, TestGeoJsonFeatureCollection::class.java)

            else -> response.statusCode to null
        }
    }
}
