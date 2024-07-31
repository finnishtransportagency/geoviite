package fi.fta.geoviite.api.frameconverter.v1

import CoordinateToTrackMeterRequestV1
import FrameConverterRequestV1
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.fta.geoviite.api.aspects.GeoviiteIntegrationApiController
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.error.IntegrationApiException
import fi.fta.geoviite.infra.error.SplitFailureException
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.localizationParams
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import kotlin.reflect.KClass

@GeoviiteIntegrationApiController("/frame-converter/v1")
class FrameConverterControllerV1 @Autowired constructor(
    private val frameConverterObjectMapperV1: ObjectMapper,
    private val frameConverterServiceV1: FrameConverterServiceV1,
    private val localizationService: LocalizationService,
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

            else -> throw IntegrationApiException(
                message = "Unsupported request type",
                localizedMessageKey = "unsupported-request-type",
            )
        }
    }

    private fun deserializeJsonRequests(json: String): List<FrameConverterRequestV1> {
        val collectionType = frameConverterObjectMapperV1.typeFactory.constructCollectionType(
            List::class.java,
            FrameConverterRequestV1::class.java,
        )

        return try {
            frameConverterObjectMapperV1.readValue(json, collectionType)
        } catch (exception: JsonProcessingException) {
            throw IntegrationApiException(
                cause = exception,
                message = "Request contained invalid json",
                localizedMessageKey = "invalid-json",
            )
        }
    }

    private fun deserializeRequestParams(request: HttpServletRequest): FrameConverterRequestV1 {
        val paramMap = request.parameterMap.mapValues { params -> params.value[0] }
        val node = frameConverterObjectMapperV1.valueToTree<JsonNode>(paramMap)

        val clazz = determineClass(node)

        return frameConverterObjectMapperV1.treeToValue(node, clazz.java)
    }
}

private fun determineClass(node: JsonNode): KClass<out FrameConverterRequestV1> {
    val classMap = listOf(
        CoordinateToTrackMeterRequestV1::class to listOf("x", "y"),
        CoordinateToTrackMeterRequestV1::class to listOf("x"),
        CoordinateToTrackMeterRequestV1::class to listOf("y"),
    )

    val clazz = classMap.firstOrNull { (_, keys) ->
        keys.all { key -> node.has(key) }
    }?.first

    if (clazz == null) {
        throw IntegrationApiException(
            message = "Request object type could not be determined, missing parameters?",
            localizedMessageKey = "bad-request",
        )
    }

    return clazz
}

class FrameConverterRequestDeserializerV1(
    private val objectMapper: ObjectMapper,
) : JsonDeserializer<List<FrameConverterRequestV1>>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<FrameConverterRequestV1> {
        val node: JsonNode = p.codec.readTree(p)

        if (!node.isArray) {
            throw IntegrationApiException(
                message = "Json structure was not wrapped in an array",
                localizedMessageKey = "json-request-was-not-wrapped-in-an-array",
            )
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
