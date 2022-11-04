package fi.fta.geoviite.infra.authorization

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

const val OK = "OK"

@RestController
class AuthorizationTestController @Autowired constructor() {

    @GetMapping("/test-auth/public")
    fun testPublic() = OK

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/test-auth/read")
    fun testRead() = OK

    @PreAuthorize(AUTH_ALL_WRITE)
    @GetMapping("/test-auth/write")
    fun testWrite() = OK

    @PreAuthorize("hasAuthority('there-is-no-such-privilege')")
    @GetMapping("/test-auth/fail")
    fun testFail() = OK
}
