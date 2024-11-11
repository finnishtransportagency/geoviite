package fi.fta.geoviite.infra.testing

import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("testing-controllers")
@RestController
@RequestMapping("/testing")
class GeoviiteTestingController {

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/stack-trace")
    fun testStackTrace() {
        error("This is a stack trace logging test")
    }
}
