package fi.fta.geoviite.infra.cloudfront

import jakarta.annotation.PostConstruct
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.Security


@Component
class SecurityProviderInitialization {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    private fun init() {
        Security.addProvider(BouncyCastleProvider())
        logger.info("Added Bouncy Castle as Security Provider")
    }
}
