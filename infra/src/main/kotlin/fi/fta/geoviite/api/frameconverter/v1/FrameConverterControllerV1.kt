package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.error.ExtApiExceptionV1
import java.util.stream.Collectors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@PreAuthorize(AUTH_API_FRAME_CONVERTER)
@GeoviiteExtApiController(["/rata-vkm/v1", "/rata-vkm/dev/v1"])
class FrameConverterControllerV1
@Autowired
constructor(private val objectMapper: ObjectMapper, private val frameConverterServiceV1: FrameConverterServiceV1) {

    @GetMapping("/koordinaatit", "/koordinaatit/")
    fun trackAddressToCoordinateRequestSingle(@RequestParam params: Map<String, String?>): GeoJsonFeatureCollection {
        val jsonString = objectMapper.writeValueAsString(params)
        val request = objectMapper.readValue(jsonString, TrackAddressToCoordinateRequestV1::class.java)
        val queryParams = objectMapper.readValue(jsonString, FrameConverterQueryParamsV1::class.java)

        return GeoJsonFeatureCollection(features = processRequest(request, queryParams))
    }

    @PostMapping("/koordinaatit", "/koordinaatit/")
    fun trackAddressToCoordinateRequestBatch(
        @RequestParam params: Map<String, String?>,
        @RequestBody requests: List<TrackAddressToCoordinateRequestV1>,
    ): GeoJsonFeatureCollection {
        val jsonString = objectMapper.writeValueAsString(params)
        val queryParams = objectMapper.readValue(jsonString, FrameConverterQueryParamsV1::class.java)

        return GeoJsonFeatureCollection(features = processRequestsInParallel(requests, queryParams))
    }

    @GetMapping("/rataosoitteet", "/rataosoitteet/")
    fun coordinateToTrackAddressRequestSingle(@RequestParam params: Map<String, String?>): GeoJsonFeatureCollection {
        val jsonString = objectMapper.writeValueAsString(params)
        val request = objectMapper.readValue(jsonString, CoordinateToTrackAddressRequestV1::class.java)
        val queryParams = objectMapper.readValue(jsonString, FrameConverterQueryParamsV1::class.java)

        return GeoJsonFeatureCollection(features = processRequest(request, queryParams))
    }

    @PostMapping("/rataosoitteet", "/rataosoitteet/")
    fun coordinateToTrackAddressRequestBatch(
        @RequestParam params: Map<String, String?>,
        @RequestBody requests: List<CoordinateToTrackAddressRequestV1>,
    ): GeoJsonFeatureCollection {
        val jsonString = objectMapper.writeValueAsString(params)
        val queryParams = objectMapper.readValue(jsonString, FrameConverterQueryParamsV1::class.java)

        return GeoJsonFeatureCollection(features = processRequestsInParallel(requests, queryParams))
    }

    private fun processRequestsInParallel(
        requests: List<FrameConverterRequestV1>,
        queryParams: FrameConverterQueryParamsV1,
    ): List<GeoJsonFeature> {
        return requests
            .parallelStream()
            .map { req -> processRequest(req, queryParams) }
            .collect(Collectors.toList())
            .flatten()
    }

    private fun processRequest(
        request: FrameConverterRequestV1,
        params: FrameConverterQueryParamsV1,
    ): List<GeoJsonFeature> {
        return when (request) {
            is CoordinateToTrackAddressRequestV1 ->
                processRequestHelper(
                    request,
                    params,
                    frameConverterServiceV1::validateCoordinateToTrackAddressRequest,
                    frameConverterServiceV1::coordinateToTrackAddress,
                )

            is TrackAddressToCoordinateRequestV1 ->
                processRequestHelper(
                    request,
                    params,
                    frameConverterServiceV1::validateTrackAddressToCoordinateRequest,
                    frameConverterServiceV1::trackAddressToCoordinate,
                )

            else ->
                throw ExtApiExceptionV1(
                    message = "Unsupported request type",
                    error = FrameConverterErrorV1.UnsupportedRequestType,
                )
        }
    }

    private inline fun <T, V> processRequestHelper(
        request: T,
        params: FrameConverterQueryParamsV1,
        validate: (T, FrameConverterQueryParamsV1) -> Pair<V?, List<GeoJsonFeatureErrorResponseV1>>,
        process: (LayoutContext, V, FrameConverterQueryParamsV1) -> List<GeoJsonFeature>,
    ): List<GeoJsonFeature> {
        val (validatedRequest, errorResponse) = validate(request, params)

        return validatedRequest?.let { req -> process(MainLayoutContext.official, req, params) } ?: errorResponse
    }
}
