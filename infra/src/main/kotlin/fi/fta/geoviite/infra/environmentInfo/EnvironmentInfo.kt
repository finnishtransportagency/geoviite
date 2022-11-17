package fi.fta.geoviite.infra.environmentInfo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
data class EnvironmentInfo(
    @Value("\${RELEASE_VERSION:Geoviite}") val releaseVersion: String,
    @Value("\${RELEASE_ENVIRONMENT:local}") val environmentName: String,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        logger.info("Application started in environment $this")
    }
}
