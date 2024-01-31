package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.inframodel.INFRAMODEL_PARSING_KEY_GENERIC
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.util.LocalizationKey
import fi.fta.geoviite.infra.util.formatForException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import kotlin.reflect.KClass

interface HasLocalizedMessage {
    val localizationKey: LocalizationKey
    val localizationParams: LocalizationParams
}

sealed class ClientException(
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

class GeocodingFailureException(message: String, cause: Throwable? = null) :
    ClientException(BAD_REQUEST, "Geocoding failed: $message", cause, "error.geocoding.generic")

class LinkingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
) : ClientException(BAD_REQUEST, "Linking failed: $message", cause, "$LOCALIZATION_KEY_BASE.$localizedMessageKey") {
    companion object {
        const val LOCALIZATION_KEY_BASE = "error.linking"
    }
}

class SplitFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
) : ClientException(BAD_REQUEST, "Split failed: $message", cause, "${LinkingFailureException.LOCALIZATION_KEY_BASE}.$localizedMessageKey") {
    companion object {
        const val LOCALIZATION_KEY_BASE = "error.split"
    }
}

class PublicationFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
) : ClientException(BAD_REQUEST, "Publishing failed: $message", cause, "$LOCALIZATION_KEY_BASE.$localizedMessageKey") {
    companion object {
        const val LOCALIZATION_KEY_BASE = "error.publication"
    }
}

class DeletingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.deleting.generic",
) : ClientException(BAD_REQUEST, "Deleting failed: $message", cause, localizedMessageKey)

class InputValidationException(
    message: String,
    type: KClass<*>,
    value: String,
    cause: Throwable? = null,
    localizationKey: String = "error.input.validation.${type.simpleName}",
) : ClientException(
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
) : ClientException(
    BAD_REQUEST,
    "InfraModel could not be parsed: $message",
    cause,
    localizedMessageKey,
    localizedMessageParams,
)

class NoSuchEntityException(
    type: String,
    id: String,
    localizedMessageKey: String = "error.entity-not-found",
) : ClientException(NOT_FOUND, "No element of type $type exists with id $id", null, localizedMessageKey) {
    constructor(type: KClass<*>, id: DomainId<*>) : this(type.simpleName ?: type.toString(), idToString(id))
    constructor(type: KClass<*>, id: RowVersion<*>) : this(type.simpleName ?: type.toString(), id.toString())
    constructor(type: String, id: DomainId<*>) : this(type, idToString(id))
    constructor(type: KClass<*>, id: String) : this(type.simpleName ?: type.toString(), id)
}

enum class DuplicateNameInPublication { SWITCH, TRACK_NUMBER }
class DuplicateNameInPublicationException(
    type: DuplicateNameInPublication,
    duplicatedName: String,
    cause: Throwable? = null,
) : ClientException(
    status = BAD_REQUEST,
    message = "Duplicate $type in publication: $duplicatedName",
    cause = cause,
    localizedMessageKey = "error.publication.duplicate-name-on.${if (type == DuplicateNameInPublication.SWITCH) "switch" else "track-number"}",
    localizedMessageParams = localizationParams("name" to duplicatedName),
)

class DuplicateLocationTrackNameInPublicationException(
    alignmentName: AlignmentName,
    trackNumber: TrackNumber,
    cause: Throwable? = null,
) : ClientException(
    status = BAD_REQUEST,
    message = "Duplicate location track $alignmentName in $trackNumber",
    cause = cause,
    localizedMessageKey = "error.publication.duplicate-name-on.location-track",
    localizedMessageParams = localizationParams(
        "locationTrack" to alignmentName,
        "trackNumber" to trackNumber,
    )
)

enum class Integration { RATKO, PROJEKTIVELHO }
class IntegrationNotConfiguredException(type: Integration) : ClientException(
    status = SERVICE_UNAVAILABLE,
    message = "Integration not configured: $type",
    cause = null,
    localizedMessageKey = "error.integration-not-configured.$type",
)
