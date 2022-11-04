package fi.fta.geoviite.infra.jsonFormatting

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.TestApi
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.time.Instant
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "nodb")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class JsonFormattingTest @Autowired constructor(
    mapper: ObjectMapper,
    mockMvc: MockMvc,
) {
    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun jacksonFormatsInstantCorrectlyInsideJson() {
        // TODO: JSON configuration
        // Object mapper configuration in "configureMessageConverters" of "WebConfiguration.kt"
        // does not have effect on serialization in tests, date format configuration just happens to be the same.
        assertEquals("\"2021-06-14T12:30:50Z\"", successResponse(Instant.ofEpochSecond(1623673850)))
        assertEquals("\"2021-06-14T12:30:50.999Z\"", successResponse(Instant.ofEpochMilli(1623673850999)))
    }

    @Test
    fun getWithInstantInArgumentSucceeds() {
        assertEquals("\"1623673850000\"",
            testApi.doGet("/json-test-path/to-millis/2021-06-14T12:30:50Z", HttpStatus.OK))
        assertEquals("\"1623673850999\"",
            testApi.doGet("/json-test-path/to-millis/2021-06-14T12:30:50.999Z", HttpStatus.OK))
    }

    @Test
    fun getWithInstantReturnValueSucceeds() {
        assertEquals("\"2021-06-14T12:30:50Z\"",
            testApi.doGet("/json-test-path/to-instant/1623673850000", HttpStatus.OK))
        assertEquals("\"2021-06-14T12:30:50.999Z\"",
            testApi.doGet("/json-test-path/to-instant/1623673850999", HttpStatus.OK))
    }

    private fun successResponse(instant: Instant): String = testApi.response(instant)
}
