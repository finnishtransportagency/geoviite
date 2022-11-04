package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.core.convert.converter.Converter

val codeRegex = Regex("^[A-Za-z0-9_\\-.]+\$")

val freeTextRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-–+().,'/*<>:!?&]*\$")

data class Code @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String) : Comparable<Code>,
    CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init {
        assertSanitized<Code>(value, codeRegex)
    }

    override fun compareTo(other: Code): Int = value.compareTo(other.value)
}

fun isValidCode(source: String): Boolean = isSanitized(source, codeRegex, allowBlank = false)

class StringToCodeConverter : Converter<String, Code> {
    override fun convert(source: String): Code = Code(source)
}

class CodeToStringConverter : Converter<Code, String> {
    override fun convert(source: Code): String = source.toString()
}

data class FreeText @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String) :
    Comparable<FreeText>, CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init {
        assertSanitized<FreeText>(value, freeTextRegex)
    }

    override fun compareTo(other: FreeText): Int = value.compareTo(other.value)
    operator fun plus(addition: String) = FreeText("$value$addition")
}

class StringToDescriptionConverter : Converter<String, FreeText> {
    override fun convert(source: String): FreeText = FreeText(source)
}

class DescriptionToStringConverter : Converter<FreeText, String> {
    override fun convert(source: FreeText): String = source.toString()
}
