package fi.fta.geoviite.infra.environmentInfo

import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

const val GEOVIITE_SUPPORT_EMAIL = "GEOVIITE_SUPPORT_EMAIL"

@Component
data class EnvironmentInfo(
    @Value("\${RELEASE_VERSION:Geoviite}") val releaseVersion: String,
    @Value("\${RELEASE_ENVIRONMENT:local}") val environmentName: String,
    @Value("\${${GEOVIITE_SUPPORT_EMAIL}:local-geoviite-support@example.com}") val geoviiteSupportEmailAddress: String,
    @Value("\${RATKO_SUPPORT_EMAIL:local-ratko-support@example.com}") val ratkoSupportEmailAddress: String,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        logger.info("Application started in environment $this")
    }
}
