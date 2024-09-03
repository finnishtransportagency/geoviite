package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.assertSanitized

enum class PlanState {
    ABANDONED,
    DESTROYED,
    EXISTING,
    PROPOSED,
}

enum class PlanPhase {
    RAILWAY_PLAN,
    RAILWAY_CONSTRUCTION_PLAN,
    RENOVATION_PLAN,
    ENHANCED_RENOVATION_PLAN,
    MAINTENANCE,
    NEW_INVESTMENT,
    REMOVED_FROM_USE,
}

enum class PlanDecisionPhase {
    APPROVED_PLAN,
    UNDER_CONSTRUCTION,
    IN_USE,
}

data class Project(val name: ProjectName, val description: FreeText?, val id: DomainId<Project> = StringId())

data class Application(
    val name: MetaDataName,
    val manufacturer: MetaDataName,
    val version: MetaDataName,
    val id: DomainId<Application> = StringId(),
)

data class Author(val companyName: CompanyName, val id: DomainId<Author> = StringId())

val metaDataNameLength = 1..100
val metaDataNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-/+&,.:()]+\$")

data class CompanyName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {
    init {
        assertSanitized<CompanyName>(value, metaDataNameRegex, metaDataNameLength)
    }

    @JsonValue override fun toString(): String = value
}

data class MetaDataName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {
    init {
        assertSanitized<MetaDataName>(value, metaDataNameRegex, metaDataNameLength)
    }

    @JsonValue override fun toString(): String = value
}
