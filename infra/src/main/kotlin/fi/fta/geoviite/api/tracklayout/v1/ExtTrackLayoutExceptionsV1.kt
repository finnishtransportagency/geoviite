package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.error.ServerException
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import java.time.Instant
import org.springframework.http.HttpStatus

class ExtOidNotFoundExceptionV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.oid-not-found",
) : ClientException(HttpStatus.NOT_FOUND, message, cause, localizedMessageKey)

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

inline fun <reified T : LayoutAsset<T>> throwOidNotFound(branch: LayoutBranch, id: DomainId<T>): Nothing =
    throw ExtOidNotFoundExceptionV1("${T::class.simpleName} OID not found: branch=$branch id=$id")

inline fun <reified T : LayoutAsset<T>> throwOidNotFound(oid: Oid<T>): Nothing =
    throw ExtOidNotFoundExceptionV1("${T::class.simpleName} OID lookup failed for oid=$oid")

fun throwTrackNumberNotFound(branch: LayoutBranch, moment: Instant, id: DomainId<LayoutTrackNumber>): Nothing =
    throw ExtTrackNumberNotFoundV1(
        "${LayoutTrackNumber::class.simpleName} was not found: branch=$branch moment=$moment id=$id"
    )

fun throwGeocodingContextNotFound(
    branch: LayoutBranch,
    moment: Instant,
    trackNumberId: DomainId<LayoutTrackNumber>,
): Nothing =
    throw ExtGeocodingFailedV1(
        "Geocoding context was not found: branch=$branch moment=$moment trackNumberId=$trackNumberId"
    )

fun throwLocationTrackNotFound(branch: LayoutBranch, moment: Instant, id: DomainId<LocationTrack>): Nothing =
    throw ExtLocationTrackNotFoundExceptionV1(
        "${LocationTrack::class.simpleName} was not found: branch=$branch moment=$moment id=$id"
    )
