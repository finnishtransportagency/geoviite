package fi.fta.geoviite.infra

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct


@Component
class ReleaseVersionHolder(
    @Value("\${commit.hash:}") val commitHash: String?,
    @Value("\${version.release.number:}") val versionReleaseNumber: String?,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        logger.info("GeoviiteInfraCommitHash [{}] - GeoviiteInfraVersionReleaseNumber [{}]",
            commitHash,
            versionReleaseNumber)
    }
}
