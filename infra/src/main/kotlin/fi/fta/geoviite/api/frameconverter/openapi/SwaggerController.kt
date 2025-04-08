package fi.fta.geoviite.api.frameconverter.openapi

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@GeoviiteExtApiController([])
@Hidden // Hide these controller paths from generated OpenApi definitions
class SwaggerController {

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

    // Resource redirects (eg. swagger-ui javascript and css files)
    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping("/geoviite/swagger-ui/{path}")
    fun internallyRedirectGeoviiteSwaggerResources(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable path: String,
    ) {
        request.getRequestDispatcher(request.requestURI.removePrefix("/geoviite")).forward(request, response)
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
    @GetMapping("/rata-vkm/swagger-ui/{path}")
    fun internallyRedirectRataVkmSwaggerResources(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable path: String,
    ) {
        request.getRequestDispatcher(request.requestURI.removePrefix("/rata-vkm")).forward(request, response)
    }
}
