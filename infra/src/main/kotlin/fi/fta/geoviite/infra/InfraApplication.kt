package fi.fta.geoviite.infra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity

@SpringBootApplication(exclude = [
    SecurityAutoConfiguration::class,
    UserDetailsServiceAutoConfiguration::class,
])
@EnableScheduling
@EnableGlobalMethodSecurity(prePostEnabled = true)
class InfraApplication

fun main(args: Array<String>) {
    runApplication<InfraApplication>(*args)
}
