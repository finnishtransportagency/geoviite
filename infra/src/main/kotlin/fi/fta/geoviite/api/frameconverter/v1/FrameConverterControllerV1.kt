package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.tracklayout.v1.ExtSridV1
import fi.fta.geoviite.infra.aspects.DisableDefaultGeoviiteLogging
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.logging.apiResult
import fi.fta.geoviite.infra.util.Either
import fi.fta.geoviite.infra.util.processRights
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigDecimal

const val EXT_FRAME_CONVERTER_BASE_PATH = "/rata-vkm"

@PreAuthorize(AUTH_API_FRAME_CONVERTER)
@GeoviiteExtApiController(["$EXT_FRAME_CONVERTER_BASE_PATH/v1", "$EXT_FRAME_CONVERTER_BASE_PATH/dev/v1"])
class FrameConverterControllerV1
@Autowired
constructor(
    private val frameConverterServiceV1: FrameConverterServiceV1,
    @param:Value("\${geoviite.ext-api.max-batch-requests:0}") private val maxBatchRequests: Int,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/koordinaatit", "/koordinaatit/")
    @Tag(name = FRAME_CONVERTER_TAG_TRACK_ADDRESS_TO_COORDINATE)
    @Operation(
        summary = FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_SINGLE_SUMMARY,
        description = FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_SINGLE_DESCRIPTION,
    )
    fun trackAddressToCoordinateRequestSingle(
        @Parameter(description = FRAME_CONVERTER_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false, defaultValue = "EPSG:3067")
        coordinateSystem: ExtSridV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER_EXACTLY_ONE)
        @RequestParam(TRACK_NUMBER_NAME_PARAM, required = false)
        trackNumberName: FrameConverterStringV1?,
        @Parameter(
            description = FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER_OID_EXACTLY_ONE,
            schema = Schema(type = "string", format = "oid"),
        )
        @RequestParam(TRACK_NUMBER_OID_PARAM, required = false)
        trackNumberOid: FrameConverterStringV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_TRACK_KILOMETER, required = true)
        // Note: the above required is just for swagger: it's required for a successful request, but the controller
        // needs to accept missing args for the call to end up in the custom error handling
        @RequestParam(TRACK_KILOMETER_PARAM, required = false)
        trackKilometer: Int?,
        @Parameter(
            description = FRAME_CONVERTER_OPENAPI_TRACK_METER,
            required = true,
            schema =
                Schema(
                    minimum = FRAME_CONVERTER_OPENAPI_TRACK_METER_MIN,
                    maximum = FRAME_CONVERTER_OPENAPI_TRACK_METER_MAX,
                    exclusiveMaximum = true,
                ),
        )
        // Note: the above required is just for swagger: it's required for a successful request, but the controller
        // needs to accept missing args for the call to end up in the custom error handling
        @RequestParam(TRACK_METER_PARAM, required = false)
        trackMeter: BigDecimal?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK)
        @RequestParam(LOCATION_TRACK_NAME_PARAM, required = false)
        locationTrackName: FrameConverterStringV1?,
        @Parameter(
            description = FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK_OID,
            schema = Schema(type = "string", format = "oid"),
        )
        @RequestParam(LOCATION_TRACK_OID_PARAM, required = false)
        locationTrackOid: FrameConverterStringV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK_TYPE)
        @RequestParam(LOCATION_TRACK_TYPE_PARAM, required = false)
        locationTrackType: FrameConverterLocationTrackTypeV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_GEOMETRY)
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false, defaultValue = "false")
        featureGeometry: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_BASIC)
        @RequestParam(FEATURE_BASIC_PARAM, required = false, defaultValue = "true")
        featureBasic: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_DETAILS)
        @RequestParam(FEATURE_DETAILS_PARAM, required = false, defaultValue = "true")
        featureDetails: Boolean?,
    ): TrackAddressToCoordinateCollectionResponseV1 {
        val request =
            TrackAddressToCoordinateRequestV1(
                trackNumberName = trackNumberName,
                trackNumberOid = trackNumberOid,
                trackKilometer = trackKilometer,
                trackMeter = trackMeter,
                locationTrackName = locationTrackName,
                locationTrackOid = locationTrackOid,
                locationTrackType = locationTrackType,
            )

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)

        return TrackAddressToCoordinateCollectionResponseV1(
            features =
                processTrackAddressToCoordinateRequests(listOf(request), queryParams).flatten().map {
                    it as TrackAddressToCoordinateSingleResponseV1
                }
        )
    }

    @DisableDefaultGeoviiteLogging
    @PostMapping("/koordinaatit", "/koordinaatit/")
    @Tag(name = FRAME_CONVERTER_TAG_TRACK_ADDRESS_TO_COORDINATE)
    @Operation(
        summary = FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_BATCH_SUMMARY,
        description = FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_BATCH_DESCRIPTION,
    )
    fun trackAddressToCoordinateRequestBatch(
        @Parameter(description = FRAME_CONVERTER_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false, defaultValue = "EPSG:3067")
        coordinateSystem: ExtSridV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_GEOMETRY)
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false, defaultValue = "false")
        featureGeometry: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_BASIC)
        @RequestParam(FEATURE_BASIC_PARAM, required = false, defaultValue = "true")
        featureBasic: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_DETAILS)
        @RequestParam(FEATURE_DETAILS_PARAM, required = false, defaultValue = "true")
        featureDetails: Boolean?,
        @RequestBody(
            required = true,
            content =
                [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = TrackAddressToCoordinateRequestV1::class))
                    )
                ],
        )
        @org.springframework.web.bind.annotation.RequestBody
        requests: List<TrackAddressToCoordinateRequestV1>,
    ): TrackAddressToCoordinateCollectionResponseV1 {
        assertRequestSize(requests)
        logRequestAmount("trackAddressToCoordinateRequestBatch", requests)

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)
        val features =
            processTrackAddressToCoordinateRequests(requests, queryParams).flatten().map {
                it as TrackAddressToCoordinateSingleResponseV1
            }

        logFeatureAmount("trackAddressToCoordinateRequestBatch", features)
        return TrackAddressToCoordinateCollectionResponseV1(features = features)
    }

    @GetMapping("/rataosoitteet", "/rataosoitteet/")
    @Tag(name = FRAME_CONVERTER_TAG_COORDINATE_TO_TRACK_ADDRESS)
    @Operation(
        summary = FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_SINGLE_SUMMARY,
        description = FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_SINGLE_DESCRIPTION,
    )
    fun coordinateToTrackAddressRequestSingle(
        @Parameter(description = FRAME_CONVERTER_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false, defaultValue = "EPSG:3067")
        coordinateSystem: ExtSridV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_X, required = true)
        // Note: the above required is just for swagger: it's required for a successful request, but the controller
        // needs to accept missing args for the call to end up in the custom error handling
        @RequestParam("x", required = false)
        xCoordinate: Double?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_Y, required = true)
        // Note: the above required is just for swagger: it's required for a successful request, but the controller
        // needs to accept missing args for the call to end up in the custom error handling
        @RequestParam("y", required = false)
        yCoordinate: Double?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_SEARCH_RADIUS)
        @RequestParam(SEARCH_RADIUS_PARAM, required = false)
        searchRadius: Double?,
        @Parameter(
            description = FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER_OID,
            schema = Schema(type = "string", format = "oid"),
        )
        @RequestParam(TRACK_NUMBER_OID_PARAM, required = false)
        trackNumberOid: FrameConverterStringV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER)
        @RequestParam(TRACK_NUMBER_NAME_PARAM, required = false)
        trackNumberName: FrameConverterStringV1?,
        @Parameter(
            description = FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK_OID,
            schema = Schema(type = "string", format = "oid"),
        )
        @RequestParam(LOCATION_TRACK_OID_PARAM, required = false)
        locationTrackOid: FrameConverterStringV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK)
        @RequestParam(LOCATION_TRACK_NAME_PARAM, required = false)
        locationTrackName: FrameConverterStringV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK_TYPE)
        @RequestParam(LOCATION_TRACK_TYPE_PARAM, required = false)
        locationTrackType: FrameConverterLocationTrackTypeV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_GEOMETRY)
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false, defaultValue = "false")
        featureGeometry: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_BASIC)
        @RequestParam(FEATURE_BASIC_PARAM, required = false, defaultValue = "true")
        featureBasic: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_DETAILS)
        @RequestParam(FEATURE_DETAILS_PARAM, required = false, defaultValue = "true")
        featureDetails: Boolean?,
    ): CoordinateToTrackAddressCollectionResponseV1 {
        val request =
            CoordinateToTrackAddressRequestV1(
                x = xCoordinate,
                y = yCoordinate,
                searchRadius = searchRadius,
                trackNumberName = trackNumberName,
                trackNumberOid = trackNumberOid,
                locationTrackOid = locationTrackOid,
                locationTrackName = locationTrackName,
                locationTrackType = locationTrackType,
            )

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)

        return CoordinateToTrackAddressCollectionResponseV1(
            features =
                processCoordinateToTrackAddressRequests(listOf(request), queryParams).flatten().map {
                    it as CoordinateToTrackAddressSingleResponseV1
                }
        )
    }

    @DisableDefaultGeoviiteLogging
    @PostMapping("/rataosoitteet", "/rataosoitteet/")
    @Tag(name = FRAME_CONVERTER_TAG_COORDINATE_TO_TRACK_ADDRESS)
    @Operation(
        summary = FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_BATCH_SUMMARY,
        description = FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_BATCH_DESCRIPTION,
    )
    fun coordinateToTrackAddressRequestBatch(
        @Parameter(description = FRAME_CONVERTER_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false, defaultValue = "EPSG:3067")
        coordinateSystem: ExtSridV1?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_GEOMETRY)
        @RequestParam(FEATURE_GEOMETRY_PARAM, required = false, defaultValue = "false")
        featureGeometry: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_BASIC)
        @RequestParam(FEATURE_BASIC_PARAM, required = false, defaultValue = "true")
        featureBasic: Boolean?,
        @Parameter(description = FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_DETAILS)
        @RequestParam(FEATURE_DETAILS_PARAM, required = false, defaultValue = "true")
        featureDetails: Boolean?,
        @RequestBody(
            required = true,
            content =
                [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = CoordinateToTrackAddressRequestV1::class))
                    )
                ],
        )
        @org.springframework.web.bind.annotation.RequestBody
        requests: List<CoordinateToTrackAddressRequestV1>,
    ): CoordinateToTrackAddressCollectionResponseV1 {
        assertRequestSize(requests)
        logRequestAmount("coordinateToTrackAddressRequestBatch", requests)

        val queryParams = FrameConverterQueryParamsV1(coordinateSystem, featureGeometry, featureBasic, featureDetails)
        val features =
            processCoordinateToTrackAddressRequests(requests, queryParams).flatten().map {
                it as CoordinateToTrackAddressSingleResponseV1
            }

        logFeatureAmount("coordinateToTrackAddressRequestBatch", features)

        return CoordinateToTrackAddressCollectionResponseV1(features = features)
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
            frameConverterServiceV1.validateTrackAddressToCoordinateRequests(
                LayoutBranch.main,
                assertRequestType(requests),
                params,
            ),
            params,
            { validated, _ -> validated },
            frameConverterServiceV1::trackAddressesToCoordinates,
        )
    }

    private inline fun <reified Request : FrameConverterRequestV1> assertRequestType(requests: List<*>): List<Request> {
        if (requests.any { it !is Request }) {
            throw FrameConverterExceptionV1(
                message = "Unsupported request type",
                error = FrameConverterErrorV1.UnsupportedRequestType,
            )
        }
        @Suppress("UNCHECKED_CAST")
        return requests as List<Request>
    }

    private fun <T : FrameConverterRequestV1> assertRequestSize(requests: List<T>) {
        if (requests.size > maxBatchRequests) {
            throw FrameConverterExceptionV1(
                message = "Too many requests in batch: maxCount=$maxBatchRequests",
                error = FrameConverterErrorV1.TooManyRequests,
            )
        }
    }

    private fun <Request, ValidRequest> processRequests(
        requests: List<Request>,
        params: FrameConverterQueryParamsV1,
        validate: (Request, FrameConverterQueryParamsV1) -> Either<List<GeoJsonFeatureErrorResponseV1>, ValidRequest>,
        process: (LayoutBranch, List<ValidRequest>, FrameConverterQueryParamsV1) -> List<List<GeoJsonFeature>>,
    ): List<List<GeoJsonFeature>> =
        processRights(
            requests,
            { request -> validate(request, params) },
            { validRequests -> process(LayoutBranch.main, validRequests, params) },
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
