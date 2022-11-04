package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter


val switchNameLength = 1..50

val switchNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 \\-_/]+\$")

data class SwitchName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : Comparable<SwitchName>, CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init { assertSanitized<SwitchName>(value, switchNameRegex, switchNameLength, allowBlank = false) }

    override fun compareTo(other: SwitchName): Int = value.compareTo(other.value)
}

class StringToSwitchNameConverter : Converter<String, SwitchName> {
    override fun convert(source: String): SwitchName = SwitchName(source)
}

class SwitchNameToStringConverter : Converter<SwitchName, String> {
    override fun convert(source: SwitchName): String = source.toString()
}
