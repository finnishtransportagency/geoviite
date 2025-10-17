package fi.fta.geoviite.infra.aspects

import fi.fta.geoviite.infra.logging.apiCall
import java.util.concurrent.ConcurrentHashMap
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AliasFor
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@AtLeastOneProfile("backend", "ext-api")
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@RestController
@RequestMapping
annotation class GeoviiteCommonController(@get:AliasFor(annotation = RequestMapping::class) val path: String)

@Aspect
@Component
class GeoviiteCommonControllerAspect {
    private val loggerCache = ConcurrentHashMap<Class<*>, Logger>()

    @Before("within(@GeoviiteController *)")
    fun logBefore(joinPoint: JoinPoint) {
        val targetClass = joinPoint.target::class.java
        val logger = loggerCache.computeIfAbsent(targetClass) { classRef -> LoggerFactory.getLogger(classRef) }

        if (logger.isInfoEnabled) {
            reflectedLogBefore(joinPoint, logger::apiCall)
        }
    }
}
