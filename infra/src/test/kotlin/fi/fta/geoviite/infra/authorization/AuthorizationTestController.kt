package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.aspects.GeoviiteController
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping

const val OK = "OK"

@GeoviiteController("/test-auth")
class AuthorizationTestController {

    @GetMapping("/public") fun testPublic() = OK

    @PreAuthorize(AUTH_BASIC) @GetMapping("/read") fun testRead() = OK

    @PreAuthorize(AUTH_EDIT_LAYOUT) @GetMapping("/write") fun testWrite() = OK

    @PreAuthorize("hasAuthority('there-is-no-such-privilege')") @GetMapping("/fail") fun testFail() = OK
}
