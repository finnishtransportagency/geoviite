package fi.fta.geoviite.infra.util

fun <T> produceIfNot(condition: Boolean, producer: () -> T): T? = if (!condition) producer() else null

fun <T> produceIf(condition: Boolean, producer: () -> T): T? = if (condition) producer() else null

inline fun <T> T?.alsoIfNull(action: () -> Unit): T? {
    if (this == null) action()
    return this
}

fun all(vararg conditions: () -> Boolean): Boolean = conditions.all { it() }
