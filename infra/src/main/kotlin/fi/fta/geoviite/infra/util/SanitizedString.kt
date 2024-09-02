package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

data class Code @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<Code>, CharSequence by value {

    companion object {
        val sanitizer = Regex("^[A-Za-z0-9_\\-.]+\$")
    }

    init { assertSanitized<Code>(value, sanitizer) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: Code): Int = value.compareTo(other.value)
}

fun isValidCode(source: String): Boolean = isSanitized(source, Code.sanitizer, allowBlank = false)

data class FreeText @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<FreeText>, CharSequence by value {

    companion object {
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\\\\\-–+().,'/*<>:;!?&\""
        val sanitizer = Regex("^[$ALLOWED_CHARACTERS]*\$")
    }

    init { assertSanitized<FreeText>(value, sanitizer) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: FreeText): Int = value.compareTo(other.value)
    operator fun plus(addition: String) = FreeText("$value$addition")
}

data class FreeTextWithNewLines private constructor(private val value: String) :
    Comparable<FreeTextWithNewLines>, CharSequence by value {

    companion object {
        const val ALLOWED_CHARACTERS = FreeText.ALLOWED_CHARACTERS + "\n"
        val sanitizer = Regex("^[$ALLOWED_CHARACTERS]*\$")

        @JvmStatic
        @JsonCreator
        fun of(value: String) = FreeTextWithNewLines(normalizeLinebreaksToUnixFormat(value))
    }

    init { assertSanitized<FreeTextWithNewLines>(value, sanitizer) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: FreeTextWithNewLines): Int = value.compareTo(other.value)
    operator fun plus(addition: String) = FreeTextWithNewLines("$value$addition")
}
