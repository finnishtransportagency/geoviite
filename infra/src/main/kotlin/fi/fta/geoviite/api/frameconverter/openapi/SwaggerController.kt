package fi.fta.geoviite.api.frameconverter.openapi

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_API_SWAGGER
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@GeoviiteExtApiController([])
@Hidden // Hide these controller paths from generated OpenApi definitions
class SwaggerController {

    val allowedResourcePrefixes = listOf("geoviite", "rata-vkm")

    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping(
        "/geoviite",
        "/geoviite/",
        "/geoviite/swagger-ui",
        "/geoviite/swagger-ui/",
        "/geoviite/swagger-ui/index.html",
        params = ["!url"],
    )
    fun sendGeoviiteSwaggerIndexRedirect(response: HttpServletResponse) {
        response.sendRedirect("/geoviite/swagger-ui/index.html?url=/geoviite/v3/api-docs/geoviite")
    }

    // TODO Missing @Profile
    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping(
        "/geoviite/dev",
        "/geoviite/dev/",
        "/geoviite/dev/swagger-ui",
        "/geoviite/dev/swagger-ui/",
        "/geoviite/dev/swagger-ui/index.html",
        params = ["!url"],
    )
    fun sendGeoviiteDevSwaggerIndexRedirect(response: HttpServletResponse) {
        response.sendRedirect("/geoviite/dev/swagger-ui/index.html?url=/geoviite/dev/v3/api-docs/geoviite-dev")
    }

    @PreAuthorize(AUTH_API_FRAME_CONVERTER)
    @GetMapping(
        "/rata-vkm",
        "/rata-vkm/",
        "/rata-vkm/swagger-ui",
        "/rata-vkm/swagger-ui/",
        "/rata-vkm/swagger-ui/index.html",
        params = ["!url"],
    )
    fun sendRataVkmSwaggerIndexRedirect(response: HttpServletResponse) {
        response.sendRedirect("/rata-vkm/swagger-ui/index.html?url=/rata-vkm/static/openapi-rata-vkm-v1.yml")
    }

    @PreAuthorize(AUTH_API_FRAME_CONVERTER)
    @GetMapping(
        "/rata-vkm/dev",
        "/rata-vkm/dev/",
        "/rata-vkm/dev/swagger-ui",
        "/rata-vkm/dev/swagger-ui/",
        "/rata-vkm/dev/swagger-ui/index.html",
        params = ["!url"],
    )
    fun sendRataVkmDevSwaggerIndexRedirect(response: HttpServletResponse) {
        response.sendRedirect("/rata-vkm/dev/swagger-ui/index.html?url=/rata-vkm/dev/static/openapi-rata-vkm-v1.yml")
    }

    // Resource redirects (eg. swagger-ui javascript and css files)
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/{prefix}/swagger-ui/{path}")
    fun internallyRedirectGeoviiteSwaggerResources(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable prefix: String,
        @PathVariable path: String,
    ) {
        if (allowedResourcePrefixes.contains(prefix)) {
            request.getRequestDispatcher(request.requestURI.removePrefix("/$prefix")).forward(request, response)
        } else {
            response.status = HttpServletResponse.SC_NOT_FOUND // Or another status code, if needed
        }
    }

    // TODO Missing @Profile
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/{prefix}/dev/swagger-ui/{path}")
    fun internallyRedirectGeoviiteDevSwaggerResources(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable prefix: String,
        @PathVariable path: String,
    ) {
        if (allowedResourcePrefixes.contains(prefix)) {
            request.getRequestDispatcher(request.requestURI.removePrefix("/$prefix/dev")).forward(request, response)
        } else {
            response.status = HttpServletResponse.SC_NOT_FOUND // Or another status code, if needed
        }
    }
}
