package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.logging.apiCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/geography")
class GeographyController(private val geographyService: GeographyService) {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/coordinate-systems")
    fun getCoordinateSystems(): List<CoordinateSystem> {
        log.apiCall("getCoordinateSystems")
        return geographyService.getCoordinateSystems()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/coordinate-systems/{srid}")
    fun getCoordinateSystem(@PathVariable("srid") srid: Srid): CoordinateSystem {
        log.apiCall("getCoordinateSystem", "srid" to srid)
        return geographyService.getCoordinateSystem(srid)
    }
}
