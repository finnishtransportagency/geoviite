package fi.fta.geoviite.infra.configuration

//import org.springframework.security.core.userdetails.UserDetailsService
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain


@ConditionalOnWebApplication
@EnableWebSecurity // spring knows this is our security context
@Configuration // add to bean context
@EnableMethodSecurity
//@EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityConfiguration {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

//    @Bean
//    fun configureAuthentication(): InMemoryUserDetailsManager {
//        logger.info("Configuring authentication manager")
//        /* auth.inMemoryAuthentication() // No users */
//        return InMemoryUserDetailsManager()
//    }

    // not a recommended approach
//    @Bean
//    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager {
//        return authenticationConfiguration.getAuthenticationManager();
//    }

    @Bean
    fun authManager(userDetailService: UserDetailsService): AuthenticationManager {
        val authProvider = DaoAuthenticationProvider()
        authProvider.setUserDetailsService(userDetailService)
        return ProviderManager(authProvider)

    }

    @Bean
    fun userDetailsService(): UserDetailsService {

        return InMemoryUserDetailsManager(
            User.withUsername("test")
                .password("password")
                .authorities("read")
                .build()
        )
    }

    @Bean
    @Throws(Exception::class)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { csrf -> csrf.disable() }
            //.authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
            //.httpBasic().disable()
            .cors { cors -> cors.disable() }
            //.authorizeRequests().anyRequest().fullyAuthenticated().and()
            //.authorizeRequests().anyRequest().hasAnyAuthority().and()
            .sessionManagement().sessionCreationPolicy(STATELESS)
            .and()
            .authorizeHttpRequests()
            .anyRequest().permitAll()
            .and()
            .build()
    }


//    fun configure(auth: AuthenticationManagerBuilder): InMemoryUserDetailsManagerConfigurer<AuthenticationManagerBuilder>? {
//        logger.info("Configuring authentication manager")
//        return auth.inMemoryAuthentication() // No users
//    }

//    override fun configure(http: HttpSecurity) {
//        logger.info("Configuring HTTP Security")
//        val httpConfig = http
//            .httpBasic().disable()
//            // Disable CORS: Not sharing anything with other domains
//            .cors().disable()
//            // Disable CSRF: JWT is stored in ALB -> session not configured here
//            .csrf().disable()
//            // Set session management to stateless
//            .sessionManagement().sessionCreationPolicy(STATELESS).and()
//
//        // Set permissions on endpoints
//        httpConfig.authorizeRequests().anyRequest().authenticated().and()
//    }


}
