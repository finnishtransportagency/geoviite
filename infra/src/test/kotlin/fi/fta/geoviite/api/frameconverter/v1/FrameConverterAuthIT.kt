package fi.fta.geoviite.api.frameconverter.v1

import TestGeoJsonFeatureCollection
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestApi
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

private const val API_URI = "/rata-vkm/v1"

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class], properties = ["geoviite.skip-auth=false"])
@AutoConfigureMockMvc
class FrameConverterAuthIT @Autowired constructor(mockMvc: MockMvc) {
    private val mapper = ObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }

    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun `Unset api host should not work`() {
        val headers = HttpHeaders()

        fetchAuthorizedFeatureCollectionOrNull(headers, expectedStatus = HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `Empty api host should not work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "")

        fetchAuthorizedFeatureCollectionOrNull(headers, expectedStatus = HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `Unrecognized api host should not work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "asd.something.test")

        fetchAuthorizedFeatureCollectionOrNull(headers, expectedStatus = HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `Invalid api host should not work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "http://asd.example.test")

        fetchAuthorizedFeatureCollectionOrNull(headers, expectedStatus = HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `Public api access should work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "avoinapi.example.test")

        val featureCollection = fetchAuthorizedFeatureCollectionOrNull(headers)

        assertNotNull(featureCollection!!.features[0].properties?.get("virheet"))
    }

    @Test
    fun `Private api access should work`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "api.example.test")

        val featureCollection = fetchAuthorizedFeatureCollectionOrNull(headers)
        assertNotNull(featureCollection!!.features[0].properties?.get("virheet"))
    }

    private fun fetchAuthorizedFeatureCollectionOrNull(
        headers: HttpHeaders,
        apiUri: String = API_URI,
        expectedStatus: HttpStatus = HttpStatus.OK,
    ): TestGeoJsonFeatureCollection? {
        return testApi.doPostWithParams(apiUri, emptyMap(), expectedStatus, headers).let { body ->
            try {
                mapper.readValue(body, TestGeoJsonFeatureCollection::class.java)
            } catch (e: ValueInstantiationException) {
                null
            }
        }
    }
}
