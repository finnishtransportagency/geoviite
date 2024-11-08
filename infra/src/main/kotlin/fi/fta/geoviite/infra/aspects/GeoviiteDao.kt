package fi.fta.geoviite.infra.aspects

import fi.fta.geoviite.infra.logging.daoAccess
import java.util.concurrent.ConcurrentHashMap
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.RUNTIME) @Component annotation class GeoviiteDao

@Aspect
@Component
class GeoviiteDaoAspect {
    private val loggerCache = ConcurrentHashMap<Class<*>, Logger>()

    @AfterReturning("within(@GeoviiteDao *)", returning = "result")
    fun logAfterReturn(joinPoint: JoinPoint, result: Any?) {
        val targetClass = joinPoint.target::class.java
        val logger = loggerCache.computeIfAbsent(targetClass) { classRef -> LoggerFactory.getLogger(classRef) }

        if (logger.isInfoEnabled) {
            reflectedLogWithReturnValue(joinPoint, result, logger::daoAccess)
        }
    }
}
