package fi.fta.geoviite.infra.util

import kotlin.reflect.KClass

data class StringSanitizer(val type: KClass<*>, val allowedCharacters: String, val allowedLength: IntRange? = null) {
    val safeStringRegex by lazy { Regex("^[$allowedCharacters]*\$") }
    val unsafeCharactersRegex by lazy { Regex("[^$allowedCharacters]") }

    fun assertSanitized(value: String) = assertSanitized(type, value, safeStringRegex, allowedLength)

    fun assertTrimmed(value: String) = assertTrimmed(type, value)

    fun sanitize(value: String) = sanitize(value, unsafeCharactersRegex, allowedLength?.last)

    fun isSanitized(value: String) = isSanitized(value, safeStringRegex, allowedLength)
}
