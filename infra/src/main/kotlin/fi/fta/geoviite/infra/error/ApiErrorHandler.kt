package fi.fta.geoviite.infra.error

import correlationId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest

@ConditionalOnWebApplication
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
class ApiErrorHandler {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        log.info("Initialized error handler")
    }

    @ExceptionHandler(value = [(Exception::class)])
    fun handleAnyException(ex: Exception, request: WebRequest): ResponseEntity<ApiErrorResponse> =
        createErrorResponse(log, ex)
}

fun createErrorResponse(log: Logger, ex: Exception): ResponseEntity<ApiErrorResponse> {
    val correlationId: String = correlationId.getOrNull() ?: "N/A"
    val response =
        createResponse(ex, correlationId)
            ?: run {
                log.warn(
                    "Error handling failed. Defaulting to \"Internal server error\": " +
                        "correlationId=$correlationId exceptionChain=${getCauseChain(ex)}"
                )
                createTerseErrorResponse(correlationId, INTERNAL_SERVER_ERROR)
            }
    when {
        // Log server errors with full stack
        response.statusCode.is5xxServerError -> log.error("Server error: correlationId=$correlationId $response", ex)
        // Log handled (client) errors with Exception.toString -> no stack
        response.statusCode.is4xxClientError ->
            log.warn("Client error: correlationId=$correlationId $response exception=\"$ex\"")
        else -> log.info("Non-error response: correlationId=$correlationId ${response.statusCode} exception=\"$ex\"")
    }
    return response
}
