package fi.fta.geoviite.infra.error

import com.auth0.jwt.exceptions.AlgorithmMismatchException
import com.auth0.jwt.exceptions.InvalidClaimException
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import jakarta.xml.bind.UnmarshalException
import org.geotools.api.referencing.operation.TransformException
import org.postgresql.util.PSQLException
import org.springframework.beans.ConversionNotSupportedException
import org.springframework.beans.TypeMismatchException
import org.springframework.boot.context.properties.bind.BindException
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED
import org.springframework.http.HttpStatus.NOT_ACCEPTABLE
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.REQUEST_TIMEOUT
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.TransactionTimedOutException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException

fun createResponse(exception: Exception, correlationId: String): ResponseEntity<ApiErrorResponse>? {
    val causeChain = getCauseChain(exception)
    val status = getStatusCode(causeChain)
    return status?.let { s ->
        if (s.is5xxServerError) {
            createTerseErrorResponse(correlationId, s)
        } else {
            createDescriptiveErrorResponse(correlationId, s, causeChain)
        }
    }
}

fun createTerseErrorResponse(correlationId: String, status: HttpStatus): ResponseEntity<ApiErrorResponse> =
    createResponse(listOf(describe(status)), status, correlationId)

fun createDescriptiveErrorResponse(
    correlationId: String,
    status: HttpStatus,
    causeChain: List<Exception>,
): ResponseEntity<ApiErrorResponse> = createResponse(causeChain.mapNotNull(::describe), status, correlationId)

fun createResponse(
    messageRows: List<ErrorDescription>,
    status: HttpStatus,
    correlationId: String,
): ResponseEntity<ApiErrorResponse> {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    val description = getPrimaryDescription(messageRows, status)
    return ResponseEntity(
        ApiErrorResponse(
            messageRows = messageRows.map { m -> m.message },
            localizationKey = description.localizationKey,
            localizationParams = description.localizationParams,
            correlationId = correlationId,
        ),
        headers,
        status,
    )
}

fun getPrimaryDescription(messageRows: List<ErrorDescription>, status: HttpStatus): ErrorDescription =
    messageRows.minByOrNull { r -> r.priority } ?: describe(status)

fun getCauseChain(exception: Exception): List<Exception> {
    val chain = mutableListOf<Exception>()
    var current: Throwable? = exception
    while (current != null) {
        when (current) {
            is Exception -> chain.add(current)
            is Error ->
                error(
                    "Errors are not meant to be caught! Tried to handle $current${current.cause?.let { c -> " (cause: $c)" }}"
                )
        }
        current = current.cause
    }
    return chain
}

fun getStatusCode(causeChain: List<Exception>): HttpStatus? =
    if (causeChain.any { e -> e is ServerException }) INTERNAL_SERVER_ERROR
    else causeChain.firstNotNullOfOrNull(::getStatusCode)

fun getStatusCode(exception: Exception): HttpStatus? =
    when (exception) {
        // Our own exceptions
        is ServerException -> INTERNAL_SERVER_ERROR
        is ClientException -> exception.status
        // Spring exceptions
        is AccessDeniedException -> FORBIDDEN
        is HttpRequestMethodNotSupportedException -> METHOD_NOT_ALLOWED
        is HttpMediaTypeNotSupportedException -> UNSUPPORTED_MEDIA_TYPE
        is HttpMediaTypeNotAcceptableException -> NOT_ACCEPTABLE
        is MissingPathVariableException -> INTERNAL_SERVER_ERROR
        is MissingServletRequestParameterException -> BAD_REQUEST
        is ServletRequestBindingException -> BAD_REQUEST
        is ConversionNotSupportedException -> INTERNAL_SERVER_ERROR
        is TypeMismatchException -> BAD_REQUEST
        is HttpMessageNotReadableException -> BAD_REQUEST
        is HttpMessageNotWritableException -> INTERNAL_SERVER_ERROR
        is MethodArgumentNotValidException -> BAD_REQUEST
        is MissingServletRequestPartException -> BAD_REQUEST
        is BindException -> BAD_REQUEST
        is NoHandlerFoundException -> NOT_FOUND
        is AsyncRequestTimeoutException -> SERVICE_UNAVAILABLE
        is MaxUploadSizeExceededException -> BAD_REQUEST
        is TransactionTimedOutException -> REQUEST_TIMEOUT
        // We don't know -> return nothing to continue resolving through the cause chain
        else -> null
    }

fun describe(status: HttpStatus) =
    ErrorDescription(
        message = status.reasonPhrase,
        key =
            if (status.is4xxClientError) {
                "error.client-error"
            } else {
                "error.internal-server-error"
            },
        params = localizationParams("code" to status.value()),
    )

