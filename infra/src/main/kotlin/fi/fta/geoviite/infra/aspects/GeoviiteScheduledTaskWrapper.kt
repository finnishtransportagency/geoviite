package fi.fta.geoviite.infra.aspects

import fi.fta.geoviite.infra.logging.withLogSpan
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class GeoviiteScheduledTaskWrapper {
    val logger: Logger = LoggerFactory.getLogger(GeoviiteScheduledTaskWrapper::class.java)

    @Around("execution(@org.springframework.scheduling.annotation.Scheduled * *(..))")
    fun aroundScheduledTask(joinPoint: ProceedingJoinPoint?): Any? {
        return withLogSpan {
            try {
                joinPoint?.proceed()
            } catch (exception: Exception) {
                logger.error("caught an exception: ${exception.message}", exception)
            }
        }
    }
}
