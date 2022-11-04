package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter

enum class PlanState { ABANDONED, DESTROYED, EXISTING, PROPOSED }

enum class PlanPhase {
    RAILWAY_PLAN,
    RAILWAY_CONSTRUCTION_PLAN,
    RENOVATION_PLAN,
    ENHANCED_RENOVATION_PLAN,
    MAINTENANCE,
    NEW_INVESTMENT,
    REMOVED_FROM_USE,
}

enum class PlanDecisionPhase { APPROVED_PLAN, UNDER_CONSTRUCTION, IN_USE }

data class Project(val name: ProjectName, val description: FreeText?, val id: DomainId<Project> = StringId())

data class Application(
    val name: MetaDataName,
    val manufacturer: MetaDataName,
    val version: MetaDataName,
    val id: DomainId<Application> = StringId(),
)

data class Author(val companyName: MetaDataName, val id: DomainId<Author> = StringId())


val metaDataNameLength = 1..100
val metaDataNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-/+&,.:()]+\$")

data class MetaDataName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String) :
    CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init {
        assertSanitized<MetaDataName>(value, metaDataNameRegex, metaDataNameLength)
    }
}

class StringToMetaDataNameConverter : Converter<String, MetaDataName> {
    override fun convert(source: String): MetaDataName = MetaDataName(source)
}

class MetaDataNameToStringConverter : Converter<MetaDataName, String> {
    override fun convert(source: MetaDataName): String = source.toString()
}
