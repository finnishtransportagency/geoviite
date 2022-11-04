package fi.fta.geoviite.infra.error

import com.auth0.jwt.exceptions.*
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import jakarta.xml.bind.UnmarshalException
import org.opengis.referencing.operation.TransformException
import org.springframework.beans.ConversionNotSupportedException
import org.springframework.beans.TypeMismatchException
import org.springframework.boot.context.properties.bind.BindException
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException

fun createResponse(exception: Exception, correlationId: String): ResponseEntity<ApiErrorResponse>? {
    val causeChain = getCauseChain(exception)
    val status = getStatusCode(causeChain)
    val localizedMessageKey = causeChain.firstNotNullOfOrNull(::getLocalizationKey)
    return status?.let { s ->
        if (s.is5xxServerError) createStatusOnlyErrorResponse(correlationId, s, localizedMessageKey)
        else createDescriptiveErrorResponse(correlationId, s, causeChain, localizedMessageKey)
    }
}

fun createStatusOnlyErrorResponse(correlationId: String, status: HttpStatus, localizedMessageKey: String? = null) =
    createResponse(listOf(status.reasonPhrase), status, correlationId, localizedMessageKey)

fun createDescriptiveErrorResponse(
    correlationId: String,
    status: HttpStatus,
    causeChain: List<Exception>,
    localizedMessageKey: String? = null,
) = createResponse(causeChain.mapNotNull(::describe), status, correlationId, localizedMessageKey)

fun createResponse(
    messageRows: List<String>,
    status: HttpStatus,
    correlationId: String,
    localizedMessageKey: String? = null,
): ResponseEntity<ApiErrorResponse> {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    return ResponseEntity(ApiErrorResponse(messageRows, correlationId, localizedMessageKey), headers, status)
}

fun getCauseChain(exception: Exception): List<Exception> {
    val chain = mutableListOf<Exception>()
    var current: Throwable? = exception
    while (current != null) {
        when (current) {
            is Exception -> chain.add(current)
            is Error -> throw IllegalStateException("Errors are not meant to be caught! Tried to handle $current")
        }
        current = current.cause
    }
    return chain
}

fun getStatusCode(causeChain: List<Exception>): HttpStatus? =
    if (causeChain.any { e -> e is ServerException }) INTERNAL_SERVER_ERROR
    else causeChain.mapNotNull(::getStatusCode).firstOrNull()

fun getStatusCode(exception: Exception): HttpStatus? = when (exception) {
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
    // We don't know -> return nothing to continue resolving through the cause chain
    else -> null
}

fun getLocalizationKey(exception: Exception): String? =
    if (exception is HasLocalizeMessageKey) exception.localizedMessageKey
    else null

fun describe(exception: Exception): String? = when (exception) {
    // Our own exceptions
    is ClientException -> exception.message
    is AccessDeniedException -> exception.message
    // General Kotlin exceptions
    is IllegalArgumentException -> exception.message
    // Spring exceptions
    is HttpRequestMethodNotSupportedException -> exception.message
    is MissingServletRequestParameterException -> "Missing parameter: ${exception.parameterName} of type ${exception.parameterType}"
    is HttpMessageNotReadableException -> "Request body not readable"
    is NoHandlerFoundException -> "No handler found: ${exception.httpMethod} ${exception.requestURL}"
    is MethodArgumentTypeMismatchException -> "Argument type mismatch: ${exception.parameter}"
    is ConversionFailedException -> "Conversion failed for value \"${exception.value}\": [${exception.sourceType?.type?.simpleName}] -> [${exception.targetType.type.simpleName}]"
    is MissingKotlinParameterException -> "Missing parameter \"${exception.parameter.name}\""
    is MismatchedInputException -> exception.toString()
    is JsonParseException -> "Failed to parse JSON input"
    is ValueInstantiationException -> "Failed to instantiate ${exception.type.genericSignature}"
    // Jaxb exceptions (XML parsing)
    is UnmarshalException -> exception.message
    // Geotools exceptions
    is TransformException -> exception.message
    // Token decoding: JWT.java
    is JWTDecodeException -> "JWT - Unparseable token: ${exception.message}"
    // Token verification: JWTVerifier.java
    is InvalidClaimException -> "JWT - Invalid claim: ${exception.message}"
    is TokenExpiredException -> "JWT - Token expired: ${exception.message}"
    is SignatureVerificationException -> "JWT - Invalid signature: ${exception.message}"
    is AlgorithmMismatchException -> "JWT - Wrong signature algorithm: ${exception.message}"
    // Switch to this if you want to see what types end up in the chain:
//    else -> "<${exception::class.simpleName}>"
    else -> null
}
