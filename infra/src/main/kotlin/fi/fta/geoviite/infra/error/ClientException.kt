package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.idToString
import fi.fta.geoviite.infra.inframodel.INFRAMODEL_PARSING_KEY_GENERIC
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import kotlin.reflect.KClass

interface HasLocalizeMessageKey {
    val localizedMessageKey: String
}

sealed class ClientException(
    val status: HttpStatus,
    message: String,
    cause: Throwable? = null,
    override val localizedMessageKey: String,
) :
    Exception(message, cause), HasLocalizeMessageKey {
    init {
        if (!status.is4xxClientError) throw ServerException("Not a client exception: $status")
    }
}

class GeocodingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.geocoding.generic",
) : ClientException(BAD_REQUEST, "Geocoding failed: $message", cause, localizedMessageKey)

class LinkingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.linking.generic",
) : ClientException(BAD_REQUEST, "Linking failed: $message", cause, localizedMessageKey)

class DeletingFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.deleting.generic",
) : ClientException(BAD_REQUEST, "Deleting failed: $message", cause, localizedMessageKey)

class PublishFailureException(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "error.publish.generic",
) : ClientException(BAD_REQUEST, "Publish failed: $message", cause, localizedMessageKey)

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
