package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

data class AlignmentName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<AlignmentName>, CharSequence by value {

    companion object {
        val sanitizer = Regex("^[A-Za-zÄÖÅäöå0-9 \\-_!?§]+\$")
        val allowedLength = 1..50
    }

    init { assertSanitized<AlignmentName>(value, sanitizer, allowedLength, allowBlank = false) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: AlignmentName): Int = value.compareTo(other.value)
    fun padStart(length: Int, padChar: Char) = AlignmentName(value.padStart(length, padChar))
}
