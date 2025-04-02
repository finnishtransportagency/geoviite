package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.v1.LOCATION_TRACK_OID_PARAM
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.tags.Tag
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController([])
@Tag(name = "Rataverkon paikannuspohja V1")
class ExtLocationTrackControllerV1(private val extLocationTrackService: ExtLocationTrackServiceV1) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    //    @GetMapping(
    //        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}",
    //        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/",
    //    )
    //    fun singleTrackCenterLineGeometry(
    //        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: ApiRequestStringV1,
    //        @RequestParam(TRACK_NETWORK_VERSION) trackNetworkVersion: ApiRequestStringV1?,
    //        @RequestParam(MODIFICATIONS_FROM_VERSION) modificationsFromVersion: ApiRequestStringV1?,
    //        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: ApiRequestStringV1?,
    //    ): CenterLineGeometryResponseV1 {
    //        val translation = localizationService.getLocalization(LocalizationLanguage.FI)
    //
    //        val (validationErrors, validRequest) =
    //            LocationTrackRequestV1(locationTrackOid, coordinateSystem).let(centerLineGeometryService::validate)
    //
    //        return validRequest?.let { processValidatedRequest(translation, validRequest) }
    //            ?: createErrorResponse(translation, validationErrors)
    //    }

    @GetMapping("/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}")
    fun locationTrack(
        @PathVariable(LOCATION_TRACK_OID_PARAM) oid: Oid<LocationTrack>,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid = LAYOUT_SRID,
    ): ExtLocationTrackResponseV1 {
        return extLocationTrackService.locationTrackResponse(oid, coordinateSystem)
    }

    //    @GetMapping(
    //        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}",
    //        params = [MODIFICATIONS_FROM_VERSION],
    //    )
    //    fun locationTrackModifications(
    //        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: Oid<LocationTrack>,
    //        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid,
    //        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
    //    ): ExtModifiedLocationTrackResponseV1 {}
}
