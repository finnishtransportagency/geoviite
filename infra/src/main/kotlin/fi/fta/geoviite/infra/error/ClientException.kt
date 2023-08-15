package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.idToString
import fi.fta.geoviite.infra.inframodel.INFRAMODEL_PARSING_KEY_GENERIC
import fi.fta.geoviite.infra.util.LocalizationKey
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import kotlin.reflect.KClass

interface HasLocalizeMessageKey {
    val localizedMessageKey: LocalizationKey
    val localizedMessageParams: List<String>
}

sealed class ClientException(
    val status: HttpStatus,
    message: String,
    cause: Throwable? = null,
    override val localizedMessageKey: LocalizationKey,
    override val localizedMessageParams: List<String> = listOf(),
) : RuntimeException(message, cause), HasLocalizeMessageKey {
    constructor(
        status: HttpStatus,
        message: String,
        cause: Throwable?,
        localizedMessageKey: String,
        localizedMessageParams: List<String> = listOf(),
    ) : this(status, message, cause, LocalizationKey(localizedMessageKey), localizedMessageParams)
}

class GeocodingFailureException(message: String, cause: Throwable? = null)
    : ClientException(BAD_REQUEST, "Geocoding failed: $message", cause, "error.geocoding.generic")

class LinkingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
) : ClientException(BAD_REQUEST, "Linking failed: $message", cause, "$LOCALIZATION_KEY_BASE.$localizedMessageKey") {
    companion object { const val LOCALIZATION_KEY_BASE = "error.linking" }
}

class PublicationFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "generic",
) : ClientException(BAD_REQUEST, "Publishing failed: $message", cause, "$LOCALIZATION_KEY_BASE.$localizedMessageKey") {
    companion object { const val LOCALIZATION_KEY_BASE = "error.publication" }
}

class DeletingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.deleting.generic",
) : ClientException(BAD_REQUEST, "Deleting failed: $message", cause, localizedMessageKey)

class InputValidationException(
    message: String,
    type: KClass<*>,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.input.validation.${type.simpleName}",
) : ClientException(BAD_REQUEST, "Input validation failed: $message", cause, localizedMessageKey)

class ApiUnauthorizedException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.unauthorized",
) : ClientException(UNAUTHORIZED, "API request unauthorized: $message", cause, localizedMessageKey)

class InframodelParsingException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = INFRAMODEL_PARSING_KEY_GENERIC,
    localizedMessageParams: List<String> = listOf(),
) : ClientException(
    BAD_REQUEST, "InfraModel could not be parsed: $message", cause, localizedMessageKey, localizedMessageParams)

class NoSuchEntityException(
    type: String,
    id: String,
    localizedMessageKey: String = "error.entity_not_found",
) : ClientException(NOT_FOUND, "No element of type $type exists with id $id", null, localizedMessageKey) {
    constructor(type: KClass<*>, id: DomainId<*>) : this(type.simpleName ?: type.toString(), idToString(id))
    constructor(type: String, id: DomainId<*>) : this(type, idToString(id))
    constructor(type: KClass<*>, id: String) : this(type.simpleName ?: type.toString(), id)
}

enum class DuplicateNameInPublication { SWITCH, TRACK_NUMBER }
class DuplicateNameInPublicationException(
    type: DuplicateNameInPublication,
    duplicatedName: String,
    cause: Throwable? = null,
) : ClientException(
    BAD_REQUEST,
    "Duplicate $type in publication: $duplicatedName",
    cause,
    localizedMessageKey = "error.publication.duplicate-name-on.$type",
    localizedMessageParams = listOf(duplicatedName),
)
class DuplicateLocationTrackNameInPublicationException(
    alignmentName: AlignmentName,
    trackNumber: TrackNumber,
    cause: Throwable? = null
) : ClientException(
    BAD_REQUEST, "Duplicate location track $alignmentName in $trackNumber", cause,
    localizedMessageKey = "error.publication.duplicate-name-on-location-track",
    localizedMessageParams = listOf(alignmentName.toString(), trackNumber.value)
)


enum class Integration { RATKO, PROJEKTIVELHO }
class IntegrationNotConfiguredException(type: Integration): ClientException(
    status = SERVICE_UNAVAILABLE,
    message = "Integration not configured: $type",
    cause = null,
    localizedMessageKey = "error.integration-not-configured.$type",
)
