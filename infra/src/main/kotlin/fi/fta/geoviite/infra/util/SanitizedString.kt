package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

val codeRegex = Regex("^[A-Za-z0-9_\\-.]+\$")
val freeTextRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-–+().,'/*<>:;!?&]*\$")

data class Code @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<Code>, CharSequence by value {
    init { assertSanitized<Code>(value, codeRegex) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: Code): Int = value.compareTo(other.value)
}

fun isValidCode(source: String): Boolean = isSanitized(source, codeRegex, allowBlank = false)

data class FreeText @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<FreeText>, CharSequence by value {
    init { assertSanitized<FreeText>(value, freeTextRegex) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: FreeText): Int = value.compareTo(other.value)
    operator fun plus(addition: String) = FreeText("$value$addition")
}
