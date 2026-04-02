package fi.fta.geoviite.api.openapi

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_FRAME_CONVERTER
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_API_SWAGGER
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

// Browser-facing api-docs paths: each path is under its own prefix so that integrators
// proxying only /geoviite/** or /rata-vkm/** can reach the API definition for their portion.
// These intentionally don't end with the SpringDoc base path (/v3/api-docs) to avoid
// interfering with SpringDoc's auto-generated server URL calculation.
const val OPENAPI_GEOVIITE_PATH = "/geoviite/openapi"
const val OPENAPI_RATAVKM_PATH = "/rata-vkm/openapi"

const val OPENAPI_GEOVIITE_DEV_PATH = "/geoviite/dev/openapi"
const val OPENAPI_RATAVKM_DEV_PATH = "/rata-vkm/dev/openapi"

// Actual SpringDoc-served paths (springdoc.api-docs.path + group name). These are internal
// implementation details — browsers never fetch these directly.
private const val SPRINGDOC_GEOVIITE_PATH = "/v3/api-docs/geoviite"
private const val SPRINGDOC_RATAVKM_PATH = "/v3/api-docs/rata-vkm"
private const val SPRINGDOC_GEOVIITE_DEV_PATH = "/dev/v3/api-docs/geoviite-dev"
private const val SPRINGDOC_RATAVKM_DEV_PATH = "/dev/v3/api-docs/rata-vkm-dev"

val allowedResourcePrefixes = listOf("/", "/geoviite", "/rata-vkm", "/geoviite/dev", "/rata-vkm/dev")

@GeoviiteExtApiController([])
@Hidden // These controller paths are hidden from the dynamically generated OpenApi definitions.
@ConditionalOnProperty(name = ["springdoc.swagger-ui.enabled"], havingValue = "true")
class SwaggerController {

    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping("/geoviite", "/geoviite/", "/geoviite/swagger-ui", "/geoviite/swagger-ui/")
    fun sendGeoviiteSwaggerIndexRedirect(response: HttpServletResponse) {
        sendRedirect(response, "/geoviite/swagger-ui/index.html")
    }

