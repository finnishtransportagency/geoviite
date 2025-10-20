package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.inframodel.INFRAMODEL_PARSING_KEY_GENERIC
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.util.formatForException
import kotlin.reflect.KClass
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import org.springframework.http.HttpStatus.UNAUTHORIZED

interface HasLocalizedMessage {
    val localizationKey: LocalizationKey
    val localizationParams: LocalizationParams
}

open class ClientException(
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
    ) : this(status, message, cause, LocalizationKey.of(localizedMessageKey), localizedMessageParams)
}

class GeocodingFailureException(message: String, cause: Throwable? = null) :
    ClientException(BAD_REQUEST, "Geocoding failed: $message", cause, "error.geocoding.generic")

class UnsupportedSridException(srid: Srid, cause: Throwable? = null) :
    ClientException(
        status = BAD_REQUEST,
        message = "Unsupported coordinate reference system: $srid",
        cause = cause,
        localizedMessageKey = "error.coordinate-transformation.unsupported-srid",
        localizedMessageParams = localizationParams("srid" to srid),
    )

class CoordinateTransformationException(point: IPoint, sourceSrid: Srid, targetSrid: Srid, cause: Throwable? = null) :
    ClientException(
        status = BAD_REQUEST,
        message = "Could not transform coordinate: x=${point.x} y=${point.y} source=$sourceSrid target=$targetSrid",
        cause = cause,
        localizedMessageKey = "error.coordinate-transformation.generic",
        localizedMessageParams =
            localizationParams("sourceSrid" to sourceSrid, "targetSrid" to targetSrid, "x" to point.x, "y" to point.y),
    )

class LinkingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
    localizedMessageParams: LocalizationParams = LocalizationParams.empty,
) :
    ClientException(
        status = BAD_REQUEST,
        message = "Linking failed: $message",
        cause = cause,
        localizedMessageKey = "$LOCALIZATION_KEY_BASE.$localizedMessageKey",
        localizedMessageParams = localizedMessageParams,
    ) {
    companion object {
        const val LOCALIZATION_KEY_BASE = "error.linking"
    }
}

class SplitFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
    localizationParams: LocalizationParams = LocalizationParams.empty,
) :
    ClientException(
        BAD_REQUEST,
        "Split failed: $message",
        cause,
        "$LOCALIZATION_KEY_BASE.$localizedMessageKey",
        localizationParams,
    ) {
    companion object {
        const val LOCALIZATION_KEY_BASE = "error.split"
    }
}

class PublicationFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
    status: HttpStatus = BAD_REQUEST,
) : ClientException(status, "Publishing failed: $message", cause, "$LOCALIZATION_KEY_BASE.$localizedMessageKey") {
    companion object {
        const val LOCALIZATION_KEY_BASE = "error.publication"
    }
}

class DeletingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.deleting.generic",
) : ClientException(BAD_REQUEST, "Deleting failed: $message", cause, localizedMessageKey)

class SavingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.saving.invalid",
) : ClientException(BAD_REQUEST, "Saving failed: $message", cause, localizedMessageKey)

class InputValidationException(
    message: String,
    type: KClass<*>,
    value: String,
    cause: Throwable? = null,
    localizationKey: String = "error.input.validation.${type.simpleName}",
) :
    ClientException(
        BAD_REQUEST,
        "Input validation failed: $message",
        cause,
        localizationKey,
        localizationParams("value" to formatForException(value, VALUE_MAX_LENGTH)),
    ) {
    companion object {
        const val VALUE_MAX_LENGTH = 25
    }
}

class ApiUnauthorizedException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.authentication.unauthorized",
) : ClientException(UNAUTHORIZED, "API request unauthorized: $message", cause, localizedMessageKey)

class InframodelParsingException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = INFRAMODEL_PARSING_KEY_GENERIC,
    localizedMessageParams: LocalizationParams = LocalizationParams.empty,
) :
    ClientException(
        BAD_REQUEST,
        "InfraModel could not be parsed: $message",
        cause,
        localizedMessageKey,
        localizedMessageParams,
    )

