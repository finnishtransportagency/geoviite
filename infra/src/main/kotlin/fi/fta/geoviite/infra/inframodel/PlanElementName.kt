package fi.fta.geoviite.infra.inframodel

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter


val elementNameLength = 0..100

val elementNameRegex = Regex("^[A-Za-zÄÖÅäöå0-9 \\-+_/]*\$")

data class PlanElementName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init { assertSanitized<PlanElementName>(value, elementNameRegex, elementNameLength, allowBlank = true) }
}

class StringToPlanElementNameConverter : Converter<String, PlanElementName> {
    override fun convert(source: String): PlanElementName = PlanElementName(source)
}

class PlanElementNameToStringConverter : Converter<PlanElementName, String> {
    override fun convert(source: PlanElementName): String = source.toString()
}
