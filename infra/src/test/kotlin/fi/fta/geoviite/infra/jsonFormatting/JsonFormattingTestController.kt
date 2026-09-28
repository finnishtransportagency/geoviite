package fi.fta.geoviite.infra.jsonFormatting

import fi.fta.geoviite.infra.aspects.GeoviiteController
import java.time.Instant
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@GeoviiteController("/json-test-path")
class JsonFormattingTestController {

    @GetMapping("/to-millis/{instant}")
    fun requestWithInstantPath(@PathVariable("instant") instant: Instant): String {
        return instant.toEpochMilli().toString()
    }

    @GetMapping("/to-instant/{millis}")
    fun requestWithInstantPath(@PathVariable("millis") epochMillis: Long): Instant {
        return Instant.ofEpochMilli(epochMillis)
    }

    @GetMapping("/nullable-field")
    fun requestWithNullableField(): NullableFieldData = NullableFieldData(value1 = "only value1 is set", value2 = null)

    @GetMapping("/byte-array")
    fun requestByteArray(): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(BYTE_ARRAY_RESPONSE_BODY)
}

val BYTE_ARRAY_RESPONSE_BODY = byteArrayOf(1, 2, 3, 4, 5)

data class NullableFieldData(val value1: String, val value2: String?)
