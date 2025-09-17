package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.TestApi
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc

const val LOCATION_TRACK_COLLECTION_URL = "/geoviite/paikannuspohja/v1/sijaintiraiteet"

class ExtTrackLayoutTestApiService(mockMvc: MockMvc) {
    val mapper = jacksonObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApi = TestApi(mapper, mockMvc)

    fun getLocationTrackCollection(
        vararg params: Pair<String, String>,
    ): ExtTestLocationTrackCollectionV1 {
        return get<ExtTestLocationTrackCollectionV1>(LOCATION_TRACK_COLLECTION_URL, params.toMap())
    }

    //    fun getLocationTrackCollectionWithEmptyResponse(
    //        vararg params: Pair<String, String>,
    //        httpStatus: HttpStatus,
    //    ): ExtTestErrorResponseV1 {
    //        return get<ExtTestErrorResponseV1>(LOCATION_TRACK_COLLECTION_URL, params.toMap(), httpStatus)
    //    }

    fun getLocationTrackCollectionWithExpectedError(
        vararg params: Pair<String, String>,
        httpStatus: HttpStatus,
    ): ExtTestErrorResponseV1 {
        return get<ExtTestErrorResponseV1>(LOCATION_TRACK_COLLECTION_URL, params.toMap(), httpStatus)
    }

    inline fun <reified T> get(
        url: String,
        params: Map<String, String> = emptyMap(),
        httpStatus: HttpStatus = HttpStatus.OK,
    ): T {
        return testApi.doGetWithParams(url, params, httpStatus).let { body -> mapper.readValue(body, T::class.java) }
    }
}
