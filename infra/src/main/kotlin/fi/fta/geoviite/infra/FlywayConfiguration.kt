package fi.fta.geoviite.infra

import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
@DependsOn("springContextUtility")
class FlywayConfiguration(@Value("\${geoviite.data.reset:false}") private val dataReset: Boolean) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun cleanMigrateStrategy(): FlywayMigrationStrategy? {
        return FlywayMigrationStrategy { flyway: Flyway ->
            logger.info("Creating migration strategy: dataReset=$dataReset")
            if (dataReset) {
                logger.warn("Data reset enabled. Cleaning database and migrating from scratch.")
                flyway.clean()
            }
            flyway.migrate()
        }
    }
}
