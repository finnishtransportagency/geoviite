package fi.fta.geoviite.infra.jsonFormatting

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class JsonFormattingTestController @Autowired constructor() {

    @GetMapping("/json-test-path/to-millis/{instant}")
    fun requestWithInstantPath(@PathVariable("instant") instant: Instant): String {
        return instant.toEpochMilli().toString();
    }

    @GetMapping("/json-test-path/to-instant/{millis}")
    fun requestWithInstantPath(@PathVariable("millis") epochMillis: Long): Instant {
        return Instant.ofEpochMilli(epochMillis)
    }
}
