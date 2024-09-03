package fi.fta.geoviite.infra.error

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.hello.ERROR_TEST_URL
import fi.fta.geoviite.infra.hello.ErrorTestBody
import fi.fta.geoviite.infra.hello.ErrorTestResponse
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "nodb", "backend")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ApiErrorHandlerTest @Autowired constructor(mapper: ObjectMapper, mockMvc: MockMvc) {
    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun getInvalidIs404() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/no-such-path", NOT_FOUND),
            "No handler found: GET $ERROR_TEST_URL/no-such-path",
        )
    }

    @Test
    fun getInvalidDoubleSlashIs404() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/no////such//path", NOT_FOUND),
            "No handler found: GET $ERROR_TEST_URL/no/such/path",
        )
    }

    @Test
    fun correctArgumentIs200() {
        assertEquals(successResponse(), testApi.doGet("$ERROR_TEST_URL/param?param=1", OK))
    }

    @Test
    fun missingArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/param", BAD_REQUEST),
            "Missing parameter: param of type ErrorTestParam",
        )
    }

    @Test
    fun unparseableArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/param?param=asdf", BAD_REQUEST),
            "Argument type mismatch: param (type ErrorTestParam) method 'requestWithParam' parameter 0",
            "Conversion failed for value \"asdf\": [String] -> [ErrorTestParam]",
        )
    }

    @Test
    fun invalidArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/param?param=0", BAD_REQUEST),
            "Argument type mismatch: param (type ErrorTestParam) method 'requestWithParam' parameter 0",
            "Conversion failed for value \"0\": [String] -> [ErrorTestParam]",
            "Input validation failed: ErrorTestParam value too small",
        )
    }

    @Test
    fun correctPathVariableIs200() {
        assertEquals(successResponse(), testApi.doGet("$ERROR_TEST_URL/path/1", OK))
    }

    @Test
    fun missingPathVariableIs404() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/path", NOT_FOUND),
            "No handler found: GET $ERROR_TEST_URL/path",
        )
    }

    @Test
    fun unparseablePathVariableIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/path/asdf", BAD_REQUEST),
            "Argument type mismatch: variable (type ErrorTestParam) method 'requestWithPathVariable' parameter 0",
            "Conversion failed for value \"asdf\": [String] -> [ErrorTestParam]",
        )
    }

    @Test
    fun invalidPathVariableIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/path/0", BAD_REQUEST),
            "Argument type mismatch: variable (type ErrorTestParam) method 'requestWithPathVariable' parameter 0",
            "Conversion failed for value \"0\": [String] -> [ErrorTestParam]",
            "Input validation failed: ErrorTestParam value too small",
        )
    }

    @Test
    fun correctBodyIs200() {
        assertEquals(successResponse(), testApi.doPost("$ERROR_TEST_URL/body", ErrorTestBody("name", 1), OK))
    }

    @Test
    fun missingBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPost("$ERROR_TEST_URL/body", null, BAD_REQUEST),
            "Request body not readable",
        )
    }

    @Test
    fun unparseableBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPost("$ERROR_TEST_URL/body", Pair("something", "invalid"), BAD_REQUEST),
            "Request body not readable",
        )
    }

    @Test
    fun invalidBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPostWithString("$ERROR_TEST_URL/body", "{\"name\":\"name\",\"value\":0}", BAD_REQUEST),
            "Request body not readable",
            "Failed to instantiate Lfi/fta/geoviite/infra/hello/ErrorTestBody;",
            "Input validation failed: ErrorTestBody value too small",
        )
    }

    @Test
    fun unhandledExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/illegal", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    @Test
    fun unhandledJavaExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/illegal-java", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    @Test
    fun clientExceptionIs400() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/client", BAD_REQUEST),
            "Input validation failed: Client error",
        )
    }

    @Test
    fun serverExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/server", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    @Test
    fun serverExceptionWrappedInClientExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("$ERROR_TEST_URL/wrapped", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    private fun successResponse(): String = testApi.response(ErrorTestResponse())
}
