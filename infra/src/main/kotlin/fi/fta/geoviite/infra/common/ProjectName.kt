package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

data class ProjectName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {

    companion object {
        val allowedLength = 1..100
        val sanitizer = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-/+&.,]+\$")
    }

    init {
        assertSanitized<ProjectName>(value, sanitizer, allowedLength, allowBlank = false)
    }

    @JsonValue override fun toString(): String = value
}
