package fi.fta.geoviite.infra.aspects

import fi.fta.geoviite.infra.logging.serviceCall
import java.util.concurrent.ConcurrentHashMap
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.RUNTIME) @Service annotation class GeoviiteService

@Aspect
@Component
class GeoviiteServiceAspect {
    private val loggerCache = ConcurrentHashMap<Class<*>, Logger>()

    @Before("within(@GeoviiteService *)")
    fun logBefore(joinPoint: JoinPoint) {
        val targetClass = joinPoint.target::class.java
        val logger = loggerCache.computeIfAbsent(targetClass) { classRef -> LoggerFactory.getLogger(classRef) }

        if (logger.isDebugEnabled) {
            reflectedLogBefore(joinPoint, logger::serviceCall)
        }
    }
}
