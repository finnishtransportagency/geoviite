package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import java.time.Instant

data class ApiErrorResponse(
    val messageRows: List<String>,
    val localizationKey: LocalizationKey,
    val localizationParams: LocalizationParams,
    val correlationId: String,
    val timestamp: Instant = Instant.now(),
)
