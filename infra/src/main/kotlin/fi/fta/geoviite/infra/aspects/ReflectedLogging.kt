package fi.fta.geoviite.infra.aspects

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.DefaultParameterNameDiscoverer

/*
 * Annotate a function which should not be automatically logged at all.
 * Example:
 *
 * @GeoviiteController
 * class SomeController {
 *    @GetMapping("/some-path")
 *    @DisableLogging
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
annotation class DisableLogging

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
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class DoNotWriteToLog

private val parameterNameDiscoverer = DefaultParameterNameDiscoverer()

fun reflectedLogBefore(
    joinPoint: JoinPoint,
    loggerMethod: (
        className: String,
        methodName: String,
        params: List<Pair<String, *>>,
    ) -> Unit,
) {
    logInternal(
        joinPoint = joinPoint,
        loggerMethod = loggerMethod,
    )
}

fun reflectedLogWithReturnValue(
    joinPoint: JoinPoint,
    returnValue: Any?,
    loggerMethodWithReturnValue: (
        className: String,
        methodName: String,
        params: List<Pair<String, *>>,
        returnValue: Any?,
    ) -> Unit,
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
    loggerMethod: ((String, String, List<Pair<String, *>>) -> Unit)? = null,
    loggerMethodWithReturnValue: ((String, String, List<Pair<String, *>>, Any?) -> Unit)? = null,
) {
    val method = (joinPoint.signature as MethodSignature).method

    if (method.isAnnotationPresent(DisableLogging::class.java)) {
        return
    } else {
        val className = joinPoint.target.javaClass.simpleName
        val methodName = joinPoint.signature.name
        val loggedParams = reflectParams(joinPoint)

        loggerMethod?.invoke(className, methodName, loggedParams)
        loggerMethodWithReturnValue?.invoke(className, methodName, loggedParams, returnValue)
    }
}

private fun reflectParams(
    joinPoint: JoinPoint,
): List<Pair<String, *>> {
    val method = (joinPoint.signature as MethodSignature).method
    val parameterNames = parameterNameDiscoverer.getParameterNames(method) ?: emptyArray()

    return parameterNames
        .filterIndexed { index, _ ->
            method.parameterAnnotations[index].none { annotation ->
                annotation is DoNotWriteToLog
            }
        }
        .zip(joinPoint.args)
}
