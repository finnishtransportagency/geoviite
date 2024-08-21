package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import fi.fta.geoviite.api.aspects.GeoviiteIntegrationApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.error.IntegrationApiException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ModelAttribute
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
    ],
)
class FrameConverterControllerV1 @Autowired constructor(
    private val frameConverterServiceV1: FrameConverterServiceV1,
) {

    @RequestMapping(method = [RequestMethod.GET, RequestMethod.POST])
    fun multiInputTransform(
        @RequestParam(name = "json") requests: List<FrameConverterRequestV1>?,
    ): GeoJsonFeatureCollection {
        val parsedRequests = requests ?: emptyList()

        return GeoJsonFeatureCollection(
            features = parsedRequests.flatMap(::processRequest),
        )
    }

    @RequestMapping(method = [RequestMethod.GET, RequestMethod.POST], params = ["!json"])
    fun singleInputTransform(
        @ModelAttribute request: CoordinateToTrackMeterRequestV1,
    ): GeoJsonFeatureCollection {
        return GeoJsonFeatureCollection(
            features = processRequest(request),
        )
    }

    private fun processRequest(request: FrameConverterRequestV1): List<GeoJsonFeature> {
        return when (request) {
            is CoordinateToTrackMeterRequestV1 -> {
                val (validatedRequest, errorResponse)
                    = frameConverterServiceV1.validateCoordinateToTrackMeterRequest(request)

                if (validatedRequest == null) {
                    errorResponse
                } else {
                    frameConverterServiceV1.coordinateToTrackAddress(
                        layoutContext = MainLayoutContext.official,
                        request = validatedRequest,
                    )
                }
            }

            else -> throw IntegrationApiException(
                message = "Unsupported request type",
                localizedMessageKey = "unsupported-request-type",
            )
        }
    }
}
