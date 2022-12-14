package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.error.InputValidationException


val lineBreakRegex = Regex("[\n\r\t]")
const val UNSAFE_LOG_CHARACTERS = "[^A-Za-zäöÄÖåÅ0-9_-+!?.:;', �/]"

const val UNSAFE_REPLACEMENT = "�"
const val LINE_BREAK_REPLACEMENT = "^"

const val DEFAULT_LOG_MAX_LENGTH = 100
const val DEFAULT_EXCEPTION_MAX_LENGTH = 100

fun parsePrefixedInt(prefix: String, value: String): Int = parsePrefixed(prefix, value).toIntOrNull()
    ?: throw IllegalArgumentException("Invalid string value: prefix=$prefix value=${formatForException(value)}")

fun parsePrefixed(prefix: String, value: String): String =
    if (value.startsWith(prefix)) value.substring(prefix.length)
    else throw IllegalArgumentException("Invalid string prefix: prefix=$prefix value=${formatForException(value)}")

fun equalsIgnoreCaseAndWhitespace(s1: String, s2: String) =
    s1.filterNot(Char::isWhitespace).equals(s2.filterNot(Char::isWhitespace), ignoreCase = false)

fun formatForException(input: String, maxLength: Int = DEFAULT_EXCEPTION_MAX_LENGTH) = formatForLog(input, maxLength)

fun formatForLog(input: String, maxLength: Int = DEFAULT_LOG_MAX_LENGTH) =
    "\"${
    limitLength(input, maxLength)
        .let(::removeLogUnsafe)
        .let(::removeLinebreaks)
    }\""

fun limitLength(input: String, maxLength: Int) =
    if (input.length <= maxLength) input else "${input.take(maxLength-2)}.."

fun removeLogUnsafe(input: String) = input.replace(UNSAFE_LOG_CHARACTERS, UNSAFE_REPLACEMENT)

fun removeLinebreaks(input: String) = input.replace(lineBreakRegex, LINE_BREAK_REPLACEMENT)

fun isSanitized(
    stringValue: String,
    regex: Regex,
    length: ClosedRange<Int>? = null,
    allowBlank: Boolean = true,
) = (length == null || stringValue.length in length)
        && (allowBlank || stringValue.isNotBlank())
        && stringValue.matches(regex)

inline fun <reified T> assertSanitized(
    stringValue: String,
    regex: Regex,
    length: ClosedRange<Int>? = null,
    allowBlank: Boolean = true,
) {
    assertInput<T>(length == null || stringValue.length in length) {
        "Invalid length for ${T::class.simpleName} ${stringValue.length} not in [${length?.start}..${length?.endInclusive}]"
    }
    assertInput<T>(allowBlank || stringValue.isNotBlank()) {
        "Invalid (blank) value for ${T::class.simpleName}: ${formatForException(stringValue)}"
    }
    assertInput<T>(stringValue.matches(regex)) {
        "Invalid characters in ${T::class.simpleName}: ${formatForException(stringValue)}"
    }
}

inline fun <reified T> assertInput(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw InputValidationException(message = lazyMessage(), type = T::class)
}
