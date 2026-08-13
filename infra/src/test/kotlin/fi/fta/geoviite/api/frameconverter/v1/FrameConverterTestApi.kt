package fi.fta.geoviite.api.frameconverter.v1

import TestGeoJsonFeatureCollection
import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.json.JsonMapper
import fi.fta.geoviite.infra.TestApi
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc

typealias FrameConverterUrl = String

abstract class FrameConverterTestRequest

const val RESPONSE_GEOMETRY_KEY = "geometriatiedot"
const val RESPONSE_BASIC_FEATURE_KEY = "perustiedot"
const val RESPONSE_DETAILED_FEATURE_KEY = "lisatiedot"

class FrameConverterTestApiService(mockMvc: MockMvc) {
    private val mapper = JsonMapper().rebuild().changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }.build()
    val testApi = TestApi(mapper, mockMvc)

    fun fetchFeatureCollectionSingle(
        url: String,
        params: Map<String, Any> = emptyMap(),
        httpStatus: HttpStatus = HttpStatus.OK,
    ): TestGeoJsonFeatureCollection {
        val stringParams = params.mapValues { (_, v) -> v.toString() }

        return testApi.doGetWithParams(url, stringParams, httpStatus).let { body ->
            mapper.readValue(body, TestGeoJsonFeatureCollection::class.java)
        }
    }

    fun fetchFeatureCollectionBatch(
        url: FrameConverterUrl,
        request: FrameConverterTestRequest,
        params: Map<String, Any> = emptyMap(),
    ): TestGeoJsonFeatureCollection = fetchFeatureCollectionBatch(url, listOf(request), params)

    fun fetchFeatureCollectionBatch(
        url: String,
        requests: List<FrameConverterTestRequest>,
        params: Map<String, Any> = emptyMap(),
    ): TestGeoJsonFeatureCollection {
        val headers = HttpHeaders()
        headers.set("Content-Type", "application/json")

        val stringParams = params.mapValues { (_, v) -> v.toString() }
        val json = mapper.writeValueAsString(requests)

        return testApi.doPostWithParams(url, stringParams, HttpStatus.OK, json, headers).let { body ->
            mapper.readValue(body, TestGeoJsonFeatureCollection::class.java)
        }
    }
}
