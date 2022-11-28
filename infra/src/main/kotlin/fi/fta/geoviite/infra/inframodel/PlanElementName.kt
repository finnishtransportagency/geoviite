package fi.fta.geoviite.infra.inframodel

import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized


val elementNameLength = 0..100
val elementNameRegex = Regex("^[A-Za-zÄÖÅäöå0-9 \\-+_/!?§]*\$")

data class PlanElementName(val value: String) : CharSequence by value {
    init { assertSanitized<PlanElementName>(value, elementNameRegex, elementNameLength, allowBlank = true) }

    @JsonValue
    override fun toString(): String = value
}
