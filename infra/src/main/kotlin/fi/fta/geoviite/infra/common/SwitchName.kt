package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

val switchNameLength = 1..50
val switchNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 \\-_/,.]+\$")

data class SwitchName @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<SwitchName>, CharSequence by value {
    init { assertSanitized<SwitchName>(value, switchNameRegex, switchNameLength, allowBlank = false) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: SwitchName): Int = value.compareTo(other.value)
}
