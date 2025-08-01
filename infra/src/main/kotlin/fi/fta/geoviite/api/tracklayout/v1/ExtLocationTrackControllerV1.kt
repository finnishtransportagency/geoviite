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
import fi.fta.geoviite.infra.util.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

const val EXT_TRACK_LAYOUT_BASE_PATH = "/geoviite"

const val EXT_LOCATION_TRACK_TAG_V1 = "Sijaintiraide"
const val EXT_LOCATION_TRACK_COLLECTION_TAG_V1 = "Sijaintiraidekokoelma"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    ["$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1", "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1"]
)
class ExtLocationTrackControllerV1(
    private val extLocationTrackService: ExtLocationTrackServiceV1,
    private val extLocationTrackGeometryService: ExtLocationTrackGeometryServiceV1,
    private val extLocationTrackCollectionService: ExtLocationTrackCollectionServiceV1,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/sijaintiraiteet")
    @Tag(name = EXT_LOCATION_TRACK_COLLECTION_TAG_V1)
    fun extGetLocationTrackCollection(
        @RequestParam(TRACK_LAYOUT_VERSION, required = false) trackLayoutVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ExtLocationTrackCollectionResponseV1 {
        return extLocationTrackCollectionService.createLocationTrackCollectionResponse(
            trackLayoutVersion = trackLayoutVersion,
            coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
        )
    }

    @GetMapping("/sijaintiraiteet/muutokset", params = [MODIFICATIONS_FROM_VERSION])
    @Tag(name = EXT_LOCATION_TRACK_COLLECTION_TAG_V1)
    fun extGetLocationTrackCollectionModifications(
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
        @RequestParam(TRACK_LAYOUT_VERSION, required = false) trackLayoutVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedLocationTrackCollectionResponseV1> {
        return extLocationTrackCollectionService
            .createLocationTrackCollectionModificationResponse(
                modificationsFromVersion = modificationsFromVersion,
                trackLayoutVersion = trackLayoutVersion,
                coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}")
    @Tag(name = EXT_LOCATION_TRACK_TAG_V1)
    @Operation(summary = "Yksittäisen sijaintiraiteen haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Sijaintiraide löytyi onnistuneesti uusimmasta tai annetusta rataverkon versiosta.",
                ),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Sijaintiraiteen OID-tunnus löytyi, muttei se ollut olemassa annetussa rataverkon versiossa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description =
                        "Sijaintiraidetta ei löytynyt annetulla OID-tunnuksella tai annettua rataverkon versiota ei ollut olemassa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun extGetLocationTrack(
        @Parameter(description = LOCATION_TRACK_OID_DESCRIPTION)
        @PathVariable(LOCATION_TRACK_OID_PARAM)
        oid: Oid<LocationTrack>,
        @RequestParam(TRACK_LAYOUT_VERSION, required = false) trackLayoutVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ResponseEntity<ExtLocationTrackResponseV1> {
        return extLocationTrackService
            .createLocationTrackResponse(oid, trackLayoutVersion, coordinateSystem ?: LAYOUT_SRID)
            .let(::toResponse)
    }

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/muutokset", params = [MODIFICATIONS_FROM_VERSION])
    @Tag(name = EXT_LOCATION_TRACK_TAG_V1)
    fun extGetLocationTrackModifications(
        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: Oid<LocationTrack>,
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true) modificationsFromVersion: Uuid<Publication>,
        @RequestParam(TRACK_LAYOUT_VERSION, required = false) trackLayoutVersion: Uuid<Publication>?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedLocationTrackResponseV1> {
        return extLocationTrackService
            .createLocationTrackModificationResponse(
                locationTrackOid,
                modificationsFromVersion,
                trackLayoutVersion,
                coordinateSystem ?: LAYOUT_SRID,
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria")
    @Tag(name = EXT_LOCATION_TRACK_TAG_V1)
    fun extGetLocationTrackGeometry(
        @PathVariable(LOCATION_TRACK_OID_PARAM) oid: Oid<LocationTrack>,
        @RequestParam(TRACK_LAYOUT_VERSION, required = false) trackLayoutVersion: Uuid<Publication>? = null,
        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false) extResolution: ExtResolutionV1? = null,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: Srid? = null,
        @RequestParam(TRACK_KILOMETER_START_PARAM, required = false) trackKmStart: KmNumber? = null,
        @RequestParam(TRACK_KILOMETER_END_PARAM, required = false) trackKmEnd: KmNumber? = null,
    ): ExtLocationTrackGeometryResponseV1 {
        return extLocationTrackGeometryService.createGeometryResponse(
            oid,
            trackLayoutVersion,
            extResolution?.toResolution() ?: Resolution.ONE_METER,
            coordinateSystem ?: LAYOUT_SRID,
            ExtTrackKilometerIntervalV1(trackKmStart, trackKmEnd),
        )
    }
}
