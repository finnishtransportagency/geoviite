package fi.fta.geoviite.api.frameconverter.v1

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import java.io.IOException

class FrameConverterLocationTrackTypeDeserializerV1 : ValueDeserializer<FrameConverterLocationTrackTypeV1>() {
    @Throws(IOException::class)
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): FrameConverterLocationTrackTypeV1 {
        val value = parser.text
        return FrameConverterLocationTrackTypeV1.fromValue(value)
    }
}
