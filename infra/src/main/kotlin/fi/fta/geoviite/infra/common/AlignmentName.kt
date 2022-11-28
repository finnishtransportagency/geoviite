package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

val alignmentNameLength = 1..50
val alignmentNameRegex = Regex("^[A-Za-zÄÖÅäöå0-9 \\-_!?§]+\$")

data class AlignmentName @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<AlignmentName>, CharSequence by value {

    init { assertSanitized<AlignmentName>(value, alignmentNameRegex, alignmentNameLength, allowBlank = false) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: AlignmentName): Int = value.compareTo(other.value)
    fun padStart(length: Int, padChar: Char) = AlignmentName(value.padStart(length, padChar))
}
