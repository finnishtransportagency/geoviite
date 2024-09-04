package fi.fta.geoviite.infra.inframodel

import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

data class PlanElementName(val value: String) : CharSequence by value {
    companion object {
        val allowedLength = 0..100
        const val ALLOWED_CHARACTERS = "A-Za-zÄÖÅäöå0-9 \\-+_/!?§"
        val sanitizer = StringSanitizer(PlanElementName::class, ALLOWED_CHARACTERS, allowedLength)

        fun ofUnsafe(value: String) = PlanElementName(sanitizer.sanitize(value))
    }

    init {
        sanitizer.sanitize(value)
    }

    @JsonValue override fun toString(): String = value
}
