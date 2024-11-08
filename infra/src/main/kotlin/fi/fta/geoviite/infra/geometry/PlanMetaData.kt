package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.StringSanitizer

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

data class CompanyName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {
    companion object {
        val sanitizer = MetaDataName.sanitizer

        fun ofUnsafe(value: String) = CompanyName(sanitizer.sanitize(value))
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value
}

data class MetaDataName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {

    companion object {
        val allowedLength = 1..100
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\-/+&,.:()"
        val sanitizer = StringSanitizer(MetaDataName::class, ALLOWED_CHARACTERS, allowedLength)

        fun ofUnsafe(value: String) = MetaDataName(sanitizer.sanitize(value))
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value
}
