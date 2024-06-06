package fi.fta.geoviite.infra.aspects

import fi.fta.geoviite.infra.logging.daoAccess
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
annotation class GeoviiteDao

@Aspect
@Component
class GeoviiteDaoAspect {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @AfterReturning("within(@GeoviiteDao *)", returning = "result")
    fun logAfterReturn(joinPoint: JoinPoint, result: Any?) {
        if (logger.isInfoEnabled) {
            reflectedLogWithReturnValue(joinPoint, result, logger::daoAccess)
        }
    }
}
