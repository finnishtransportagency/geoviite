package fi.fta.geoviite.infra.logging

import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.VERSION_FETCH
import fi.fta.geoviite.infra.util.formatForLog
import fi.fta.geoviite.infra.util.resetCollected
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import org.apache.logging.log4j.ThreadContext
import org.slf4j.Logger
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction

fun Logger.apiRequest(req: HttpServletRequest, requestIp: String) {
    if (isDebugEnabled) debug("Request: {}:{} ip={}", req.method, req.requestURL, requestIp)
}

fun Logger.apiResponse(
    req: HttpServletRequest,
    res: HttpServletResponse,
    requestIp: String,
    startTime: Instant,
    slowRequestThreshold: Duration,
) {
    val timeMs = Duration.between(startTime, Instant.now()).toMillis()
    val timingMap = resetCollected()
    if (timeMs > slowRequestThreshold.toMillis()) {
        warn("Slow response: ${responseParams(req, res, requestIp, startTime, timingMap)}")
    } else if (isInfoEnabled) {
        info("Response: ${responseParams(req, res, requestIp, startTime, timingMap)}")
    }
}

private fun responseParams(
    req: HttpServletRequest,
    res: HttpServletResponse,
    requestIp: String,
    startTime: Instant,
    timingMap: Map<String, Duration>,
): String {
    val timeMs = Duration.between(startTime, Instant.now()).toMillis()
    val timingString = if (timingMap.isEmpty()) "[no timings]" else "[$timingMap]"
    return paramsToLog(
            "status" to res.status,
            "time" to "$timeMs ms",
            "contentType" to res.contentType,
            "ip" to requestIp,
            "request" to "${req.method}:${req.requestURL}",
            "timings" to timingString,
        )
        .joinToString(" ")
}

enum class AccessType {
    VERSION_FETCH,
    FETCH,
    INSERT,
    UPDATE,
    UPSERT,
    DELETE,
}

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

fun Logger.daoAccess(method: String, params: List<Pair<String, *>>, returnValue: Any?) {
    info("method={} params={} returnValue={}", method, paramsToLog(params), returnValueToLog(returnValue))
}

fun Logger.apiCall(method: String, params: List<Pair<String, *>>) {
    if (isInfoEnabled) info("method={} params={}", method, paramsToLog(params))
}

fun Logger.apiResult(method: String, params: List<Pair<String, *>>) {
    if (isInfoEnabled) info("method={} result={}", method, paramsToLog(params))
}

fun Logger.serviceCall(method: String, params: List<Pair<String, *>>) {
    if (isDebugEnabled) debug("method={} params={}", method, paramsToLog(params))
}

fun paramsToLog(vararg params: Pair<String, *>): List<String> =
    params.map { p ->
        "${p.first}=${p.second?.let { obj ->
            formatForLog(if (obj is Loggable) obj.toLog() else obj.toString(), 1000)
        }}"
    }

fun paramsToLog(params: List<Pair<String, *>>): List<String> =
    params.map { p ->
        "${p.first}=${p.second?.let { obj ->
            formatForLog(if (obj is Loggable) obj.toLog() else obj.toString(), 1000)
        }}"
    }

fun returnValueToLog(returnValue: Any?): String {
    return formatForLog(if (returnValue is Loggable) returnValue.toLog() else returnValue.toString(), 1000)
}

fun Logger.integrationCall(method: String, vararg params: Pair<String, *>) {
    info("method=$method params=${paramsToLog(*params)}")
}

fun Logger.integrationCall(request: ClientRequest) {
    info(
        "Sending API request to external service: " +
            "${request.logPrefix()} method=${request.method()} url=${request.url()}"
    )
}

fun Logger.integrationCall(response: ClientResponse) {
    info("External service responded: ${response.logPrefix()} status=${response.statusCode()}")
}

fun copyThreadContextToReactiveResponseThread(): ExchangeFilterFunction {
    return ExchangeFilterFunction { request: ClientRequest, next: ExchangeFunction ->
        val originalThreadContextMap = ThreadContext.getContext()?.toMap() ?: emptyMap()
        val requestThreadContext = ThreadContext.getContext()

        next
            .exchange(request)
            .doOnNext { _ -> requestThreadContext?.let { ctx -> ThreadContext.putAll(ctx) } }
            .doFinally {
                ThreadContext.clearMap()
                ThreadContext.putAll(originalThreadContextMap)
            }
    }
}
