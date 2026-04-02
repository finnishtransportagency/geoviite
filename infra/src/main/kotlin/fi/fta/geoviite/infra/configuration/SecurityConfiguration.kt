package fi.fta.geoviite.infra.configuration

import fi.fta.geoviite.api.openapi.OPENAPI_GEOVIITE_DEV_PATH
import fi.fta.geoviite.api.openapi.OPENAPI_GEOVIITE_PATH
import fi.fta.geoviite.infra.authorization.AUTH_FLAG_API_GEOMETRY
import jakarta.servlet.DispatcherType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

@ConditionalOnWebApplication
@EnableWebSecurity
@Configuration
class SecurityConfiguration {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info("Configuring HTTP Security")
        return http
            .httpBasic { it.disable() }
            // Disable CORS: Not sharing anything with other domains
            .cors { it.disable() }
            // Disable CSRF: JWT is stored in ALB -> session not configured here
            .csrf { it.disable() }
            // Disable SecurityContextPersistenceFilter which by default would replace the security
            // context set up in
            // RequestFilter with an empty one
            .securityContext { it.disable() }
            // Set session management to stateless
            .sessionManagement { it.sessionCreationPolicy(STATELESS) }
            // Set permissions on endpoints
            .authorizeHttpRequests { auth ->
                auth
                    // Block direct access to internal SpringDoc api-docs paths. These are only
                    // meant to be reached via server-side forwards from SwaggerController, which
                    // are not matched here (FORWARD dispatch type is excluded).
                    .requestMatchers(
                        directRequestMatcher("/v3/api-docs/**"),
                        directRequestMatcher("/dev/v3/api-docs/**"),
                    )
                    .denyAll()
                    // Browser-facing openapi forwarding paths for geoviite.
                    .requestMatchers(OPENAPI_GEOVIITE_PATH, OPENAPI_GEOVIITE_DEV_PATH)
                    .hasAuthority(AUTH_FLAG_API_GEOMETRY)
                    // All others
                    .anyRequest()
                    .authenticated()
            }
            .build()
    }
}

private fun directRequestMatcher(pattern: String): RequestMatcher {
    val pathMatcher = PathPatternRequestMatcher.withDefaults().matcher(pattern)
    return RequestMatcher { request ->
        request.dispatcherType == DispatcherType.REQUEST && pathMatcher.matches(request)
    }
}
