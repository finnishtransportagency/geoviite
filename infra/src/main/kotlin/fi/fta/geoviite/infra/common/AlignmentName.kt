package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter


val alignmentNameLength = 1..50

val alignmentNameRegex = Regex("^[A-Za-zÄÖÅäöå0-9 \\-_!?§]+\$")

data class AlignmentName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : Comparable<AlignmentName>, CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init { assertSanitized<AlignmentName>(value, alignmentNameRegex, alignmentNameLength, allowBlank = false) }

    override fun compareTo(other: AlignmentName): Int = value.compareTo(other.value)
}

class StringToAlignmentNameConverter : Converter<String, AlignmentName> {
    override fun convert(source: String): AlignmentName = AlignmentName(source)
}

class AlignmentNameToStringConverter : Converter<AlignmentName, String> {
    override fun convert(source: AlignmentName): String = source.toString()
}
