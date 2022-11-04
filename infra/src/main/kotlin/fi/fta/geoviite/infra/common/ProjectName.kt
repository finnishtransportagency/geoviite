package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter

val projectNameLength = 1..100
val projectNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-/+&.,]+\$")

data class ProjectName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String) :
    CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init {
        assertSanitized<ProjectName>(value, projectNameRegex, projectNameLength, allowBlank = false)
    }
}

class StringToProjectNameConverter : Converter<String, ProjectName> {
    override fun convert(source: String): ProjectName = ProjectName(source)
}

class ProjectNameToStringConverter : Converter<ProjectName, String> {
    override fun convert(source: ProjectName): String = source.toString()
}
