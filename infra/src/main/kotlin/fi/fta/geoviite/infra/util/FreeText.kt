package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

const val NEW_LINE_CHARACTER = "\n"
const val ESCAPED_NEW_LINE = "\\n"

data class FreeText @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<FreeText>, CharSequence by value {

    companion object {
        const val ALLOWED_CHARACTERS =
            "A-ZÄÖÅa-zäöå0-9 \t_\\\\\\-–—+(){}.,´`'\"/*#<>\\[\\]:;!?&=€$£@%~$UNSAFE_REPLACEMENT"
        val sanitizer = StringSanitizer(FreeText::class, ALLOWED_CHARACTERS)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    constructor(unsafeString: UnsafeString) : this(sanitizer.sanitize(unsafeString.unsafeValue))

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: FreeText): Int = value.compareTo(other.value)

    operator fun plus(addition: String) = FreeText("$value$addition")
}

data class FreeTextWithNewLines private constructor(private val value: String) :
    Comparable<FreeTextWithNewLines>, CharSequence by value {

    companion object {
        const val ALLOWED_CHARACTERS = FreeText.ALLOWED_CHARACTERS + NEW_LINE_CHARACTER

        val sanitizer = StringSanitizer(FreeTextWithNewLines::class, ALLOWED_CHARACTERS)

        @JvmStatic @JsonCreator fun of(value: String) = FreeTextWithNewLines(normalizeLinebreaksToUnixFormat(value))
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: FreeTextWithNewLines): Int = value.compareTo(other.value)

    operator fun plus(addition: String) = FreeTextWithNewLines("$value$addition")

    fun escapeNewLines(): FreeText {
        return FreeText(UnsafeString(value.replace(NEW_LINE_CHARACTER, ESCAPED_NEW_LINE)))
    }
}
