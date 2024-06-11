package fi.fta.geoviite.infra.logging

import fi.fta.geoviite.infra.util.formatForLog
import fi.fta.geoviite.infra.util.resetCollected
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import java.time.Duration
import java.time.Instant
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

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
    ).joinToString(" ")
}


fun Logger.daoAccess(method: String, params: List<Pair<String, *>>, returnValue: Any?) {
    val debugLevelMethodPrefixes = arrayOf("fetch", "get", "list")
    if (!isDebugEnabled && debugLevelMethodPrefixes.any { prefix -> method.startsWith(prefix) }) {
        return
    }

    if (isInfoEnabled) {
        info(
            "method={} params={} returnValue={}",
            method,
            paramsToLog(params),
            returnValueToLog(returnValue),
        )
    }
}

fun Logger.apiCall(method: String, params: List<Pair<String, *>>) {
    if (isInfoEnabled) info("method={} params={}", method, paramsToLog(params))
}

fun Logger.serviceCall(method: String, params: List<Pair<String, *>>) {
    if (isDebugEnabled) debug("method={} params={}", method, paramsToLog(params))
}

fun paramsToLog(vararg params: Pair<String, *>): List<String> = paramsToLog(params.toList())

fun paramsToLog(params: List<Pair<String, *>>): List<String> {
    return params.map { p ->
        "${p.first}=${p.second?.let { obj ->
            formatForLog(obj.toLogFormat(), 1000)
        }}"
    }
}

fun returnValueToLog(returnValue: Any?): String {
    return formatForLog(returnValue.toLogFormat(), 1000)
}

fun Any?.toLogFormat(preferOnlyId: Boolean = false): String {
    if (preferOnlyId && this != null) {
        this.javaClass.kotlin.memberProperties
            .find { property -> property.name == "id" || property.name == "rowVersion" }
            ?.let { idProperty ->
                return idProperty.get(this).toString()
            }
    }

    return when (this) {
        is Loggable -> this.toLog()

        is List<*> -> this.joinToString(", ", "[", "]") { value ->
            value.toLogFormat(preferOnlyId = true)
        }

        is Array<*> -> this.joinToString(", ", "[", "]") {
            it.toLogFormat(preferOnlyId = true)
        }

        is Set<*> -> this.joinToString(", ", "{", "}") { value ->
            value.toLogFormat(preferOnlyId = true)
        }

        is Map<*, *> -> this.entries.joinToString(", ", "{", "}") { (key, value) ->
            "${key.toLogFormat()}=${value.toLogFormat(preferOnlyId = true)}"
        }

        else -> this.toString()
    }
}

fun Logger.integrationCall(method: String, vararg params: Pair<String, *>) {
    info("method=$method params=${paramsToLog(params.toList())}")
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
