package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

data class ProjectName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {

    companion object {
        val allowedLength = 1..100
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\-/+&.,"
        val sanitizer = StringSanitizer(ProjectName::class, ALLOWED_CHARACTERS, allowedLength)

        fun ofUnsafe(value: String) = ProjectName(sanitizer.sanitize(value))
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value
}
