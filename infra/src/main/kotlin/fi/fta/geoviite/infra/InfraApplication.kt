package fi.fta.geoviite.infra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

@SpringBootApplication(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ]
)
@EnableMethodSecurity
class InfraApplication

@Configuration
@ConditionalOnProperty(prefix = "geoviite.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableScheduling
class InfraScheduling

fun main(args: Array<String>) {
    runApplication<InfraApplication>(*args)
}
