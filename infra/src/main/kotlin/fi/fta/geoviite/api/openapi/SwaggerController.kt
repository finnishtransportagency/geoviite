package fi.fta.geoviite.api.openapi

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_API_SWAGGER
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

const val OPENAPI_GEOVIITE_PATH = "/geoviite/v3/api-docs/geoviite"
const val OPENAPI_GEOVIITE_NO_PREFIX_PATH = "/geoviite/v3/api-docs/geoviite-user-api"
const val OPENAPI_GEOVIITE_DEV_PATH = "/geoviite/dev/v3/api-docs/geoviite-dev"

const val OPENAPI_RATAVKM_PATH = "/rata-vkm/static/openapi-rata-vkm-v1.yml"
const val OPENAPI_RATAVKM_DEV_PATH = "/rata-vkm/dev/static/openapi-rata-vkm-v1.yml"

val allowedResourcePrefixes = listOf("/", "/geoviite", "/rata-vkm")

val allowedApiDefinitionPaths =
    listOf(
        OPENAPI_GEOVIITE_PATH,
        OPENAPI_GEOVIITE_NO_PREFIX_PATH,
        OPENAPI_GEOVIITE_DEV_PATH,
        OPENAPI_RATAVKM_PATH,
        OPENAPI_RATAVKM_DEV_PATH,
    )

@GeoviiteExtApiController([])
@Hidden // These controller paths are hidden from the dynamically generated OpenApi definitions.
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
        sendRedirect(response, "/geoviite/swagger-ui/index.html?url=$OPENAPI_GEOVIITE_PATH")
    }

    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping("", "/", "/swagger-ui", "/swagger-ui/", params = ["!url"])
    fun sendGeoviiteSwaggerNoPrefixIndexRedirect(response: HttpServletResponse) {
        sendRedirect(response, "/swagger-ui/index.html?url=$OPENAPI_GEOVIITE_NO_PREFIX_PATH")
    }

    @Profile("ext-api-dev-swagger")
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
        sendRedirect(response, "/geoviite/dev/swagger-ui/index.html?url=$OPENAPI_GEOVIITE_DEV_PATH")
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
        sendRedirect(response, "/rata-vkm/swagger-ui/index.html?url=$OPENAPI_RATAVKM_PATH")
    }

    @Profile("ext-api-dev-swagger")
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
        sendRedirect(response, "/rata-vkm/dev/swagger-ui/index.html?url=$OPENAPI_RATAVKM_DEV_PATH")
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
        swaggerResourceRequest("/$prefix", request, response)
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/{prefix}/dev/swagger-ui/{path}")
    fun internallyRedirectGeoviiteDevSwaggerResources(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable prefix: String,
        @PathVariable path: String,
    ) {
        swaggerResourceRequest("/$prefix/dev", request, response)
    }
}

private fun swaggerResourceRequest(
    prefixWithLeadingSlash: String,
    request: HttpServletRequest,
    response: HttpServletResponse,
) {
    val resourcePrefixOk = allowedResourcePrefixes.contains(prefixWithLeadingSlash)
    val apiDefinitionPathOk = request.getParameter("url")?.let(allowedApiDefinitionPaths::contains) ?: true

    if (resourcePrefixOk && apiDefinitionPathOk) {
        val dispatcherUri =
            if (prefixWithLeadingSlash == "/") prefixWithLeadingSlash
            else request.requestURI.removePrefix(prefixWithLeadingSlash)

        request.getRequestDispatcher(dispatcherUri).forward(request, response)
    } else {
        response.status = HttpServletResponse.SC_NOT_FOUND
    }
}

// Although HttpServletResponse has the .sendRedirect-method, it also uses the request URL within the Location header.
// This causes issues in environments were the URL of the request is an internal URL instead of the one that the
// user has for example in their browser (the redirects are sent to the inaccessible internal URL).
private fun sendRedirect(response: HttpServletResponse, redirectPath: String) {
    response.status = HttpServletResponse.SC_FOUND
    response.setHeader("Location", redirectPath)
}
