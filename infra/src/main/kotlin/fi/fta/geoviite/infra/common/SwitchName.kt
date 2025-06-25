package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

data class SwitchName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<SwitchName>, CharSequence by value {

    companion object {
        val allowedLength = 1..50
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 \\-_/,."
        val sanitizer = StringSanitizer(SwitchName::class, ALLOWED_CHARACTERS, allowedLength)

        fun ofUnsafe(value: String) = value.trim().let(sanitizer::sanitize).let(::SwitchName)
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: SwitchName): Int = value.compareTo(other.value)

    fun equalsIgnoreCase(other: SwitchName): Boolean = value.equals(other.value, ignoreCase = true)
}

data class SwitchNamePrefix @JsonCreator(mode = DELEGATING) constructor(private val value: String) {

    companion object {
        private val prefixSeparators = setOf(' ', '_', '-')
        val allowedLength = 1..10
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå"
        val sanitizer = StringSanitizer(SwitchName::class, ALLOWED_CHARACTERS, allowedLength)

        fun tryParse(value: String): SwitchNamePrefix? =
            value
                .trim()
                .dropLastWhile(prefixSeparators::contains)
                .takeIf(sanitizer::isSanitized)
                ?.let(::SwitchNamePrefix)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value
}

data class ParsedSwitchName(val prefix: SwitchNamePrefix, val shortNumberPart: SwitchName) {
    companion object {
        private const val SHORT_NUMBER_LENGTH = 3

        fun tryParse(switchName: SwitchName): ParsedSwitchName? =
            switchName
                .split(Regex("[\\s_]+"))
                .takeIf { it.size == 2 }
                ?.let { parts -> SwitchNamePrefix.tryParse(parts.first())?.let { it to parts.last() } }
                ?.let { (prefix: SwitchNamePrefix, rest: String) ->
                    val parts = rest.split("/").takeIf { it.size in 1..2 }
                    val shortenedPars = parts?.mapNotNull(::shortenNumber)?.takeIf { it.size == parts.size }
                    shortenedPars?.let { prefix to it }
                }
                ?.let { (prefix: SwitchNamePrefix, numbers: List<String>) ->
                    ParsedSwitchName(prefix, SwitchName(numbers.joinToString("/")))
                }

        private fun shortenNumber(number: String): String? =
            number
                .takeIf { num -> num.startsWith("V") }
                ?.drop(1)
                ?.toIntOrNull()
                ?.let { num -> "V${num.toString().padStart(SHORT_NUMBER_LENGTH, '0')}" }
    }

    fun toShortName(): SwitchName = SwitchName("$prefix $shortNumberPart")
}
