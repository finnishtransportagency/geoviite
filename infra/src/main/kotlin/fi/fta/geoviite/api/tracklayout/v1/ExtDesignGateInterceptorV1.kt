package fi.fta.geoviite.api.tracklayout.v1

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.HandlerInterceptor

class ExtDesignGateInterceptorV1 : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        response.sendError(HttpStatus.NOT_FOUND.value())
        return false
    }
}
