package fi.fta.geoviite.infra.aspects

import fi.fta.geoviite.infra.logging.serviceCall
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class GeoviiteService

@Aspect
@Component
class GeoviiteServiceAspect {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Before("within(@GeoviiteService *)")
    fun logBefore(joinPoint: JoinPoint) {
        if (logger.isDebugEnabled) {
            reflectedLogBefore(joinPoint, logger::serviceCall)
        }
    }
}
