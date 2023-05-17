package fi.fta.geoviite.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.fta.geoviite.infra.error.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.isEqualTo
import kotlin.test.assertTrue

class TestApi(val mapper: ObjectMapper, val mockMvc: MockMvc) {

    fun doGet(url: String, expectedStatus: HttpStatus): String {
        return doGet(MockMvcRequestBuilders.get(url), expectedStatus);
    }

    fun doGet(requestBuilder: RequestBuilder, expectedStatus: HttpStatus): String {
        return mockMvc
            .perform(requestBuilder)
            .andExpect(status().isEqualTo(expectedStatus.value()))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andReturn().response.contentAsString
    }

    fun doPost(url: String, body: Any?, expectedStatus: HttpStatus): String {
        val bodyString = body?.let { b -> mapper.writeValueAsString(b) }
        return doPostWithString(url, bodyString, expectedStatus)
    }

    fun doPostWithString(url: String, body: String?, expectedStatus: HttpStatus): String {
        val request = MockMvcRequestBuilders.post(url).contentType(APPLICATION_JSON)
        return mockMvc
            .perform(if (body != null) request.content(body) else request)
            .andExpect(status().isEqualTo(expectedStatus.value()))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andReturn().response.contentAsString
    }

    fun doPut(url: String, body: Any?, expectedStatus: HttpStatus): String {
        val bodyString = body?.let { b -> mapper.writeValueAsString(b) }
        return doPutWithString(url, bodyString, expectedStatus)
    }

    fun doPutWithString(url: String, body: String?, expectedStatus: HttpStatus): String {
        val request = MockMvcRequestBuilders.put(url).contentType(APPLICATION_JSON)
        return mockMvc
            .perform(if (body != null) request.content(body) else request)
            .andExpect(status().isEqualTo(expectedStatus.value()))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andReturn().response.contentAsString
    }

    fun assertErrorResult(result: String, vararg messages: String) {
        val parsed = mapper.readValue<ApiErrorResponse>(result)
        for (message in messages) {
            assertTrue(parsed.messageRows.contains(message),
                "Response should contain \"$message\" actual=${parsed.messageRows}")
        }
    }

    fun response(responseObject: Any): String = mapper.writeValueAsString(responseObject)
}
