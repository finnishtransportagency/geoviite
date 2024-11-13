package fi.fta.geoviite.infra.debugging

import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import fi.fta.geoviite.infra.error.ApiUnauthorizedException
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Profile("debug-controllers")
@RestController
class GeoviiteDebugController {

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/debug/stack-trace")
    fun debugStackTrace() {
        error("This is a stack trace logging test")
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/debug/token-expired")
    fun debugTokenExpired() {
        throw ApiUnauthorizedException(
            message = "debugging JWT token expiry",
            localizedMessageKey = "error.unauthorized.token-expired",
        )
    }
}
