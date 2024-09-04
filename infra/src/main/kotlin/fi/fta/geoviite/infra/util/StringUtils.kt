package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.error.InputValidationException
import kotlin.reflect.KClass

const val UNIX_LINEBREAK = "\n"
const val LEGACY_LINEBREAK = "\r" // Possibly used in older files were text may be copied and pasted from.
const val WINDOWS_LINEBREAK = "\r\n"

const val UNSAFE_REPLACEMENT = "�" // Replace unsafe characters with a clear placeholder
const val LINE_BREAK_REPLACEMENT = "\\n" // Escape line breaks to prevent log forging

const val SAFE_LOG_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\\\\\-–—+(){}.,´`'\"/*#<>\\[\\]:;!?&=€$£@%~$UNSAFE_REPLACEMENT"

const val DEFAULT_LOG_MAX_LENGTH = 100
const val DEFAULT_EXCEPTION_MAX_LENGTH = 100

val lineBreakRegex = Regex("[\n\r\t]")
val logUnsafeRegex = Regex("[^$SAFE_LOG_CHARACTERS]")
// Order matters due to both Windows style & legacy linebreaks containing the same special
// character.
val linebreakNormalizationRegex = Regex("$WINDOWS_LINEBREAK|$LEGACY_LINEBREAK")

inline fun <reified T> parsePrefixedInt(prefix: String, value: String): Int =
    parsePrefixed<T>(prefix, value).toIntOrNull()
        ?: failInput(T::class, value) { "Invalid string value: prefix=$prefix value=${formatForException(value)}" }

inline fun <reified T> parsePrefixed(prefix: String, value: String): String =
    if (value.startsWith(prefix)) value.substring(prefix.length)
    else failInput(T::class, value) { "Invalid string prefix: prefix=$prefix value=${formatForException(value)}" }

fun formatForException(input: String, maxLength: Int = DEFAULT_EXCEPTION_MAX_LENGTH) = formatForLog(input, maxLength)

fun sanitize(input: String, regex: Regex, maxLength: Int?): String =
    input.replace(regex, "").let { s -> if (maxLength != null) s.take(maxLength) else s }

fun formatForLog(input: String, maxLength: Int = DEFAULT_LOG_MAX_LENGTH): String =
    "\"${limitLength(input, maxLength).let(::removeLogUnsafe).let(::removeLinebreaks)}\""

fun limitLength(input: String, maxLength: Int) =
    if (input.length <= maxLength) input else "${input.take(maxLength - 2)}.."

fun removeLogUnsafe(input: String) = input.replace(logUnsafeRegex, UNSAFE_REPLACEMENT)

fun removeLinebreaks(input: String) = input.replace(lineBreakRegex, LINE_BREAK_REPLACEMENT)

fun normalizeLinebreaksToUnixFormat(input: String) = input.replace(linebreakNormalizationRegex, UNIX_LINEBREAK)

fun isSanitized(stringValue: String, regex: Regex, length: ClosedRange<Int>? = null) =
    (length == null || stringValue.length in length) && stringValue.matches(regex)

inline fun <reified T> assertSanitized(stringValue: String, regex: Regex, length: IntRange? = null) =
    assertSanitized(T::class, stringValue, regex, length)

fun assertSanitized(type: KClass<*>, stringValue: String, regex: Regex, length: IntRange? = null) {
    length?.let { l -> assertLength(type, stringValue, l) }
    assertInput(type, stringValue.matches(regex), stringValue) {
        "Invalid characters in ${type.simpleName}: ${formatForException(stringValue)}"
    }
}

fun assertLength(
    type: KClass<*>,
    value: String,
    length: IntRange,
    getError: () -> String = {
        "Invalid length for ${type.simpleName} ${value.length} not in [${length.first}..${length.last}]"
    },
): Unit = assertInput(type, value.length in length, value, getError)

fun assertInput(type: KClass<*>, condition: Boolean, value: String, lazyMessage: () -> String) {
    if (!condition) failInput(type, value, lazyMessage)
}

fun failInput(type: KClass<*>, value: String, lazyMessage: () -> String): Nothing =
    throw InputValidationException(lazyMessage(), type, value)
