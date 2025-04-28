package fi.fta.geoviite.infra.error

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import java.time.Instant

sealed class GeoviiteErrorResponse

data class ApiErrorResponse(
    val messageRows: List<String>,
    val localizationKey: LocalizationKey,
    val localizationParams: LocalizationParams,
    val correlationId: String,
    val timestamp: Instant = Instant.now(),
) : GeoviiteErrorResponse()

data class ExtApiErrorResponseV1(
    @JsonProperty("virheviesti") val errorMessage: String,
    @JsonProperty("korrelaatiotunnus") val correlationId: String,
    @JsonProperty("aikaleima") val timestamp: Instant = Instant.now(),
) : GeoviiteErrorResponse()
