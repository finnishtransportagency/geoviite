package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.Point
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@GeoviiteController("/geography")
class GeographyController(
    private val geographyService: GeographyService,
    private val coordinateTransformationService: CoordinateTransformationService,
) {

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/coordinate-systems")
    fun getCoordinateSystems(): List<CoordinateSystem> {
        return geographyService.getCoordinateSystems()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/coordinate-systems/{srid}")
    fun getCoordinateSystem(@PathVariable("srid") srid: Srid): CoordinateSystem {
        return geographyService.getCoordinateSystem(srid)
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/coordinate-systems/{srid}/transformations/{toSrid}")
    fun getConvertedCoordinate(
        @PathVariable("srid") srid: Srid,
        @PathVariable("toSrid") toSrid: Srid,
        @RequestParam point: Point,
    ): Point {
        return coordinateTransformationService.getTransformation(srid, toSrid).transform(point)
    }
}
