package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.error.InputValidationException

val lineBreakRegex = Regex("[\n\r\t]")
const val UNSAFE_LOG_CHARACTERS = "[^A-Za-zäöÄÖåÅ0-9_-+!?.:;', �/]"

const val UNIX_LINEBREAK = "\n"
const val LEGACY_LINEBREAK = "\r" // Possibly used in older files were text may be copied and pasted from.
const val WINDOWS_LINEBREAK = "\r\n"

// Order matters due to both Windows style & legacy linebreaks containing the same special
// character.
val linebreakNormalizationRegex = Regex("$WINDOWS_LINEBREAK|$LEGACY_LINEBREAK")

const val UNSAFE_REPLACEMENT = "�"
const val LINE_BREAK_REPLACEMENT = "^"

const val DEFAULT_LOG_MAX_LENGTH = 100
const val DEFAULT_EXCEPTION_MAX_LENGTH = 100

inline fun <reified T> parsePrefixedInt(prefix: String, value: String): Int =
    parsePrefixed<T>(prefix, value).toIntOrNull()
        ?: failInput<T>(value) { "Invalid string value: prefix=$prefix value=${formatForException(value)}" }

inline fun <reified T> parsePrefixed(prefix: String, value: String): String =
    if (value.startsWith(prefix)) value.substring(prefix.length)
    else failInput<T>(value) { "Invalid string prefix: prefix=$prefix value=${formatForException(value)}" }

fun equalsIgnoreCaseAndWhitespace(s1: String, s2: String) =
    s1.filterNot(Char::isWhitespace).equals(s2.filterNot(Char::isWhitespace), ignoreCase = false)

fun formatForException(input: String, maxLength: Int = DEFAULT_EXCEPTION_MAX_LENGTH) = formatForLog(input, maxLength)

fun formatForLog(input: String, maxLength: Int = DEFAULT_LOG_MAX_LENGTH): String =
    "\"${limitLength(input, maxLength).let(::removeLogUnsafe).let(::removeLinebreaks)}\""

fun limitLength(input: String, maxLength: Int) =
    if (input.length <= maxLength) input else "${input.take(maxLength - 2)}.."

fun removeLogUnsafe(input: String) = input.replace(UNSAFE_LOG_CHARACTERS, UNSAFE_REPLACEMENT)

fun removeLinebreaks(input: String) = input.replace(lineBreakRegex, LINE_BREAK_REPLACEMENT)

fun normalizeLinebreaksToUnixFormat(input: String) = input.replace(linebreakNormalizationRegex, UNIX_LINEBREAK)

fun isSanitized(stringValue: String, regex: Regex, length: ClosedRange<Int>? = null, allowBlank: Boolean = true) =
    (length == null || stringValue.length in length) &&
        (allowBlank || stringValue.isNotBlank()) &&
        stringValue.matches(regex)

inline fun <reified T> assertSanitized(
    stringValue: String,
    regex: Regex,
    length: IntRange? = null,
    allowBlank: Boolean = true,
) {
    length?.let { l -> assertLength<T>(stringValue, l) }
    assertInput<T>(allowBlank || stringValue.isNotBlank(), stringValue) {
        "Invalid (blank) value for ${T::class.simpleName}: ${formatForException(stringValue)}"
    }
    assertInput<T>(stringValue.matches(regex), stringValue) {
        "Invalid characters in ${T::class.simpleName}: ${formatForException(stringValue)}"
    }
}

inline fun <reified T> assertLength(
    value: String,
    length: IntRange,
    getError: () -> String = {
        "Invalid length for ${T::class.simpleName} ${value.length} not in [${length.first}..${length.last}]"
    },
): Unit = assertInput<T>(value.length in length, value, getError)

inline fun <reified T> assertInput(condition: Boolean, value: String, lazyMessage: () -> String) {
    if (!condition) failInput<T>(value, lazyMessage)
}

inline fun <reified T> failInput(value: String, lazyMessage: () -> String): Nothing =
    throw InputValidationException(lazyMessage(), T::class, value)
