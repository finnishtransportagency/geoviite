package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.util.LocalizationKey
import java.time.Instant

data class ApiErrorResponse(
    val messageRows: List<String>,
    val correlationId: String,
    val localizedMessageKey: LocalizationKey?,
    val localizedMessageParams: LocalizationParams,
    val timestamp: Instant = Instant.now(),
)
