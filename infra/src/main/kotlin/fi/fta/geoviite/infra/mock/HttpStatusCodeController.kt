package fi.fta.geoviite.infra.mock

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mock-http")
class HttpStatusCodeController {
    @GetMapping("/status")
    fun getMockStatusCode(@RequestParam("code") statusCode: Int): ResponseEntity<Unit> {
        return ResponseEntity.status(statusCode).build();
    }
}
