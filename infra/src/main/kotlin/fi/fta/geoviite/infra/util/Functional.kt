package fi.fta.geoviite.infra.util

fun <T> produceIfNot(condition: Boolean, producer: () -> T): T? =
    if (!condition) producer() else null

fun <T> produceIf(condition: Boolean, producer: () -> T): T? = if (condition) producer() else null
