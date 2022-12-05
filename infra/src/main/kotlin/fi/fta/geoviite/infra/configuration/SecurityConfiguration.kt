package fi.fta.geoviite.infra.configuration

//import org.springframework.security.core.userdetails.UserDetailsService
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.web.SecurityFilterChain


@ConditionalOnWebApplication
@EnableWebSecurity // spring knows this is our security context
@Configuration // add to bean context
class SecurityConfiguration {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    @Throws(Exception::class)
    fun filterChain(http: HttpSecurity): SecurityFilterChain? {
        http
            .csrf().disable()
            .httpBasic().disable()
            .cors().disable()
            .authorizeRequests().anyRequest().authenticated().and()
            .sessionManagement().sessionCreationPolicy(STATELESS)
            .and()

        //.authorizeRequests().anyRequest().authenticated().and()

        return http.build()

        // httpConfig.authorizeRequests().anyRequest().authenticated().and()
    }

//    @Autowired
//    @Throws(java.lang.Exception::class)
//    fun configureGlobalSecurity(auth: AuthenticationManagerBuilder) {
//        auth.inMemoryAuthentication()
//    }

    @Bean
    @Throws(java.lang.Exception::class)
    fun authManager(
        http: HttpSecurity,
    ): AuthenticationManager? {
        return http.getSharedObject(AuthenticationManagerBuilder::class.java)
            .inMemoryAuthentication()
            .and()
            .build()
    }

//    @Bean
//    fun configure(auth: AuthenticationManagerBuilder) {
//        logger.info("Configuring authentication manager")
//        auth.inMemoryAuthentication() // No users
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
