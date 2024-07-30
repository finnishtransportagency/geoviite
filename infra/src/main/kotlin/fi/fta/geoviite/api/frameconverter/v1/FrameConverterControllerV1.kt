package fi.fta.geoviite.api.frameconverter.v1

import CoordinateToTrackMeterRequestV1
import FrameConverterRequestV1
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.api.aspects.GeoviiteIntegrationApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.infra.common.MainLayoutContext
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

class FrameConverterRequestDeserializerV1(
    private val objectMapper: ObjectMapper
) : JsonDeserializer<FrameConverterRequestV1>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FrameConverterRequestV1 {
        val node: JsonNode = p.codec.readTree(p)
        return when {
            node.has("x") && node.has("y")
            -> objectMapper.readValue(node.traverse(p.codec), CoordinateToTrackMeterRequestV1::class.java)

            else -> throw IllegalArgumentException("Unknown type")
        }
    }
}

@Configuration
class FrameConverterApiObjectMapperV1 {

    @Bean
    fun frameConverterObjectMapperV1(): ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        val module = SimpleModule().apply {
            addDeserializer(FrameConverterRequestV1::class.java, FrameConverterRequestDeserializerV1(objectMapper))
        }
        objectMapper.registerModule(module)
        return objectMapper
    }
}

@GeoviiteIntegrationApiController("/frame-converter/v1")
class FrameConverterControllerV1 @Autowired constructor(
    private val frameConverterObjectMapperV1: ObjectMapper,
    private val frameConverterServiceV1: FrameConverterServiceV1,
) {
    @RequestMapping(
        "/todo-url",
        method = [RequestMethod.GET, RequestMethod.POST]
    )
    fun multiInputTransform(request: HttpServletRequest): GeoJsonFeatureCollection {
        val body = request.inputStream.bufferedReader().use { it.readText() }

        val parsedRequests: List<FrameConverterRequestV1> = frameConverterObjectMapperV1.readValue(
            body,
            frameConverterObjectMapperV1.typeFactory.constructCollectionType(
                List::class.java,
                FrameConverterRequestV1::class.java
            )
        )

        return GeoJsonFeatureCollection(
            features = parsedRequests.map { parsedRequest ->
                when (parsedRequest) {
                    is CoordinateToTrackMeterRequestV1 -> frameConverterServiceV1.coordinateToTrackAddress(
                        layoutContext = MainLayoutContext.official,
                        request = parsedRequest,
                    )

                    else -> TODO()
                }
            }
        )
    }


    @RequestMapping(
        "/coordinate-to-track-address",
        method = [RequestMethod.GET, RequestMethod.POST]
    )
    fun coordinateToTrackAddressPost(
        @RequestParam(required = false) json: String?,
        @ModelAttribute request: CoordinateToTrackMeterRequestV1,
    ): GeoJsonFeatureCollection {
        val parsedRequest = if (json != null) {
            frameConverterObjectMapperV1.readValue(json, CoordinateToTrackMeterRequestV1::class.java)
        } else {
            request
        }

        return GeoJsonFeatureCollection(
            frameConverterServiceV1.coordinateToTrackAddress(
                layoutContext = MainLayoutContext.official,
                request = parsedRequest,
            )
        )
    }
}
