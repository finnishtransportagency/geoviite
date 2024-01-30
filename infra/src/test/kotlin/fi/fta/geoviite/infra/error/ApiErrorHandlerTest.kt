package fi.fta.geoviite.infra.error

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.hello.ErrorTestBody
import fi.fta.geoviite.infra.hello.ErrorTestResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "nodb")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ApiErrorHandlerTest @Autowired constructor(
    mapper: ObjectMapper,
    mockMvc: MockMvc,
) {
    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun getInvalidIs404() {
        testApi.assertErrorResult(
            testApi.doGet("/no-such-path", NOT_FOUND),
            "No handler found: GET /no-such-path",
        )
    }

    @Test
    fun getInvalidDoubleSlashIs404() {
        testApi.assertErrorResult(
            testApi.doGet("/no////such//path", NOT_FOUND),
            "No handler found: GET /no/such/path",
        )
    }

    @Test
    fun correctArgumentIs200() {
        assertEquals(successResponse(), testApi.doGet("/error-test-param?param=1", OK))
    }

    @Test
    fun missingArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test-param", BAD_REQUEST),
            "Missing parameter: param of type ErrorTestParam",
        )
    }

    @Test
    fun unparseableArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test-param?param=asdf", BAD_REQUEST),
            "Argument type mismatch: param (type ErrorTestParam) method 'requestWithParam' parameter 0",
            "Conversion failed for value \"asdf\": [String] -> [ErrorTestParam]",
        )
    }

    @Test
    fun invalidArgumentIs400() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test-param?param=0", BAD_REQUEST),
            "Argument type mismatch: param (type ErrorTestParam) method 'requestWithParam' parameter 0",
            "Conversion failed for value \"0\": [String] -> [ErrorTestParam]",
            "Input validation failed: ErrorTestParam value too small",
        )
    }

    @Test
    fun correctPathVariableIs200() {
        assertEquals(successResponse(), testApi.doGet("/error-test-path/1", OK))
    }

    @Test
    fun missingPathVariableIs404() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test-path", NOT_FOUND),
            "No handler found: GET /error-test-path",
        )
    }

    @Test
    fun unparseablePathVariableIs400() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test-path/asdf", BAD_REQUEST),
            "Argument type mismatch: variable (type ErrorTestParam) method 'requestWithPathVariable' parameter 0",
            "Conversion failed for value \"asdf\": [String] -> [ErrorTestParam]",
        )
    }

    @Test
    fun invalidPathVariableIs400() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test-path/0", BAD_REQUEST),
            "Argument type mismatch: variable (type ErrorTestParam) method 'requestWithPathVariable' parameter 0",
            "Conversion failed for value \"0\": [String] -> [ErrorTestParam]",
            "Input validation failed: ErrorTestParam value too small",
        )
    }

    @Test
    fun correctBodyIs200() {
        assertEquals(successResponse(), testApi.doPost("/error-test-body", ErrorTestBody("name", 1), OK))
    }

    @Test
    fun missingBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPost("/error-test-body", null, BAD_REQUEST),
            "Request body not readable",
        )
    }

    @Test
    fun unparseableBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPost("/error-test-body", Pair("something", "invalid"), BAD_REQUEST),
            "Request body not readable",
        )
    }

    @Test
    fun invalidBodyIs400() {
        testApi.assertErrorResult(
            testApi.doPostWithString("/error-test-body", "{\"name\":\"name\",\"value\":0}", BAD_REQUEST),
            "Request body not readable",
            "Failed to instantiate Lfi/fta/geoviite/infra/hello/ErrorTestBody;",
            "Input validation failed: ErrorTestBody value too small",
        )
    }

    @Test
    fun unhandledExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test/illegal", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    @Test
    fun unhandledJavaExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test/illegal-java", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    @Test
    fun clientExceptionIs400() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test/client", BAD_REQUEST),
            "Input validation failed: Client error",
        )
    }

    @Test
    fun serverExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test/server", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    @Test
    fun serverExceptionWrappedInClientExceptionIs500() {
        testApi.assertErrorResult(
            testApi.doGet("/error-test/wrapped", INTERNAL_SERVER_ERROR),
            "Internal Server Error",
        )
    }

    private fun successResponse(): String = testApi.response(ErrorTestResponse())
}
