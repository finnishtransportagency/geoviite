package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.v1.LOCATION_TRACK_OID_PARAM
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

const val EXT_TRACK_LAYOUT_BASE_PATH = "/geoviite"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    ["$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1", "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1"]
)
@Tag(name = "Rataverkon paikannuspohja V1")
class ExtLocationTrackControllerV1(
    private val extLocationTrackService: ExtLocationTrackServiceV1,
    private val extLocationTrackGeometryService: ExtLocationTrackGeometryServiceV1,
    private val extLocationTrackCollectionService: ExtLocationTrackCollectionServiceV1,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/sijaintiraiteet")
    fun extGetLocationTrackCollection(
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ExtLocationTrackCollectionResponseV1 {
        return extLocationTrackCollectionService.createLocationTrackCollectionResponse(
            trackNetworkVersion = trackNetworkVersion,
            coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
        )
    }

    @GetMapping("/sijaintiraiteet/muutokset", params = [MODIFICATIONS_FROM_VERSION])
    fun extGetLocationTrackCollectionModifications(
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedLocationTrackCollectionResponseV1> {
        return extLocationTrackCollectionService
            .createLocationTrackCollectionModificationResponse(
                modificationsFromVersion = modificationsFromVersion,
                trackNetworkVersion = trackNetworkVersion,
                coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}")
    fun extGetLocationTrack(
        @Parameter(description = LOCATION_TRACK_OID_DESCRIPTION)
        @PathVariable(LOCATION_TRACK_OID_PARAM)
        oid: Oid<LocationTrack>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ExtLocationTrackResponseV1 {
        return extLocationTrackService.createLocationTrackResponse(
            oid,
            trackNetworkVersion,
            coordinateSystem ?: LAYOUT_SRID,
        )
    }

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/muutokset", params = [MODIFICATIONS_FROM_VERSION])
    fun extGetLocationTrackModifications(
        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: Oid<LocationTrack>,
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedLocationTrackResponseV1> {
        return extLocationTrackService
            .createLocationTrackModificationResponse(
                locationTrackOid,
                modificationsFromVersion,
                trackNetworkVersion,
                coordinateSystem ?: LAYOUT_SRID,
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria")
    fun extGetLocationTrackGeometry(
        @PathVariable(LOCATION_TRACK_OID_PARAM) oid: Oid<LocationTrack>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>? = null,
        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false) extResolution: ExtResolutionV1? = null,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid? = null,
        @RequestParam(TRACK_KILOMETER_START_PARAM, required = false) trackKmStart: KmNumber? = null,
        @RequestParam(TRACK_KILOMETER_END_PARAM, required = false) trackKmEnd: KmNumber? = null,
    ): ExtLocationTrackGeometryResponseV1 {
        return extLocationTrackGeometryService.createGeometryResponse(
            oid,
            trackNetworkVersion,
            extResolution?.toResolution() ?: Resolution.ONE_METER,
            coordinateSystem ?: LAYOUT_SRID,
            ExtTrackKilometerIntervalV1(trackKmStart, trackKmEnd),
        )
    }

    @GetMapping(
        "/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria/muutokset",
        params = [MODIFICATIONS_FROM_VERSION],
    )
    fun extGetLocationTrackGeometryModifications(
        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: Oid<LocationTrack>,
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
        @RequestParam(TRACK_NETWORK_VERSION, required = false) trackNetworkVersion: Uuid<Publication>? = null,
        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false) extResolution: ExtResolutionV1? = null,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid? = null,
        @RequestParam(TRACK_KILOMETER_START_PARAM, required = false) trackKmStart: KmNumber? = null,
        @RequestParam(TRACK_KILOMETER_END_PARAM, required = false) trackKmEnd: KmNumber? = null,
    ): ResponseEntity<ExtLocationTrackModifiedGeometryResponseV1> {
        return extLocationTrackGeometryService
            .createGeometryModificationResponse(
                locationTrackOid,
                modificationsFromVersion,
                trackNetworkVersion,
                extResolution?.toResolution() ?: Resolution.ONE_METER,
                coordinateSystem ?: LAYOUT_SRID,
                ExtTrackKilometerIntervalV1(trackKmStart, trackKmEnd),
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }
}
