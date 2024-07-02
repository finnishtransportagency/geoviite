package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.util.FreeText
import java.time.LocalDate

enum class DesignState {
    ACTIVE, DELETED, COMPLETED
}

data class LayoutDesign(
    val id: DomainId<LayoutDesign>,
    val name: FreeText,
    val estimatedCompletion: LocalDate,
    val designState: DesignState,
)

data class LayoutDesignSaveRequest(
    val name: FreeText,
    val estimatedCompletion: LocalDate,
    val designState: DesignState,
)
