package fi.fta.geoviite.api.aspects

import fi.fta.geoviite.infra.aspects.reflectedLogBefore
import fi.fta.geoviite.infra.logging.apiCall
import java.util.concurrent.ConcurrentHashMap
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.AliasFor
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("ext-api")
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@RestController
@RequestMapping
annotation class GeoviiteExtApiController(@get:AliasFor(annotation = RequestMapping::class) val path: Array<String>)

@Aspect
@Component
class GeoviiteExtApiControllerAspect {
    private val loggerCache = ConcurrentHashMap<Class<*>, Logger>()

    @Before("within(@GeoviiteExtApiController *)")
    fun logBefore(joinPoint: JoinPoint) {
        val targetClass = joinPoint.target::class.java
        val logger = loggerCache.computeIfAbsent(targetClass) { classRef -> LoggerFactory.getLogger(classRef) }

        if (logger.isInfoEnabled) {
            reflectedLogBefore(joinPoint, logger::apiCall)
        }
    }
}
