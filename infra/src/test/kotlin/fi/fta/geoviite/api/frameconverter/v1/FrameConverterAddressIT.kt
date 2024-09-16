package fi.fta.geoviite.api.frameconverter.v1

import TestGeoJsonFeatureCollection
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestApi
import kotlin.test.assertNotNull
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
class FrameConverterAddressIT @Autowired constructor(mockMvc: MockMvc) : DBTestBase() {

    private val mapper = ObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }

    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun `All supported URL paths should work`() {
        listOf(
                "/rata-vkm",
                "/rata-vkm/v1",
                "/rata-vkm/dev",
                "/rata-vkm/dev/v1",

                // Trailing slashes should also work.
                "/rata-vkm/",
                "/rata-vkm/v1/",
                "/rata-vkm/dev/",
                "/rata-vkm/dev/v1/",
            )
            .forEach { uri ->
                mapOf(
                        "GET" to testApi.doGetWithParams(uri, mapOf(), HttpStatus.OK),
                        "POST" to testApi.doPostWithParams(uri, mapOf(), HttpStatus.OK),
                    )
                    .forEach { (method, request) ->
                        val featureCollection =
                            request.let { body -> mapper.readValue(body, TestGeoJsonFeatureCollection::class.java) }

                        assertNotNull(
                            featureCollection.features[0].properties?.get("virheet"),
                            "method=$method, uri=$uri",
                        )
                    }
            }
    }
}