class NoSuchEntityException(type: String, id: String, localizedMessageKey: String = "error.entity-not-found") :
    ClientException(NOT_FOUND, "No element of type $type exists with id $id", null, localizedMessageKey) {
    constructor(type: KClass<*>, id: DomainId<*>) : this(type.simpleName ?: type.toString(), id.toString())

    constructor(type: KClass<*>, id: RowVersion<*>) : this(type.simpleName ?: type.toString(), id.toString())

    constructor(type: String, id: DomainId<*>) : this(type, id.toString())

    constructor(type: KClass<*>, id: String) : this(type.simpleName ?: type.toString(), id)
}

enum class DuplicateNameInPublication {
    SWITCH,
    TRACK_NUMBER,
}

class DuplicateNameInPublicationException(
    type: DuplicateNameInPublication,
    duplicatedName: String,
    cause: Throwable? = null,
) :
    ClientException(
        status = BAD_REQUEST,
        message = "Duplicate $type in publication: $duplicatedName",
        cause = cause,
        localizedMessageKey =
            "error.publication.duplicate-name-on.${if (type == DuplicateNameInPublication.SWITCH) "switch" else "track-number"}",
        localizedMessageParams = localizationParams("name" to duplicatedName),
    )

class DuplicateLocationTrackNameInPublicationException(
    locationTrackName: AlignmentName,
    trackNumber: TrackNumber,
    cause: Throwable? = null,
) :
    ClientException(
        status = BAD_REQUEST,
        message = "Duplicate location track $locationTrackName in $trackNumber",
        cause = cause,
        localizedMessageKey = "error.publication.duplicate-name-on.location-track",
        localizedMessageParams = localizationParams("locationTrack" to locationTrackName, "trackNumber" to trackNumber),
    )

class SplitSourceLocationTrackUpdateException(locationTrackName: AlignmentName, cause: Throwable? = null) :
    ClientException(
        status = BAD_REQUEST,
        message = "Split source location track updates are not allowed",
        cause = cause,
        localizedMessageKey = "error.split.source-track-update-is-not-allowed",
        localizedMessageParams = localizationParams("name" to locationTrackName),
    )

enum class Integration {
    RATKO,
    PROJEKTIVELHO,
}

class IntegrationNotConfiguredException(type: Integration) :
    ClientException(
        status = SERVICE_UNAVAILABLE,
        message = "Integration not configured: $type",
        cause = null,
        localizedMessageKey = "error.integration-not-configured.$type",
    )

class DuplicateDesignNameException(name: String, cause: Throwable? = null) :
    ClientException(
        status = BAD_REQUEST,
        message = "Duplicate design name: $name",
        cause = cause,
        localizedMessageKey = "error.design.duplicate-name",
        localizedMessageParams = localizationParams("name" to name),
    )

class InvalidUiVersionException(cause: Throwable? = null) :
    ClientException(
        status = BAD_REQUEST,
        message = "Invalid request: version mismatch",
        cause = cause,
        localizedMessageKey = "error.bad-request.invalid-version",
    )

class InvalidTrackLayoutVersionOrder(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.bad-request.wrong-track-layout-version-order",
) :
    ClientException(
        BAD_REQUEST,
        "comparison was attempted from newer to older version (the correct order is from older to same/newer version): $message",
        cause,
        localizedMessageKey,
    )

class TrackLayoutVersionNotFound(
    version: Uuid<Publication>,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.bad-request.track-layout-version-not-found",
) :
    ClientException(
        NOT_FOUND,
        "track layout version not found: trackLayoutVersion=$version",
        cause,
        localizedMessageKey,
    )

class ConcurrentChangesToTrackInSwitchLinkingException(trackName: AlignmentName) :
    ClientException(
        status = BAD_REQUEST,
        message = "concurrent change to track $trackName",
        cause = null,
        "error.bad-request.wrong-track-version-in-linking",
        LocalizationParams(mapOf("track" to trackName.toString())),
    )
