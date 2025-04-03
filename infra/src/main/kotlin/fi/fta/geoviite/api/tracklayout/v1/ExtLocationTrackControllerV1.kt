package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController([])
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
    fun extGetLocationTrack(
        @PathVariable(LOCATION_TRACK_OID_PARAM) oid: Oid<LocationTrack>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>? = null,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid = LAYOUT_SRID,
    ): ExtLocationTrackResponseV1 {
        return extLocationTrackService.locationTrackResponse(oid, trackNetworkVersion, coordinateSystem)
    }

    @GetMapping(
        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}",
        params = [MODIFICATIONS_FROM_VERSION],
    )
    fun extGetLocationTrackModifications(
        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: Oid<LocationTrack>,
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>? = null,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid = LAYOUT_SRID,
    ): ResponseEntity<ExtModifiedLocationTrackResponseV1> {
        return extLocationTrackService
            .locationTrackModificationResponse(
                locationTrackOid,
                modificationsFromVersion,
                trackNetworkVersion,
                coordinateSystem,
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }

    @GetMapping("/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria")
    fun extGetLocationTrackGeometry(
        @PathVariable(LOCATION_TRACK_OID_PARAM) oid: Oid<LocationTrack>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>? = null,
        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false) resolution: Resolution = Resolution.ONE_METER,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid = LAYOUT_SRID,
        @RequestParam(TRACK_KILOMETER_START_PARAM, required = false) trackKmStart: KmNumber? = null,
        @RequestParam(TRACK_KILOMETER_END_PARAM, required = false) trackKmEnd: KmNumber? = null,
    ): ExtLocationTrackGeometryResponseV1 {
        return extLocationTrackService.locationTrackGeometryResponse(
            oid,
            trackNetworkVersion,
            resolution,
            coordinateSystem,
            ExtTrackKilometerIntervalV1(trackKmStart, trackKmEnd),
        )
    }

    @GetMapping(
        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria",
        params = [MODIFICATIONS_FROM_VERSION],
    )
    fun extGetLocationTrackGeometryModifications(
        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: Oid<LocationTrack>,
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>? = null,
        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false) resolution: Resolution = Resolution.ONE_METER,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid = LAYOUT_SRID,
        @RequestParam(TRACK_KILOMETER_START_PARAM, required = false) trackKmStart: KmNumber? = null,
        @RequestParam(TRACK_KILOMETER_END_PARAM, required = false) trackKmEnd: KmNumber? = null,
    ): ResponseEntity<ExtModifiedLocationTrackGeometryResponseV1> {
        return extLocationTrackService
            .locationTrackGeometryModificationResponse(
                locationTrackOid,
                modificationsFromVersion,
                trackNetworkVersion,
                resolution,
                coordinateSystem,
                ExtTrackKilometerIntervalV1(trackKmStart, trackKmEnd),
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }
}
