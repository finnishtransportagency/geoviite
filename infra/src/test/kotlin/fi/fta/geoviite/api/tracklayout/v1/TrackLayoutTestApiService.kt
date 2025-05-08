package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.TestApi
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc

class TrackLayoutTestApiService(mockMvc: MockMvc) {
    val mapper = ObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApi = TestApi(mapper, mockMvc)

    inline fun <reified T> get(
        url: String,
        params: Map<String, Any> = emptyMap(),
        httpStatus: HttpStatus = HttpStatus.OK,
    ): T {
        val stringParams = params.mapValues { (_, v) -> v.toString() }

        return testApi.doGetWithParams(url, stringParams, httpStatus).let { body ->
            mapper.readValue(body, T::class.java)
        }
    }
}
