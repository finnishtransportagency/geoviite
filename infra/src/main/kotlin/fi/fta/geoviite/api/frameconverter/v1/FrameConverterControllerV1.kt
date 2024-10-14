package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.infra.aspects.DisableDefaultGeoviiteLogging
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.ExtApiExceptionV1
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.logging.apiResult
import fi.fta.geoviite.infra.util.Either
import fi.fta.geoviite.infra.util.processValidated
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

        return GeoJsonFeatureCollection(
            features = processTrackAddressToCoordinateRequests(listOf(request), queryParams).flatten()
        )
    }

    @DisableDefaultGeoviiteLogging
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
        val features = processTrackAddressToCoordinateRequests(requests, queryParams).flatten()

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

        return GeoJsonFeatureCollection(
            features = processCoordinateToTrackAddressRequests(listOf(request), queryParams).flatten()
        )
    }

    @DisableDefaultGeoviiteLogging
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
        val features = processCoordinateToTrackAddressRequests(requests, queryParams).flatten()

        logFeatureAmount("coordinateToTrackAddressRequestBatch", features)

        return GeoJsonFeatureCollection(features = features)
    }

    private fun processCoordinateToTrackAddressRequests(
        requests: List<FrameConverterRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> =
        processRequests(
            assertRequestType(requests),
            params,
            frameConverterServiceV1::validateCoordinateToTrackAddressRequest,
            frameConverterServiceV1::coordinatesToTrackAddresses,
        )

    private fun processTrackAddressToCoordinateRequests(
        requests: List<FrameConverterRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> {
        return processRequests(
            assertRequestType(requests),
            params,
            frameConverterServiceV1::validateTrackAddressToCoordinateRequest,
            frameConverterServiceV1::trackAddressesToCoordinates,
        )
    }

    private inline fun <reified Request : FrameConverterRequestV1> assertRequestType(requests: List<*>): List<Request> {
        if (requests.any { it !is Request }) {
            throw ExtApiExceptionV1(
                message = "Unsupported request type",
                error = FrameConverterErrorV1.UnsupportedRequestType,
            )
        }
        @Suppress("UNCHECKED_CAST")
        return requests as List<Request>
    }

    private fun <Request : FrameConverterRequestV1, ValidRequest> processRequests(
        requests: List<Request>,
        params: FrameConverterQueryParamsV1,
        validate: (Request, FrameConverterQueryParamsV1) -> Either<List<GeoJsonFeatureErrorResponseV1>, ValidRequest>,
        process: (LayoutContext, List<ValidRequest>, FrameConverterQueryParamsV1) -> List<List<GeoJsonFeature>>,
    ): List<List<GeoJsonFeature>> =
        processValidated(
            requests,
            { request -> validate(request, params) },
            { validRequests -> process(MainLayoutContext.official, validRequests, params) },
        )

    private fun logRequestAmount(method: String, requests: List<FrameConverterRequestV1>) {
        logger.apiCall(method, listOf("requestAmount" to requests.size))
    }

    private fun logFeatureAmount(method: String, features: List<GeoJsonFeature>) {
        val errorAmount = features.count { feature -> GeoJsonFeatureErrorResponseV1::class.java.isInstance(feature) }
        val successAmount = features.size - errorAmount

        logger.apiResult(method, listOf("successAmount" to successAmount, "errorAmount" to errorAmount))
    }
}
