package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

val projectNameLength = 1..100
val projectNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-/+&.,]+\$")

data class ProjectName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {
    init { assertSanitized<ProjectName>(value, projectNameRegex, projectNameLength, allowBlank = false) }

    @JsonValue
    override fun toString(): String = value
}
