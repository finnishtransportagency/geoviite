package fi.fta.geoviite.infra.jsonFormatting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.fta.geoviite.infra.TestApi
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "nodb", "backend")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class JsonFormattingTest @Autowired constructor(val mapper: ObjectMapper, mockMvc: MockMvc) {
    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun `Instants as response body work`() {
        assertEquals("\"2021-06-14T12:30:50Z\"", successResponse(Instant.ofEpochSecond(1623673850)))
        assertEquals("\"2021-06-14T12:30:50.999Z\"", successResponse(Instant.ofEpochMilli(1623673850999)))
    }

    @Test
    fun `Instants inside response body JSON work`() {
        assertEquals(
            """{"instant":"2021-06-14T12:30:50.999Z"}""",
            successResponse(InstantData(Instant.ofEpochMilli(1623673850999))),
        )
    }

    @Test
    fun `Instants in path arguments work`() {
        assertEquals(
            "\"1623673850000\"",
            testApi.doGet("/json-test-path/to-millis/2021-06-14T12:30:50Z", HttpStatus.OK),
        )
        assertEquals(
            "\"1623673850999\"",
            testApi.doGet("/json-test-path/to-millis/2021-06-14T12:30:50.999Z", HttpStatus.OK),
        )
    }

    @Test
    fun `Instants in return values work`() {
        assertEquals(
            "\"2021-06-14T12:30:50Z\"",
            testApi.doGet("/json-test-path/to-instant/1623673850000", HttpStatus.OK),
        )
        assertEquals(
            "\"2021-06-14T12:30:50.999Z\"",
            testApi.doGet("/json-test-path/to-instant/1623673850999", HttpStatus.OK),
        )
    }

    @Test
    fun `Null is read as default value`() {
        assertEquals(
            TestData("argument value 1", "argument value 2"),
            mapper.readValue<TestData>(
                """{
                "value1":"argument value 1",
                "value2":"argument value 2"
            }"""
                    .trimIndent()
            ),
        )
        assertEquals(TestData("argument value 1"), mapper.readValue<TestData>("""{"value1":"argument value 1"}"""))
    }

    @Test
    fun `Deserialization doesn't care about extra arguments`() {
        assertEquals(
            TestData("argument value 1", "argument value 2"),
            mapper.readValue<TestData>(
                """{
               "value1":"argument value 1", 
               "value2":"argument value 2", 
               "extraValue":"something useless"
            }"""
                    .trimIndent()
            ),
        )
    }

    private fun successResponse(instant: Any): String = testApi.response(instant)
}

data class TestData(val value1: String, val value2: String = "default value")

data class InstantData(val instant: Instant)
