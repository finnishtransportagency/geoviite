package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.error.ErrorDescription
import fi.fta.geoviite.infra.error.ErrorPriority
import fi.fta.geoviite.infra.error.ExtApiErrorResponseV1
import fi.fta.geoviite.infra.error.GeoviiteErrorResponse
import fi.fta.geoviite.infra.error.HasLocalizedMessage
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException

private const val ERROR_KEY_BASE = "ext-api.track-layout.v1.error"

typealias ExtApiExceptionV1 = ClientException

class ExtOidNotFoundExceptionV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.oid-not-found",
) : ExtApiExceptionV1(HttpStatus.NOT_FOUND, "oid not found: $message", cause, localizedMessageKey)

class ExtGeocodingFailedV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.geocoding-failed",
) : ExtApiExceptionV1(HttpStatus.INTERNAL_SERVER_ERROR, "geocoding failed: $message", cause, localizedMessageKey)

class ExtTrackNumberNotFoundV1(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.track-number-not-found",
) : ExtApiExceptionV1(HttpStatus.BAD_REQUEST, "track number not found: $message", cause, localizedMessageKey)

class ExtTrackNetworkVersionNotFound(
    message: String,
    cause: Throwable? = null,
    localizedMessageKey: String = "$ERROR_KEY_BASE.track-network-version-not-found",
) : ExtApiExceptionV1(HttpStatus.BAD_REQUEST, "track network version not found: $message", cause, localizedMessageKey)

fun createExtApiErrorResponseV1(
    correlationId: String,
    status: HttpStatus,
    causeChain: List<Exception>,
    translation: Translation,
): ResponseEntity<GeoviiteErrorResponse>? {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON

    val messageRows =
        when {
            status.is5xxServerError -> describeHttpStatus(translation, status).let(::listOf)

            else -> causeChain.mapNotNull { ex -> describeException(translation, ex) }
        }

    val description =
        messageRows.minByOrNull { errorDescription -> errorDescription.priority }
            ?: describeHttpStatus(translation, status)

    val body =
        ExtApiErrorResponseV1(
            messageRows = messageRows.map { m -> m.message },
            localizationKey = description.localizationKey,
            localizationParams = description.localizationParams,
            correlationId = correlationId,
        )

    return ResponseEntity(body, headers, status)
}

private fun describeHttpStatus(translation: Translation, status: HttpStatus): ErrorDescription {
    val errorKey =
        if (status.is4xxClientError) {
            "ext-api.track-layout.v1.error.client-error"
        } else {
            "ext-api.track-layout.v1.error.server-error"
        }

    return ErrorDescription(
        message = translation.t(errorKey),
        key = errorKey,
        params = localizationParams("koodi" to status.value()),
    )
}

private fun describeException(translation: Translation, ex: Exception): ErrorDescription? {
    return when (ex) {
        is HasLocalizedMessage ->
            ErrorDescription(
                message = ex.message ?: "${ex::class.simpleName}",
                localizationKey = ex.localizationKey,
                localizationParams = ex.localizationParams,
                priority = ErrorPriority.HIGH,
            )

        is HttpRequestMethodNotSupportedException -> {
            val key = "$ERROR_KEY_BASE.bad-request.invalid-path"
            val params = localizationParams("metodi" to ex.method)

            ErrorDescription(translation.t(key, params), key, params)
        }

        is NoHandlerFoundException -> {
            val key = "$ERROR_KEY_BASE.bad-request.invalid-path"
            val params = localizationParams("metodi" to ex.httpMethod)

            ErrorDescription(translation.t(key, params), key, params)
        }

        is MissingServletRequestParameterException -> {
            val key = "$ERROR_KEY_BASE.bad-request.missing-parameter"
            val params = localizationParams("parametri" to ex.parameterName, "tyyppi" to ex.parameterType)

            ErrorDescription(translation.t(key, params), key, params)
        }

        is MethodArgumentTypeMismatchException -> {
            val key = "$ERROR_KEY_BASE.bad-request.conversion-failed-detailed"
            val params =
                localizationParams(
                    "nimi" to ex.name,
                    "parametri" to ex.parameter,
                    "tavoitetyyppi" to (ex.requiredType?.simpleName ?: "tuntematon"),
                )

            ErrorDescription(translation.t(key, params), key, params)
        }

        is ConversionFailedException -> {
            val key = "$ERROR_KEY_BASE.bad-request.conversion-failed"
            val params = localizationParams("tavoitetyyppi" to ex.targetType.type.simpleName)

            ErrorDescription(translation.t(key, params), key, params, ErrorPriority.LOW)
        }

        is MismatchedInputException -> {
            val key = "$ERROR_KEY_BASE.bad-request.conversion-failed"
            val params = localizationParams("tavoitetyyppi" to ex.targetType?.simpleName)

            ErrorDescription(translation.t(key, params), key, params, ErrorPriority.LOW)
        }

        is ValueInstantiationException -> {
            val key = "$ERROR_KEY_BASE.bad-request.conversion-failed"
            val params = localizationParams("tavoitetyyppi" to ex.type?.genericSignature)

            ErrorDescription(translation.t(key, params), key, params, ErrorPriority.LOW)
        }

        else -> null
    }
}
