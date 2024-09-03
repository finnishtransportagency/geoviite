package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

data class SwitchName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<SwitchName>, CharSequence by value {

    companion object {
        val sanitizer = Regex("^[A-ZÄÖÅa-zäöå0-9 \\-_/,.]+\$")
        val allowedLength = 1..50
    }

    init {
        assertSanitized<SwitchName>(value, sanitizer, allowedLength, allowBlank = false)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: SwitchName): Int = value.compareTo(other.value)

    fun equalsIgnoreCase(other: SwitchName): Boolean = value.equals(other.value, ignoreCase = true)
}
