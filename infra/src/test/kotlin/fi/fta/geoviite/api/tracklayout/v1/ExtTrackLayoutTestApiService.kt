package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.TestApi
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc

private const val LOCATION_TRACK_COLLECTION_URL = "/geoviite/paikannuspohja/v1/sijaintiraiteet"

class ExtTrackLayoutTestApiService(mockMvc: MockMvc) {
    val mapper = jacksonObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApi = TestApi(mapper, mockMvc)

    fun getLocationTrackCollection(params: Map<String, String> = emptyMap()): ExtTestLocationTrackCollectionV1 {
        return get<ExtTestLocationTrackCollectionV1>(LOCATION_TRACK_COLLECTION_URL, params)
    }

    inline fun <reified T> get(
        url: String,
        params: Map<String, String> = emptyMap(),
        httpStatus: HttpStatus = HttpStatus.OK,
    ): T {
        return testApi.doGetWithParams(url, params, httpStatus).let { body -> mapper.readValue(body, T::class.java) }
    }
}
