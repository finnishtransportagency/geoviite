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

class ExtInvalidTrackMeterV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.bad-request.invalid-track-meter",
) : ClientException(HttpStatus.BAD_REQUEST, "invalid track meter: $message", cause, localizedMessageKey)

class ExtInvalidKmNumberV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.bad-request.invalid-km-number",
) : ClientException(HttpStatus.BAD_REQUEST, "invalid km number: $message", cause, localizedMessageKey)

class ExtInvalidAddressPointFilterOrderV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.bad-request.invalid-address-point-filter-order",
) :
    ClientException(
        HttpStatus.BAD_REQUEST,
        "invalid address point filter order (start > end): $message",
        cause,
        localizedMessageKey,
    )
