package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import java.time.Instant
import org.springframework.http.HttpStatus

class ExtOidNotFoundExceptionV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.oid-not-found",
) : ClientException(HttpStatus.NOT_FOUND, message, cause, localizedMessageKey)

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
    error("${LayoutTrackNumber::class.simpleName} was not found: branch=$branch moment=$moment id=$id")

fun throwGeocodingContextNotFound(
    branch: LayoutBranch,
    moment: Instant,
    trackNumberId: DomainId<LayoutTrackNumber>,
): Nothing = error("Geocoding context was not found: branch=$branch moment=$moment trackNumberId=$trackNumberId")

fun throwLocationTrackNotFound(version: LayoutRowVersion<LocationTrack>): Nothing =
    error("${LocationTrack::class.simpleName} was not found: version=$version")
