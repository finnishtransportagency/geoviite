package fi.fta.geoviite.infra.error

import java.time.Instant

data class ApiErrorResponse(
    val messageRows: List<String>,
    val correlationId: String,
    val localizedMessageKey: String?,
    val timestamp: Instant = Instant.now(),
)
