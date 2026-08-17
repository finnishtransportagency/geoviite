package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.geography.initGeotools
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class, UserDetailsServiceAutoConfiguration::class])
@EnableMethodSecurity
@ComponentScan(basePackages = ["fi.fta.geoviite"])
class InfraApplication

@Configuration
@ConditionalOnProperty(name = ["geoviite.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
@EnableScheduling
class InfraScheduling

fun main(args: Array<String>) {
    initGeotools()
    runApplication<InfraApplication>(*args)
}
