package fi.fta.geoviite.infra.error

import correlationId
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.configuration.GeoviiteRequestType
import fi.fta.geoviite.infra.configuration.inferRequestType
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
@GeoviiteService
class ApiErrorHandler @Autowired constructor(private val localizationService: LocalizationService) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("Initialized error handler")
    }

    @ExceptionHandler(value = [(Exception::class)])
    fun handleAnyException(exception: Exception, request: WebRequest): ResponseEntity<GeoviiteErrorResponse> {
        return createErrorResponse(
            logger,
            exception,
            requestType = inferRequestType(request),
            translation = localizationService.getLocalization(LocalizationLanguage.FI),
        )
    }
}

fun createErrorResponse(
    logger: Logger,
    exception: Exception,
    requestType: GeoviiteRequestType,
    translation: Translation,
): ResponseEntity<GeoviiteErrorResponse> {
    val correlationId: String = correlationId.getOrNull() ?: "N/A"
    val response =
        handleErrorResponseCreation(exception, correlationId, requestType, translation)
            ?: run {
                logger.warn(
                    "Error handling failed. Defaulting to \"Internal server error\": " +
                        "correlationId=$correlationId exceptionChain=${getCauseChain(exception)}"
                )
                createTerseErrorResponse(correlationId, INTERNAL_SERVER_ERROR)
            }
    when {
        // Log server errors with full stack
        response.statusCode.is5xxServerError ->
            logger.error("Server error: correlationId=$correlationId $response", exception)
        // Log handled (client) errors with Exception.toString -> no stack
        response.statusCode.is4xxClientError ->
            logger.warn("Client error: correlationId=$correlationId $response exception=\"$exception\"")
        else ->
            logger.info(
                "Non-error response: correlationId=$correlationId ${response.statusCode} exception=\"$exception\""
            )
    }
    return response
}
