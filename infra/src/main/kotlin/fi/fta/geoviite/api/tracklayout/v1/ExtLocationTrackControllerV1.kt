package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.v1.LOCATION_TRACK_OID_PARAM
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

const val EXT_TRACK_LAYOUT_BASE_PATH = "/geoviite"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController([])
@Tag(name = "Rataverkon paikannuspohja V1")
class ExtLocationTrackControllerV1 {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping(
        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}",
        "/geoviite/dev/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}",
    )
    fun extGetLocationTrack(@PathVariable(LOCATION_TRACK_OID_PARAM) oid: Oid<LocationTrack>): String {
        return "Scaffolding"
    }
}
