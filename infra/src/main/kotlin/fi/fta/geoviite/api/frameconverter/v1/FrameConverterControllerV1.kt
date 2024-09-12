package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import fi.fta.geoviite.api.aspects.GeoviiteIntegrationApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.error.IntegrationApiExceptionV1
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

@Configuration
class FrameConverterApiObjectMapperV1 {

    @Bean
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper {
        val module = SimpleModule()
        module.addDeserializer(FrameConverterRequestV1::class.java, FrameConverterRequestDeserializerV1())
        return builder.build<ObjectMapper>().registerModule(module)
    }
}

@PreAuthorize(AUTH_API_FRAME_CONVERTER)
@GeoviiteIntegrationApiController(
    [
        "/rata-vkm",
        "/rata-vkm/dev",
        "/rata-vkm/v1",
        "/rata-vkm/dev/v1",

        // Trailing slashes are also supported in the frame converter.
        "/rata-vkm/",
        "/rata-vkm/dev/",
        "/rata-vkm/v1/",
        "/rata-vkm/dev/v1/",
    ]
)
class FrameConverterControllerV1
@Autowired
constructor(
    private val objectMapper: ObjectMapper,
    private val frameConverterServiceV1: FrameConverterServiceV1,
    private val localizationService: LocalizationService,
) {

    @RequestMapping(method = [RequestMethod.GET, RequestMethod.POST], params = ["json"])
    fun multiInputTransform(
        @RequestParam(name = "json") requests: List<FrameConverterRequestV1>?
    ): GeoJsonFeatureCollection {
        val parsedRequests = requests ?: emptyList()

        return GeoJsonFeatureCollection(features = parsedRequests.flatMap(::processRequest))
    }

    @RequestMapping(method = [RequestMethod.GET, RequestMethod.POST], params = ["!json", "x", "y"])
    fun coordinateToTrackAddressRequest(@RequestParam params: Map<String, String?>): GeoJsonFeatureCollection {
        val jsonString = objectMapper.writeValueAsString(params)
        val request = objectMapper.readValue(jsonString, CoordinateToTrackAddressRequestV1::class.java)

        return GeoJsonFeatureCollection(features = processRequest(request))
    }

    @RequestMapping(method = [RequestMethod.GET, RequestMethod.POST], params = ["!json", "ratanumero"])
    fun trackAddressToCoordinateRequest(@RequestParam params: Map<String, String?>): GeoJsonFeatureCollection {
        val jsonString = objectMapper.writeValueAsString(params)
        val request = objectMapper.readValue(jsonString, TrackAddressToCoordinateRequestV1::class.java)

        return GeoJsonFeatureCollection(features = processRequest(request))
    }

    @RequestMapping(method = [RequestMethod.GET, RequestMethod.POST], params = ["!json"])
    fun invalidRequest(): GeoJsonFeatureCollection {
        return GeoJsonFeatureCollection(
            features =
                GeoJsonFeatureErrorResponseV1(
                        identifier = null,
                        errorMessages =
                            localizationService.getLocalization(LocalizationLanguage.FI).t("ext-api.error.bad-request"),
                    )
                    .let(::listOf)
        )
    }

    private fun processRequest(request: FrameConverterRequestV1): List<GeoJsonFeature> {
        return when (request) {
            is CoordinateToTrackAddressRequestV1 ->
                processRequestHelper(
                    request,
                    frameConverterServiceV1::validateCoordinateToTrackAddressRequest,
                    frameConverterServiceV1::coordinateToTrackAddress,
                )

            is TrackAddressToCoordinateRequestV1 ->
                processRequestHelper(
                    request,
                    frameConverterServiceV1::validateTrackAddressToCoordinateRequest,
                    frameConverterServiceV1::trackAddressToCoordinate,
                )

            else ->
                throw IntegrationApiExceptionV1(
                    message = "Unsupported request type",
                    error = FrameConverterErrorV1.UnsupportedRequestType,
                )
        }
    }

    private inline fun <T, V> processRequestHelper(
        request: T,
        validate: (T) -> Pair<V?, List<GeoJsonFeatureErrorResponseV1>>,
        process: (LayoutContext, V) -> List<GeoJsonFeature>,
    ): List<GeoJsonFeature> {
        val (validatedRequest, errorResponse) = validate(request)

        return validatedRequest?.let { req -> process(MainLayoutContext.official, req) } ?: errorResponse
    }
}
