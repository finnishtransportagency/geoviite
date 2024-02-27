package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.authorization.AUTH_UI_READ
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.Point
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/geography")
class GeographyController(
    private val geographyService: GeographyService,
    private val coordinateTransformationService: CoordinateTransformationService,
) {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/coordinate-systems")
    fun getCoordinateSystems(): List<CoordinateSystem> {
        log.apiCall("getCoordinateSystems")
        return geographyService.getCoordinateSystems()
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/coordinate-systems/{srid}")
    fun getCoordinateSystem(@PathVariable("srid") srid: Srid): CoordinateSystem {
        log.apiCall("getCoordinateSystem", "srid" to srid)
        return geographyService.getCoordinateSystem(srid)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/coordinate-systems/{srid}/to/{toSrid}/{x}/{y}")
    fun getConvertedCoordinate(
        @PathVariable("srid") srid: Srid,
        @PathVariable("toSrid") toSrid: Srid,
        @PathVariable("x") x: Double,
        @PathVariable("y") y: Double,
    ): Point {
        log.apiCall(
            "getConvertedCoordinate", "srid" to srid, "toSrid" to toSrid, "x" to x, "y" to y
        )
        return coordinateTransformationService.getTransformation(srid, toSrid).transform(Point(x, y))
    }
}
