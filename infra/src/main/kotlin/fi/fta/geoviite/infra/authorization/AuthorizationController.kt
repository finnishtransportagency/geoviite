package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.cloudfront.CloudFrontCookies
import fi.fta.geoviite.infra.cloudfront.CookieSigner
import fi.fta.geoviite.infra.error.ApiUnauthorizedException
import fi.fta.geoviite.infra.util.Code
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@GeoviiteController("/authorization")
class AuthorizationController @Autowired constructor(private val signer: CookieSigner) {

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/own-details")
    fun getOwnDetails(): User {
        return SecurityContextHolder.getContext().authentication.principal as User
    }

    @PreAuthorize(AUTH_BASIC)
    @PostMapping("/desired-role")
    fun setDesiredRole(
        @RequestParam("code") code: Code,
        response: HttpServletResponse,
    ): Code {
        val roleCookie =
            Cookie(DESIRED_ROLE_COOKIE_NAME, code.toString()).apply {
                path = "/"
                isHttpOnly = true
                secure = true
            }

        response.addCookie(roleCookie)
        return code
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/cf-cookies")
    fun getCloudFrontCookies(
        @RequestParam("redirect") redirectPath: String = ""
    ): ResponseEntity<CloudFrontCookies> {
        val cloudFrontCookies = signer.createSignedCustomCookies()
        val httpHeaders = HttpHeaders()

        val trimmedRedirectPath = redirectPath.trimStart('/')

        httpHeaders.add("Set-Cookie", cloudFrontCookies.policy)
        httpHeaders.add("Set-Cookie", cloudFrontCookies.signature)
        httpHeaders.add("Set-Cookie", cloudFrontCookies.keyPairId)
        httpHeaders.add("Location", "https://${cloudFrontCookies.domain}/$trimmedRedirectPath")

        return ResponseEntity.status(HttpStatus.FOUND).headers(httpHeaders).body(cloudFrontCookies)
    }

    @PreAuthorize(AUTH_BASIC)
    @PutMapping("/not-allowed")
    fun csrfPutForbidden(): Nothing = throw ApiUnauthorizedException("not allowed")

    @PreAuthorize(AUTH_BASIC)
    @PostMapping("/not-allowed")
    fun csrfPostForbidden(): Nothing = throw ApiUnauthorizedException("not allowed")

    @PreAuthorize(AUTH_BASIC)
    @DeleteMapping("/not-allowed")
    fun csrfDeleteForbidden(): Nothing = throw ApiUnauthorizedException("not allowed")

    @PreAuthorize(AUTH_BASIC)
    @PatchMapping("/not-allowed")
    fun csrfPatchForbidden(): Nothing = throw ApiUnauthorizedException("not allowed")
}
