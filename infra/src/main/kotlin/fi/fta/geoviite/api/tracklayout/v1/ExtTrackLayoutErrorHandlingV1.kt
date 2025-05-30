package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import fi.fta.geoviite.infra.error.ErrorDescription
import fi.fta.geoviite.infra.error.ErrorPriority
import fi.fta.geoviite.infra.error.GeoviiteErrorResponse
import fi.fta.geoviite.infra.error.HasLocalizedMessage
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import java.time.Instant
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException

internal const val ERROR_KEY_BASE = "ext-api.track-layout.v1.error"

data class ExtApiErrorResponseV1(
    @JsonProperty("virheviesti") val errorMessage: String,
    @JsonProperty("korrelaatiotunnus") val correlationId: String,
    @JsonProperty("aikaleima") val timestamp: Instant = Instant.now(),
) : GeoviiteErrorResponse()

fun createExtApiErrorResponseV1(
    correlationId: String,
    status: HttpStatus,
    causeChain: List<Exception>,
    translation: Translation,
): ResponseEntity<GeoviiteErrorResponse>? {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON

    val errorMessageChain =
        when {
            status.is5xxServerError -> describeHttpStatus(translation, status).let(::listOf)

            else -> causeChain.mapNotNull { ex -> describeException(translation, ex) }
        }

    val prioritizedErrorMessage =
        errorMessageChain.minByOrNull { errorDescription -> errorDescription.priority }
            ?: describeHttpStatus(translation, status)

    return ResponseEntity(
        ExtApiErrorResponseV1(errorMessage = prioritizedErrorMessage.message, correlationId = correlationId),
        headers,
        status,
    )
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
