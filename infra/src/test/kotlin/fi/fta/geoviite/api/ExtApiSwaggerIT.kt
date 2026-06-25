package fi.fta.geoviite.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestApi
import io.swagger.v3.parser.OpenAPIV3Parser
import jakarta.servlet.DispatcherType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api", "ext-api-dev-swagger")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtApiSwaggerIT @Autowired constructor(val mockMvc: MockMvc) {
    private val mapper = ObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun `valid openapi`() {
        val openApiJson = testApi.doGet("/v3/api-docs/geoviite-dev", HttpStatus.OK, DispatcherType.FORWARD)

        val result = OpenAPIV3Parser().readContents(openApiJson, null, null)

        if (result.messages != null && result.messages.isNotEmpty()) {
            result.messages.forEach { System.err.println(it) }
            fail { "Validation failed with ${result.messages.size} errors" }
        }
    }
}
