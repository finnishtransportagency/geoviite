package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.infra.aspects.DisableLogging
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.ExtApiExceptionV1
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.logging.apiResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@PreAuthorize(AUTH_API_FRAME_CONVERTER)
@GeoviiteExtApiController(["/rata-vkm/v1", "/rata-vkm/dev/v1"])
class FrameConverterControllerV1 @Autowired constructor(private val frameConverterServiceV1: FrameConverterServiceV1) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/koordinaatit", "/koordinaatit/")
    fun trackAddressToCoordinateRequestSingle(
        @RequestParam(TRACK_NUMBER_NAME_PARAM, required = false) trackNumberName: FrameConverterStringV1?,
        @RequestParam(TRACK_KILOMETER_PARAM, required = false) trackKilometer: Int?,
        @RequestParam(TRACK_METER_PARAM, required = false) trackMeter: Int?,
        @RequestParam(LOCATION_TRACK_NAME_PARAM, required = false) locationTrackName: FrameConverterStringV1?,
        @RequestParam(LOCATION_TRACK_TYPE_PARAM, required = false)
        locationTrackType: FrameConverterLocationTrackTypeV1?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false) featureGeometry: Boolean?,
        @RequestParam(FEATURE_BASIC_PARAM, required = false) featureBasic: Boolean?,
        @RequestParam(FEATURE_DETAILS_PARAM, required = false) featureDetails: Boolean?,
    ): GeoJsonFeatureCollection {
        val request =
            TrackAddressToCoordinateRequestV1(
                trackNumberName = trackNumberName,
                trackKilometer = trackKilometer,
                trackMeter = trackMeter,
                locationTrackName = locationTrackName,
                locationTrackType = locationTrackType,
            )

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)

        return GeoJsonFeatureCollection(features = processRequest(request, queryParams))
    }

    @DisableLogging
    @PostMapping("/koordinaatit", "/koordinaatit/")
    fun trackAddressToCoordinateRequestBatch(
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false) featureGeometry: Boolean?,
        @RequestParam(FEATURE_BASIC_PARAM, required = false) featureBasic: Boolean?,
        @RequestParam(FEATURE_DETAILS_PARAM, required = false) featureDetails: Boolean?,
        @RequestBody requests: List<TrackAddressToCoordinateRequestV1>,
    ): GeoJsonFeatureCollection {
        logRequestAmount("trackAddressToCoordinateRequestBatch", requests)

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)
        val features = requests.flatMap { request -> processRequest(request, queryParams) }

        logFeatureAmount("trackAddressToCoordinateRequestBatch", features)
        return GeoJsonFeatureCollection(features = features)
    }

    @GetMapping("/rataosoitteet", "/rataosoitteet/")
    fun coordinateToTrackAddressRequestSingle(
        @RequestParam("x") xCoordinate: Double?,
        @RequestParam("y") yCoordinate: Double?,
        @RequestParam(SEARCH_RADIUS_PARAM, required = false) searchRadius: Double?,
        @RequestParam(TRACK_NUMBER_NAME_PARAM, required = false) trackNumberName: FrameConverterStringV1?,
        @RequestParam(LOCATION_TRACK_NAME_PARAM, required = false) locationTrackName: FrameConverterStringV1?,
        @RequestParam(LOCATION_TRACK_TYPE_PARAM, required = false)
        locationTrackType: FrameConverterLocationTrackTypeV1?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false) featureGeometry: Boolean?,
        @RequestParam(FEATURE_BASIC_PARAM, required = false) featureBasic: Boolean?,
        @RequestParam(FEATURE_DETAILS_PARAM, required = false) featureDetails: Boolean?,
    ): GeoJsonFeatureCollection {
        val request =
            CoordinateToTrackAddressRequestV1(
                x = xCoordinate,
                y = yCoordinate,
                searchRadius = searchRadius,
                trackNumberName = trackNumberName,
                locationTrackName = locationTrackName,
                locationTrackType = locationTrackType,
            )

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)

        return GeoJsonFeatureCollection(features = processRequest(request, queryParams))
    }

    @DisableLogging
    @PostMapping("/rataosoitteet", "/rataosoitteet/")
    fun coordinateToTrackAddressRequestBatch(
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false) featureGeometry: Boolean?,
        @RequestParam(FEATURE_BASIC_PARAM, required = false) featureBasic: Boolean?,
        @RequestParam(FEATURE_DETAILS_PARAM, required = false) featureDetails: Boolean?,
        @RequestBody requests: List<CoordinateToTrackAddressRequestV1>,
    ): GeoJsonFeatureCollection {
        logRequestAmount("coordinateToTrackAddressRequestBatch", requests)

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)
        val features = requests.flatMap { request -> processRequest(request, queryParams) }

        logFeatureAmount("coordinateToTrackAddressRequestBatch", features)
        return GeoJsonFeatureCollection(features = features)
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

    private fun logRequestAmount(method: String, requests: List<FrameConverterRequestV1>) {
        logger.apiCall(method, listOf("requestAmount" to requests.size))
    }

    private fun logFeatureAmount(method: String, features: List<GeoJsonFeature>) {
        val errorAmount = features.count { feature -> GeoJsonFeatureErrorResponseV1::class.java.isInstance(feature) }
        val successAmount = features.size - errorAmount

        logger.apiResult(method, listOf("successAmount" to successAmount, "errorAmount" to errorAmount))
    }
}
