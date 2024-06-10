package fi.fta.geoviite.infra.environmentInfo

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping

@GeoviiteController("/environment")
class EnvironmentInfoController @Autowired constructor(val info: EnvironmentInfo) {

    @PreAuthorize(AUTH_BASIC)
    @GetMapping
    fun getEnvironmentInfo(): EnvironmentInfo {
        return info
    }
}
