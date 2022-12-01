package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.idToString
import fi.fta.geoviite.infra.inframodel.INFRAMODEL_PARSING_KEY_GENERIC
import fi.fta.geoviite.infra.util.LocalizationKey
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import kotlin.reflect.KClass

interface HasLocalizeMessageKey {
    val localizedMessageKey: LocalizationKey
}

sealed class ClientException(
    val status: HttpStatus,
    message: String,
    cause: Throwable? = null,
    override val localizedMessageKey: LocalizationKey,
) : RuntimeException(message, cause), HasLocalizeMessageKey {
    constructor(status: HttpStatus, message: String, cause: Throwable?, localizedMessageKey: String) :
            this(status, message, cause, LocalizationKey(localizedMessageKey))

    init {
        if (!status.is4xxClientError) throw ServerException("Not a client exception: $status")
    }
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
) : ClientException(BAD_REQUEST, "InfraModel could not be parsed: $message", cause, localizedMessageKey)

class NoSuchEntityException(
    type: String,
    id: String,
    localizedMessageKey: String = "error.entity_not_found",
) : ClientException(NOT_FOUND, "No element of type ${type} exists with id $id", null, localizedMessageKey) {
    constructor(type: KClass<*>, id: DomainId<*>) : this(type.simpleName ?: type.toString(), idToString(id))
    constructor(type: String, id: DomainId<*>) : this(type, idToString(id))
    constructor(type: KClass<*>, id: String) : this(type.simpleName ?: type.toString(), id)
}
