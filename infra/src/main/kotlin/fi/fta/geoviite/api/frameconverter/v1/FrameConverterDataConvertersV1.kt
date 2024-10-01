package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.io.IOException

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
