package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.error.ExtApiExceptionV1
import java.io.IOException
import kotlin.reflect.KClass
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
class FrameConverterRequestConverterV1(private val objectMapper: ObjectMapper) :
    Converter<String, FrameConverterRequestV1> {

    override fun convert(source: String): FrameConverterRequestV1? {
        return try {
            objectMapper.readValue(source, FrameConverterRequestV1::class.java)
        } catch (e: IOException) {
            throw ExtApiExceptionV1(
                cause = e,
                message = "Json structure was not wrapped in an array",
                error = FrameConverterErrorV1.RequestCouldNotBeDeserialized,
            )
        }
    }
}

@Component
class FrameConverterListRequestConverterV1(private val objectMapper: ObjectMapper) :
    Converter<String, List<FrameConverterRequestV1>> {

    override fun convert(source: String): List<FrameConverterRequestV1>? {
        val collectionType =
            objectMapper.typeFactory.constructCollectionType(List::class.java, FrameConverterRequestV1::class.java)

        return try {
            objectMapper.readValue(source, collectionType)
        } catch (e: IOException) {
            throw ExtApiExceptionV1(
                cause = e,
                message = "Json structure was not wrapped in an array",
                error = FrameConverterErrorV1.ListOfJsonRequestsCouldNotBeDeserialized,
            )
        }
    }
}

class FrameConverterRequestDeserializerV1 : JsonDeserializer<FrameConverterRequestV1>() {
    private val objectMapper = jacksonObjectMapper()

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FrameConverterRequestV1 {
        val node: JsonNode = p.codec.readTree(p)
        return objectMapper.treeToValue(node, determineClass(node).java)
    }

    private fun determineClass(node: JsonNode): KClass<out FrameConverterRequestV1> {
        val classMap =
            listOf(
                CoordinateToTrackAddressRequestV1::class to listOf("x", "y"),
                CoordinateToTrackAddressRequestV1::class to listOf("x"),
                CoordinateToTrackAddressRequestV1::class to listOf("y"),
                TrackAddressToCoordinateRequestV1::class to listOf("ratakilometri", "ratametri", "ratanumero"),
                TrackAddressToCoordinateRequestV1::class to listOf("ratakilometri", "ratametri"),
                TrackAddressToCoordinateRequestV1::class to listOf("ratakilometri"),
                TrackAddressToCoordinateRequestV1::class to listOf("ratametri"),
            )

        val clazz = classMap.firstOrNull { (_, keys) -> keys.all { key -> node.has(key) } }?.first

        if (clazz == null) {
            throw ExtApiExceptionV1(
                message = "Request object type could not be determined, missing parameters?",
                error = FrameConverterErrorV1.BadRequest,
            )
        }

        return clazz
    }
}

class FrameConverterStringDeserializerV1 : JsonDeserializer<FrameConverterStringV1>() {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): FrameConverterStringV1 {
        val value = p?.valueAsString ?: ""
        return FrameConverterStringV1(value)
    }
}

class FrameConverterLocationTrackTypeDeserializerV1 : JsonDeserializer<FrameConverterLocationTrackTypeV1>() {
    @Throws(IOException::class)
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): FrameConverterLocationTrackTypeV1 {
        val value = parser.text
        return FrameConverterLocationTrackTypeV1.fromValue(value)
    }
}
