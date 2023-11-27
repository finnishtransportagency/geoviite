package fi.fta.geoviite.infra.logging

import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.VERSION_FETCH
import fi.fta.geoviite.infra.util.formatForLog
import fi.fta.geoviite.infra.util.resetCollected
import org.slf4j.Logger
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import java.time.Duration
import java.time.Instant
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KClass

fun Logger.apiRequest(req: HttpServletRequest, requestIp: String) {
    if (isDebugEnabled) debug("Request: {}:{} ip={}", req.method, req.requestURL, requestIp)
}

fun Logger.apiResponse(req: HttpServletRequest, res: HttpServletResponse, requestIp: String, startTime: Instant) {
    val timeMs = Duration.between(startTime, Instant.now()).toMillis()
    val timingMap = resetCollected()
    val timingString = if (timingMap.isEmpty()) " [no timings]" else " [$timingMap]"
    if (isInfoEnabled) info("Response: status=${res.status} time=${timeMs}ms${timingString} contentType=${res.contentType} ip=$requestIp request=${req.method}:${req.requestURL}")
}

enum class AccessType { VERSION_FETCH, FETCH, INSERT, UPDATE, UPSERT, DELETE }

fun Logger.daoAccess(accessType: AccessType, objectType: KClass<*>, vararg ids: Any) {
    daoAccess(accessType, objectType, ids.toList())
}

fun Logger.daoAccess(accessType: AccessType, objectType: KClass<*>, ids: List<Any>) {
    daoAccess(accessType, objectType.simpleName ?: objectType.toString(), ids)
}

fun Logger.daoAccess(accessType: AccessType, objectType: String, vararg ids: Any) {
    daoAccess(accessType, objectType, ids.toList())
}

fun Logger.daoAccess(accessType: AccessType, objectType: String, ids: List<Any>) {
    if (accessType == VERSION_FETCH || accessType == FETCH) {
        if (isDebugEnabled) debug("accessType={} objectType={} ids={}", accessType, objectType, ids)
    } else {
        if (isInfoEnabled) info("accessType=$accessType objectType=$objectType ids=$ids")
    }
}

fun Logger.apiCall(method: String, vararg params: Pair<String, *>) {
    if (isInfoEnabled) info("method=$method params=${paramsToLog(params)}")
}

fun Logger.serviceCall(method: String, vararg params: Pair<String, *>) {
    if (isDebugEnabled) debug("method={} params={}", method, paramsToLog(params))
}

fun paramsToLog(params: Array<out Pair<String, *>>): List<String> =
    params.map { p -> "${p.first}=${p.second?.let { obj ->
        formatForLog(formatForLog(if (obj is Loggable) obj.toLog() else obj.toString(), 1000))
    }}" }

fun Logger.integrationCall(method: String, vararg params: Pair<String, *>) {
    info("method=$method params=${paramsToLog(params)}")
}

fun Logger.integrationCall(request: ClientRequest) {
    info("Sending API request to external service: ${request.logPrefix()} method=${request.method()} url=${request.url()}")
}

fun Logger.integrationCall(response: ClientResponse) {
    info("External service responded: ${response.logPrefix()} status=${response.statusCode()}")
}
