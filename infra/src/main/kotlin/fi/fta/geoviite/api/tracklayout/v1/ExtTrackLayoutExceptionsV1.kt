package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.error.HasLocalizedMessage
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import java.lang.RuntimeException
import org.springframework.http.HttpStatus

sealed class ExtApiExceptionV1(
    val status: HttpStatus,
    message: String,
    cause: Throwable? = null,
    override val localizationKey: LocalizationKey,
    override val localizationParams: LocalizationParams = LocalizationParams.empty,
) : RuntimeException(message, cause), HasLocalizedMessage {
    constructor(
        status: HttpStatus,
        message: String,
        cause: Throwable?,
        localizedMessageKey: String,
        localizedMessageParams: LocalizationParams = LocalizationParams.empty,
    ) : this(status, message, cause, LocalizationKey(localizedMessageKey), localizedMessageParams)
}

class ExtOidNotFoundExceptionV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "ext-api.track-layout.v1.error.oid-not-found",
) : ExtApiExceptionV1(HttpStatus.NOT_FOUND, "oid not found: $message", cause, localizedMessageKey)

class ExtGeocodingFailedV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "ext-api.track-layout.v1.error.geocoding-failed",
) : ExtApiExceptionV1(HttpStatus.INTERNAL_SERVER_ERROR, "geocoding failed: $message", cause, localizedMessageKey)

class ExtTrackNumberNotFoundV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "ext-api.track-layout.v1.error.track-number-not-found",
) : ExtApiExceptionV1(HttpStatus.INTERNAL_SERVER_ERROR, "track number not found: $message", cause, localizedMessageKey)
