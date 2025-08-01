package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.error.ServerException
import org.springframework.http.HttpStatus

class ExtOidNotFoundExceptionV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.oid-not-found",
) : ClientException(HttpStatus.NOT_FOUND, "oid not found: $message", cause, localizedMessageKey)

class ExtLocationTrackNotFoundExceptionV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.location-track-not-found",
) : ClientException(HttpStatus.NOT_FOUND, "location track not found: $message", cause, localizedMessageKey)

class ExtGeocodingFailedV1(message: String, cause: Throwable? = null) :
    ServerException("geocoding failed: $message", cause)

class ExtTrackNumberNotFoundV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.track-number-not-found",
) : ClientException(HttpStatus.BAD_REQUEST, "track number not found: $message", cause, localizedMessageKey)

class ExtTrackLayoutVersionNotFound(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.track-layout-version-not-found",
) : ClientException(HttpStatus.NOT_FOUND, "track layout version not found: $message", cause, localizedMessageKey)
