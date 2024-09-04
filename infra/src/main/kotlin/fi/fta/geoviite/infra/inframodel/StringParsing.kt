package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.formatForException
import fi.fta.geoviite.infra.util.formatForLog
import java.math.BigDecimal

val missingEnumValues = listOf("N/A", "").map { v -> v.uppercase() }
val missingNumericValues = listOf("NaN", "N/A", "null", "?", "").map { v -> v.uppercase() }
const val FIELD_DATA_SEPARATOR = "\\s+"

fun isMissingNumeric(value: String) = missingNumericValues.any { v -> v == value.uppercase() }

fun isMissingEnum(value: String) = missingEnumValues.any { v -> v == value.uppercase() }

fun parseOptionalBigDecimal(name: String, value: String): BigDecimal? =
    if (isMissingNumeric(value)) null else parseBigDecimal(name, value)

fun parseBigDecimal(name: String, value: String): BigDecimal = parseMandatory(name, value, String::toBigDecimal)

fun parseOptionalDouble(name: String, value: String): Double? =
    if (isMissingNumeric(value)) null else parseDouble(name, value)

fun parseDouble(name: String, value: String): Double = parseMandatory(name, value, String::toDouble)

fun parseOptionalInt(name: String, value: String): Int? = if (isMissingNumeric(value)) null else parseInt(name, value)

fun parseInt(name: String, value: String): Int = parseMandatory(name, value, String::toInt)

fun splitStringOnSpaces(string: String): List<String> = string.trim().split(Regex(FIELD_DATA_SEPARATOR))

inline fun <reified T : Enum<T>> parseOptionalEnum(name: String, value: String?): T? =
    value?.let { v -> if (isMissingEnum(v)) null else parseEnum<T>(name, v) }

inline fun <reified T : Enum<T>> parseEnum(name: String, value: String): T {
    return parseMandatory(name, value.uppercase(), ::enumValueOf)
}

fun parsePoint(name: String, x: String, y: String): Point =
    Point(x = parseDouble("$name X", x), y = parseDouble("$name Y", y))

inline fun <reified T> parseMandatory(name: String, value: String, function: (String) -> T): T {
    try {
        return function(value)
    } catch (e: Exception) {
        throw InputValidationException(
            message = "Cannot parse ${formatForException(name)} from value ${formatForException(value)}",
            type = T::class,
            value = value,
            cause = e,
        )
    }
}

// U+FFFD � REPLACEMENT CHARACTER used to replace an unknown, unrecognized, or unrepresentable
// character
const val REPLACEMENT_CHAR = '�'

inline fun <reified T> tryParseText(text: String, toTyped: (string: String) -> T) =
    try {
        if (text.contains(REPLACEMENT_CHAR)) {
            logger.warn("${T::class.simpleName} string ${formatForLog(text)} contains UTF-8 replacement char.")
            toTyped(text.filter { c -> c != REPLACEMENT_CHAR })
        } else {
            toTyped(text)
        }
    } catch (e: InputValidationException) {
        logger.warn("Failed to parse ${T::class.simpleName} from string: ${e.message}")
        null
    }
