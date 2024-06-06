package fi.fta.geoviite.infra.aspects

import fi.fta.geoviite.infra.logging.apiCall
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@RestController
annotation class GeoviiteController

@Aspect
@Component
class GeoviiteControllerAspect {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Before("within(@GeoviiteController *)")
    fun logBefore(joinPoint: JoinPoint) {
        if (logger.isInfoEnabled) {
            reflectedLogBefore(joinPoint, logger::apiCall)
        }
    }
}
