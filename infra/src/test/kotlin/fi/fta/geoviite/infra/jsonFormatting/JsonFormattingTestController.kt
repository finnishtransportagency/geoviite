package fi.fta.geoviite.infra.jsonFormatting

import fi.fta.geoviite.infra.aspects.GeoviiteController
import java.time.Instant
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
}
