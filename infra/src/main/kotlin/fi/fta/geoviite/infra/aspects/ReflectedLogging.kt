package fi.fta.geoviite.infra.aspects

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.DefaultParameterNameDiscoverer

/*
 * Annotate a function which should not be automatically logged at all.
 * Example:
 *
 * @GeoviiteController("/some-base-path")
 * class SomeController {
 *    @GetMapping("/some-path")
 *    @DisableDefaultGeoviiteLogging
 *    fun someFunction(
 *        @RequestParam("someAutomaticallyLoggedParam") foo: Int,
 *        @RequestParam("someOtherNotLoggedParam") bar: Int,
 *    } {
 *        // This function call is not written to the log at all. Manual logging can be used instead.
 *    }
 * }
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DisableDefaultGeoviiteLogging

/*
 * Annotate a function parameter which should not be automatically written to log
 * Example:
 *
 * @GeoviiteService
 * class SomeService {
 *    fun someFunction(
 *        @RequestParam("someAutomaticallyLoggedParam") someAutomaticallyLoggedParam: Int,
 *        @DoNotWriteToLog @RequestParam("someOtherNotLoggedParam") someOtherNotLoggedParam: Int,
 *    } {
 *        // This function call is logged, but only the someAutomaticallyLoggedParam is written to the log.
 *    }
 * }
 */
@Target(AnnotationTarget.VALUE_PARAMETER) @Retention(AnnotationRetention.RUNTIME) annotation class DoNotWriteToLog

private val parameterNameDiscoverer = DefaultParameterNameDiscoverer()

fun reflectedLogBefore(
    joinPoint: JoinPoint,
    loggerMethod: (methodName: String, params: List<Pair<String, *>>) -> Unit,
) {
    logInternal(joinPoint = joinPoint, loggerMethod = loggerMethod)
}

fun reflectedLogWithReturnValue(
    joinPoint: JoinPoint,
    returnValue: Any?,
    loggerMethodWithReturnValue: (methodName: String, params: List<Pair<String, *>>, returnValue: Any?) -> Unit,
) {
    logInternal(
        joinPoint = joinPoint,
        returnValue = returnValue,
        loggerMethodWithReturnValue = loggerMethodWithReturnValue,
    )
}

private fun logInternal(
    joinPoint: JoinPoint,
    returnValue: Any? = null,
    loggerMethod: ((String, List<Pair<String, *>>) -> Unit)? = null,
    loggerMethodWithReturnValue: ((String, List<Pair<String, *>>, Any?) -> Unit)? = null,
) {
    val method = (joinPoint.signature as MethodSignature).method

    if (method.isAnnotationPresent(DisableDefaultGeoviiteLogging::class.java)) {
        return
    } else {
        val methodName = joinPoint.signature.name
        val loggedParams = reflectParams(joinPoint)

        loggerMethod?.invoke(methodName, loggedParams)
        loggerMethodWithReturnValue?.invoke(methodName, loggedParams, returnValue)
    }
}

private fun reflectParams(joinPoint: JoinPoint): List<Pair<String, *>> {
    val method = (joinPoint.signature as MethodSignature).method
    val parameterNames = parameterNameDiscoverer.getParameterNames(method) ?: emptyArray()

    return parameterNames
        .filterIndexed { index, _ ->
            method.parameterAnnotations[index].none { annotation -> annotation is DoNotWriteToLog }
        }
        .zip(joinPoint.args)
}