    // Redirect root/no-prefix access to the /geoviite swagger. Full-subdomain proxies land here
    // and get redirected to the prefixed swagger so that paths in the docs match the actual API.
    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping("", "/", "/swagger-ui", "/swagger-ui/")
    fun sendRootSwaggerRedirect(response: HttpServletResponse) {
        sendRedirect(response, "/geoviite/swagger-ui/index.html")
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping("/geoviite/dev", "/geoviite/dev/", "/geoviite/dev/swagger-ui", "/geoviite/dev/swagger-ui/")
    fun sendGeoviiteDevSwaggerIndexRedirect(response: HttpServletResponse) {
        sendRedirect(response, "/geoviite/dev/swagger-ui/index.html")
    }

    @PreAuthorize(AUTH_API_FRAME_CONVERTER)
    @GetMapping("/rata-vkm", "/rata-vkm/", "/rata-vkm/swagger-ui", "/rata-vkm/swagger-ui/")
    fun sendRataVkmSwaggerIndexRedirect(response: HttpServletResponse) {
        sendRedirect(response, "/rata-vkm/swagger-ui/index.html")
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_FRAME_CONVERTER)
    @GetMapping("/rata-vkm/dev", "/rata-vkm/dev/", "/rata-vkm/dev/swagger-ui", "/rata-vkm/dev/swagger-ui/")
    fun sendRataVkmDevSwaggerIndexRedirect(response: HttpServletResponse) {
        sendRedirect(response, "/rata-vkm/dev/swagger-ui/index.html")
    }

    // Serve a per-prefix swagger-initializer.js with the correct API docs URL baked in.
    // This replaces the previous approach of passing ?url= query params to swagger-ui, which required
    // queryConfigEnabled and prevented layout customization. Now the URL is determined server-side.
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/geoviite/swagger-ui/swagger-initializer.js")
    fun serveGeoviiteSwaggerInitializer(response: HttpServletResponse) {
        serveSwaggerInitializer(response, OPENAPI_GEOVIITE_PATH)
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/geoviite/dev/swagger-ui/swagger-initializer.js")
    fun serveGeoviiteDevSwaggerInitializer(response: HttpServletResponse) {
        serveSwaggerInitializer(response, OPENAPI_GEOVIITE_DEV_PATH)
    }

    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/rata-vkm/swagger-ui/swagger-initializer.js")
    fun serveRataVkmSwaggerInitializer(response: HttpServletResponse) {
        serveSwaggerInitializer(response, OPENAPI_RATAVKM_PATH)
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/rata-vkm/dev/swagger-ui/swagger-initializer.js")
    fun serveRataVkmDevSwaggerInitializer(response: HttpServletResponse) {
        serveSwaggerInitializer(response, OPENAPI_RATAVKM_DEV_PATH)
    }

    // Forward prefixed api-docs requests to the actual SpringDoc-served paths.
    // SpringDoc's api-docs base path is global (/v3/api-docs), but each swagger-ui serves its
    // api-docs URL under its own prefix so that integrators proxying only /geoviite/** or
    // /rata-vkm/** can reach the API definition for their portion.
    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping(OPENAPI_GEOVIITE_PATH)
    fun forwardGeoviiteApiDocs(request: HttpServletRequest, response: HttpServletResponse) {
        request.getRequestDispatcher(SPRINGDOC_GEOVIITE_PATH).forward(request, response)
    }

    @PreAuthorize(AUTH_API_FRAME_CONVERTER)
    @GetMapping(OPENAPI_RATAVKM_PATH)
    fun forwardRataVkmApiDocs(request: HttpServletRequest, response: HttpServletResponse) {
        request.getRequestDispatcher(SPRINGDOC_RATAVKM_PATH).forward(request, response)
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_GEOMETRY)
    @GetMapping(OPENAPI_GEOVIITE_DEV_PATH)
    fun forwardGeoviiteDevApiDocs(request: HttpServletRequest, response: HttpServletResponse) {
        request.getRequestDispatcher(SPRINGDOC_GEOVIITE_DEV_PATH).forward(request, response)
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_FRAME_CONVERTER)
    @GetMapping(OPENAPI_RATAVKM_DEV_PATH)
    fun forwardRataVkmDevApiDocs(request: HttpServletRequest, response: HttpServletResponse) {
        request.getRequestDispatcher(SPRINGDOC_RATAVKM_DEV_PATH).forward(request, response)
    }

    // Resource forwarding: serve swagger-ui static files (JS bundles, CSS) from springdoc's webjar.
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/{prefix}/swagger-ui/{path}")
    fun forwardSwaggerResources(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable prefix: String,
        @PathVariable path: String,
    ) {
        forwardSwaggerResource("/$prefix", request, response)
    }

    @Profile("ext-api-dev-swagger")
    @PreAuthorize(AUTH_API_SWAGGER)
    @GetMapping("/{prefix}/dev/swagger-ui/{path}")
    fun forwardDevSwaggerResources(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable prefix: String,
        @PathVariable path: String,
    ) {
        forwardSwaggerResource("/$prefix/dev", request, response)
    }
}

// To hide the schemas section, add this: defaultModelsExpandDepth: -1
private fun serveSwaggerInitializer(response: HttpServletResponse, apiDocsUrl: String) {
    response.contentType = "application/javascript;charset=UTF-8"
    response.writer.write(
        """
        |window.onload = function() {
        |  window.ui = SwaggerUIBundle({
        |    url: "$apiDocsUrl",
        |    dom_id: '#swagger-ui',
        |    deepLinking: true,
        |    presets: [SwaggerUIBundle.presets.apis],
        |    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
        |    layout: "BaseLayout",
        |    validatorUrl: "none",
        |    syntaxHighlight: { activated: false },
        |  });
        |};
        """
            .trimMargin()
    )
}

private fun forwardSwaggerResource(
    prefixWithLeadingSlash: String,
    request: HttpServletRequest,
    response: HttpServletResponse,
) {
    if (!allowedResourcePrefixes.contains(prefixWithLeadingSlash)) {
        response.status = HttpServletResponse.SC_NOT_FOUND
        return
    }

    val dispatcherUri =
        if (prefixWithLeadingSlash == "/") prefixWithLeadingSlash
        else request.requestURI.removePrefix(prefixWithLeadingSlash)

    request.getRequestDispatcher(dispatcherUri).forward(request, response)
}

// Although HttpServletResponse has the .sendRedirect-method, it also uses the request URL within the Location header.
// This causes issues in environments where the URL of the request is an internal URL instead of the one that the
// user has for example in their browser (the redirects are sent to the inaccessible internal URL).
private fun sendRedirect(response: HttpServletResponse, redirectPath: String) {
    response.status = HttpServletResponse.SC_FOUND
    response.setHeader("Location", redirectPath)
}
