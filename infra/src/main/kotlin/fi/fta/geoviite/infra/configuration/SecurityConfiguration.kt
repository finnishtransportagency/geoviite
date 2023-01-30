package fi.fta.geoviite.infra.configuration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.web.SecurityFilterChain


@ConditionalOnWebApplication
@EnableWebSecurity
@Configuration
class SecurityConfiguration {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info("Configuring HTTP Security")
        return http
            .httpBasic().disable()
            // Disable CORS: Not sharing anything with other domains
            .cors().disable()
            // Disable CSRF: JWT is stored in ALB -> session not configured here
            .csrf().disable()
            // Disable SecurityContextPersistenceFilter which by default would replace the security context set up in
            // RequestFilter with an empty one
            .securityContext().disable()
            // Set session management to stateless
            .sessionManagement().sessionCreationPolicy(STATELESS).and()
            // Set permissions on endpoints
            .authorizeHttpRequests().anyRequest().authenticated().and()
            .build()
    }
}
