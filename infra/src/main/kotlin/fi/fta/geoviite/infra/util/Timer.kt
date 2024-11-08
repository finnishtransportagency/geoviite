package fi.fta.geoviite.infra.util

import correlationId
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private class Timer

val logger: Logger = LoggerFactory.getLogger(Timer::class.java)

fun <T> measureAndPrintLength(function: () -> T, title: String): T =
    measureLength({ duration -> printDuration(title, duration) }, function)

fun operationId() = correlationId.getOrNull() ?: "DEFAULT"

val collectedTimes: MutableMap<String, MutableMap<String, Duration>> = ConcurrentHashMap()

fun logAndResetCollected() {
    val map = collectedTimes.remove(operationId())
    map?.forEach { (key: String, value: Duration) -> printDuration(key, value) }
}

fun resetCollected(): Map<String, Duration> {
    return collectedTimes.remove(operationId()) ?: mapOf()
}

fun <T> measureAndCollect(title: String, function: () -> T): T =
    measureLength({ duration -> collectDuration(title, duration) }, function)

fun <T> measureLength(timeConsumer: (duration: Duration) -> Unit, function: () -> T): T {
    val before = Date().toInstant()
    val value = function()
    val after = Date().toInstant()
    timeConsumer(Duration.between(before, after))
    return value
}

fun collectDuration(title: String, duration: Duration) {
    collectedTimes.compute(operationId()) { _, previousMap ->
        val map = previousMap ?: ConcurrentHashMap()
        map.compute(title) { _, previousDuration -> previousDuration?.plus(duration) ?: duration }
        map
    }
}

fun printDuration(title: String, duration: Duration) =
    logger.info(title + ": " + duration.toMillis().toString() + " ms")
