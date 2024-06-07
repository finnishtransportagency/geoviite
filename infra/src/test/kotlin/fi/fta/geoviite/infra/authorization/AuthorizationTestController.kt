package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.aspects.GeoviiteController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping

const val OK = "OK"

@GeoviiteController
class AuthorizationTestController @Autowired constructor() {

    @GetMapping("/test-auth/public")
    fun testPublic() = OK

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/test-auth/read")
    fun testRead() = OK

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @GetMapping("/test-auth/write")
    fun testWrite() = OK

    @PreAuthorize("hasAuthority('there-is-no-such-privilege')")
    @GetMapping("/test-auth/fail")
    fun testFail() = OK
}
