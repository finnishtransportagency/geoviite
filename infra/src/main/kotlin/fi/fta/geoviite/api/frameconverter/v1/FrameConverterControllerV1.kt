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
import com.fasterxml.jackson.module.kotlin.readValue
import fi.fta.geoviite.api.aspects.GeoviiteIntegrationApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
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
import kotlin.reflect.KClass

@GeoviiteIntegrationApiController("/frame-converter/v1")
class FrameConverterControllerV1 @Autowired constructor(
    private val frameConverterObjectMapperV1: ObjectMapper,
    private val frameConverterServiceV1: FrameConverterServiceV1,
) {

    @RequestMapping(
        "/todo-url",
        method = [RequestMethod.GET, RequestMethod.POST]
    )
    fun multiInputTransform(
        request: HttpServletRequest,
        @RequestParam(required = false) json: String?,
    ): GeoJsonFeatureCollection {
        val parsedRequests = if (json != null) {
            deserializeJsonRequests(json)
        } else {
            listOf(deserializeRequestParams(request))
        }

        return GeoJsonFeatureCollection(
            features = parsedRequests.flatMap(::processRequest)
        )
    }

    private fun processRequest(request: FrameConverterRequestV1): List<GeoJsonFeature> {
        return when (request) {
            is CoordinateToTrackMeterRequestV1 -> frameConverterServiceV1.coordinateToTrackAddress(
                layoutContext = MainLayoutContext.official,
                request = request,
            )

            else -> TODO()
        }
    }

    private fun deserializeJsonRequests(json: String): List<FrameConverterRequestV1> {
        val collectionType = frameConverterObjectMapperV1.typeFactory.constructCollectionType(
            List::class.java,
            FrameConverterRequestV1::class.java,
        )

        return frameConverterObjectMapperV1.readValue(json, collectionType)
    }

    private fun deserializeRequestParams(request: HttpServletRequest): FrameConverterRequestV1 {
        val paramMap = request.parameterMap.mapValues { params -> params.value[0] }
        val node = frameConverterObjectMapperV1.valueToTree<JsonNode>(paramMap)

        val clazz = determineClass(node)
        requireNotNull(clazz)

        return frameConverterObjectMapperV1.treeToValue(node, clazz.java)
    }
}

private fun determineClass(node: JsonNode): KClass<out FrameConverterRequestV1>? {
    val classMap = mapOf(
        CoordinateToTrackMeterRequestV1::class to listOf("x", "y"),
    )

    return classMap.entries.firstOrNull { (_, keys) ->
        keys.all { key -> node.has(key) }
    }?.key
}

class FrameConverterRequestDeserializerV1(
    private val objectMapper: ObjectMapper
) : JsonDeserializer<List<FrameConverterRequestV1>>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<FrameConverterRequestV1> {
        val node: JsonNode = p.codec.readTree(p)

        require(node.isArray) {
            "Can't deserialize non-array!"
        }

        return node.map { nodeValue -> deserializeSingle(nodeValue) }
    }

    private fun deserializeSingle(node: JsonNode): FrameConverterRequestV1 {
        val clazz = determineClass(node)
        requireNotNull(clazz) // TODO Better error handling

        return objectMapper.treeToValue(node, clazz.java)
    }
}

@Configuration
class FrameConverterApiObjectMapperV1 {

    @Bean
    fun frameConverterObjectMapperV1(): ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        val module = SimpleModule().apply {
            addDeserializer(List::class.java, FrameConverterRequestDeserializerV1(objectMapper))
        }
        objectMapper.registerModule(module)
        return objectMapper
    }
}
