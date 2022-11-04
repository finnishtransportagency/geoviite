package fi.fta.geoviite.infra.configuration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS

@ConditionalOnWebApplication
@EnableWebSecurity
@Configuration
class SecurityConfiguration : WebSecurityConfigurerAdapter(true) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun configure(auth: AuthenticationManagerBuilder) {
        logger.info("Configuring authentication manager")
        auth.inMemoryAuthentication() // No users
    }

    override fun configure(http: HttpSecurity) {
        logger.info("Configuring HTTP Security")
        val httpConfig = http
            .httpBasic().disable()
            // Disable CORS: Not sharing anything with other domains
            .cors().disable()
            // Disable CSRF: JWT is stored in ALB -> session not configured here
            .csrf().disable()
            // Set session management to stateless
            .sessionManagement().sessionCreationPolicy(STATELESS).and()

        // Set permissions on endpoints
        httpConfig.authorizeRequests().anyRequest().authenticated().and()
    }
}