fun describe(ex: Exception): ErrorDescription? {
    val message = ex.message ?: "${ex::class.simpleName}"
    return when (ex) {
        // Our own exceptions: prioritized for error display as they likely contain the most
        // understandable message
        is HasLocalizedMessage ->
            ErrorDescription(
                message = message,
                localizationKey = ex.localizationKey,
                localizationParams = ex.localizationParams,
                priority = ErrorPriority.HIGH,
            )

        // General Kotlin exceptions
        is IllegalArgumentException ->
            ErrorDescription(message = message, key = "error.exception.illegal-argument", priority = ErrorPriority.LOW)

        // Spring exceptions
        is AccessDeniedException -> ErrorDescription(message, "error.authentication.unauthorized")

        is HttpMessageNotReadableException ->
            ErrorDescription(message = "Request body not readable", key = "error.bad-request.invalid-body")

        is HttpRequestMethodNotSupportedException ->
            ErrorDescription(
                message = message,
                key = "error.bad-request.invalid-path",
                params = localizationParams("method" to ex.method),
            )

        is NoHandlerFoundException ->
            ErrorDescription(
                message = "No handler found: ${ex.httpMethod} ${ex.requestURL}",
                key = "error.bad-request.invalid-path",
                params = localizationParams("method" to ex.httpMethod),
            )

        is MissingServletRequestParameterException ->
            ErrorDescription(
                message = "Missing parameter: ${ex.parameterName} of type ${ex.parameterType}",
                key = "error.bad-request.missing-parameter",
                params = localizationParams("param" to ex.parameterName, "type" to ex.parameterType),
            )

        is MethodArgumentTypeMismatchException ->
            ErrorDescription(
                message = "Argument type mismatch: ${ex.name} (type ${ex.requiredType?.simpleName}) ${ex.parameter}",
                key = "error.bad-request.conversion-failed",
                params =
                    localizationParams(
                        "name" to ex.name,
                        "parameter" to ex.parameter,
                        "target" to ex.requiredType?.simpleName,
                    ),
            )

        is ConversionFailedException ->
            ErrorDescription(
                message =
                    "Conversion failed for value \"${ex.value}\": " +
                        "[${ex.sourceType?.type?.simpleName}] -> [${ex.targetType.type.simpleName}]",
                key = "error.bad-request.conversion-failed",
                params = localizationParams("target" to ex.targetType.type.simpleName),
                priority = ErrorPriority.LOW,
            )

        // Jackson exceptions
        is MismatchedInputException ->
            ErrorDescription(
                message = message,
                key = "error.bad-request.conversion-failed",
                params = localizationParams("target" to ex.targetType?.simpleName),
                priority = ErrorPriority.LOW,
            )

        is JsonParseException ->
            ErrorDescription(message = "Failed to parse JSON input", key = "error.bad-request.invalid-body")

        is ValueInstantiationException ->
            ErrorDescription(
                message = "Failed to instantiate ${ex.type?.genericSignature}",
                key = "error.bad-request.conversion-failed",
                params = localizationParams("target" to ex.type?.genericSignature),
                priority = ErrorPriority.LOW,
            )

        // Jaxb exceptions (XML parsing)
        is UnmarshalException -> ErrorDescription(message = message, key = "error.xml-unmarshal-failed")

        // Geotools exceptions
        is TransformException -> ErrorDescription(message = message, key = "error.coordinate-transformation-failed")

        // Token decoding: JWT.java
        is JWTDecodeException ->
            ErrorDescription(message = "JWT - Unparseable token: $message", key = "error.authentication.invalid-token")

        // Token verification: JWTVerifier.java
        is TokenExpiredException ->
            ErrorDescription(message = "JWT - Token expired: $message", key = "error.authentication.token-expired")

        is InvalidClaimException ->
            ErrorDescription(message = "JWT - Invalid claim: $message", key = "error.authentication.invalid-token")

        is SignatureVerificationException ->
            ErrorDescription(message = "JWT - Invalid signature: $message", key = "error.authentication.invalid-token")

        is AlgorithmMismatchException ->
            ErrorDescription(
                message = "JWT - Wrong signature algorithm: $message",
                key = "error.authentication.invalid-token",
            )

        is MaxUploadSizeExceededException ->
            ErrorDescription(
                message = "Maximum upload size exceeded: ${ex.mostSpecificCause.message}",
                key = "error.file-size-limit-exceeded",
            )

        is TransactionTimedOutException ->
            ErrorDescription(message = "Request timed out", key = "error.request-timed-out")

        // Switch to this if you want to see what types end up in the chain:
        //        else -> ErrorDescription(
        //            message = message,
        //            key = "error.exception",
        //            params = localizationParams("type" to ex::class.simpleName),
        //        )

        else -> null
    }
}

enum class ErrorPriority {
    HIGH,
    AVERAGE,
    LOW,
}

data class ErrorDescription(
    val message: String,
    val localizationKey: LocalizationKey,
    val localizationParams: LocalizationParams,
    val priority: ErrorPriority,
) {
    constructor(
        message: String,
        key: String,
        params: LocalizationParams = LocalizationParams.empty,
        priority: ErrorPriority = ErrorPriority.AVERAGE,
    ) : this(message, LocalizationKey(key), params, priority)
}

fun getPSQLExceptionConstraintAndDetailOrRethrow(psqlException: PSQLException): Pair<String, String> {
    val constraint = psqlException.serverErrorMessage?.constraint ?: throw psqlException
    val detail = psqlException.serverErrorMessage?.detail ?: throw psqlException

    return constraint to detail
}
