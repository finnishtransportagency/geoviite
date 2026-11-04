package fi.fta.geoviite.infra.authorization

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.parameters.DefaultSecurityParameterNameDiscoverer
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@ActiveProfiles("dev", "test", "backend")
@SpringBootTest
class PreAuthorizeParameterNamesIT @Autowired constructor(private val applicationContext: ApplicationContext) {

    @Test
    fun `every PreAuthorize expression variable matches a parameter of the annotated method`() {
        // Same discoverer Spring Security uses to bind #name variables, so a mismatch here evaluates to null at runtime
        val parameterNameDiscoverer = DefaultSecurityParameterNameDiscoverer()
        val checkedMethods =
            applicationContext
                .getBeansOfType(RequestMappingHandlerMapping::class.java)
                .values
                .flatMap { mapping -> mapping.handlerMethods.values }
                .mapNotNull { handlerMethod ->
                    AnnotatedElementUtils.findMergedAnnotation(handlerMethod.method, PreAuthorize::class.java)?.let {
                        annotation ->
                        handlerMethod.method to annotation.value
                    }
                }
                .filter { (_, expression) -> expression.contains('#') }

        assertTrue(checkedMethods.isNotEmpty(), "Expected to find PreAuthorize expressions with variables")

        val mismatches = checkedMethods.flatMap { (method, expression) ->
            val parameterNames = parameterNameDiscoverer.getParameterNames(method)?.toSet() ?: emptySet()
            Regex("#(\\w+)")
                .findAll(expression)
                .map { match -> match.groupValues[1] }
                .filter { variable -> variable !in parameterNames }
                .map { variable -> "${method.declaringClass.simpleName}.${method.name}: #$variable" }
                .toList()
        }

        assertEquals(emptyList(), mismatches.distinct())
    }
}
