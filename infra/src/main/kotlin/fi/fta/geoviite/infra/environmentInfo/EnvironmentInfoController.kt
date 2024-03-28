package fi.fta.geoviite.infra.environmentInfo

import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import fi.fta.geoviite.infra.logging.apiCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/environment")
class EnvironmentInfoController @Autowired constructor(val info: EnvironmentInfo) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_BASIC)
    @GetMapping
    fun getEnvironmentInfo(): EnvironmentInfo {
        logger.apiCall("getEnvironmentInfo")
        return info
    }
}
