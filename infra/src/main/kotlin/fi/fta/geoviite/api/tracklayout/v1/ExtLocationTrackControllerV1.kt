package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.frameconverter.v1.LOCATION_TRACK_OID_PARAM
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
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

private const val EXT_LOCATION_TRACKS_TAG_V1 = "Sijaintiraiteet"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtLocationTrackControllerV1(private val extLocationTrackService: ExtLocationTrackServiceV1) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/sijaintiraiteet")
    @Tag(name = EXT_LOCATION_TRACKS_TAG_V1)
    @Operation(summary = "Sijaintiraidekokoelman haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Sijaintiraidekokoelman haku onnistui."),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtLocationTrackCollection(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ExtLocationTrackCollectionResponseV1 =
        extLocationTrackService.getExtLocationTrackCollection(trackLayoutVersion, coordinateSystem)

    @GetMapping("/sijaintiraiteet/muutokset")
    @Tag(name = EXT_LOCATION_TRACKS_TAG_V1)
    @Operation(summary = "Sijaintiraidekokoelman muutosten haku")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description =
                        "Sijaintiraidekokoelman muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
                ),
                ApiResponse(
                    responseCode = "204",
                    description = EXT_OPENAPI_NO_MODIFICATIONS_BETWEEN_VERSIONS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_ONE_OR_MORE_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtLocationTrackCollectionModifications(
        @Parameter(
            description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM,
            schema = Schema(type = "string", format = "uuid"),
        )
        @RequestParam(TRACK_LAYOUT_VERSION_FROM, required = true)
        trackLayoutVersionFrom: Uuid<Publication>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION_TO, required = false)
        trackLayoutVersionTo: Uuid<Publication>?,
        @Parameter(
            name = COORDINATE_SYSTEM,
            description = EXT_OPENAPI_COORDINATE_SYSTEM,
            schema = Schema(type = "string", format = "string"),
        )
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedLocationTrackCollectionResponseV1> =
        extLocationTrackService
            .getExtLocationTrackCollectionModifications(trackLayoutVersionFrom, trackLayoutVersionTo, coordinateSystem)
            .let(::toResponse)

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}")
    @Tag(name = EXT_LOCATION_TRACKS_TAG_V1)
    @Operation(summary = "Yksittäisen sijaintiraiteen haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Sijaintiraiteen haku onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Sijaintiraiteen OID-tunnus löytyi, mutta se ei ole olemassa annetussa rataverkon versiossa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_LOCATION_TRACK_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtLocationTrack(
        @Parameter(description = EXT_OPENAPI_LOCATION_TRACK_OID_DESCRIPTION)
        @PathVariable(LOCATION_TRACK_OID_PARAM)
        oid: Oid<LocationTrack>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtLocationTrackResponseV1> =
        extLocationTrackService.getExtLocationTrack(oid, trackLayoutVersion, coordinateSystem).let(::toResponse)

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/muutokset")
    @Tag(name = EXT_LOCATION_TRACKS_TAG_V1)
    @Operation(
        summary = "Yksittäisen sijaintiraiteen muutosten haku OID-tunnuksella",
        description =
            """
                Esimerkkejä HTTP-paluukoodien arvoista tietyissä tilanteissa:
                
                - 200, kun sijaintiraide on luotu annetun rataverkon version ja uusimman rataverkon välillä (null -> Raiteen versio A).
                - 200, kun sijaintiraide muuttuu rataverkon versioiden välillä, joissa kummassakin se on olemassa (Raiteen versio A -> Raiteen versio B).
                - 204, kun kysytään muutoksia rataverkon versioiden välillä, joissa kummassakaan haettua sijaintiraidetta ei ole olemassa (null -> null).
                - 204, kun muutoksia sijaintiraiteeseen ei ole tapahtunut rataverkon versioiden välillä (Raiteen versio A -> Raiteen versio A).
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Sijaintiraiteen muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
                ),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Sijaintiraiteen OID-tunnus löytyi, mutta muutoksia vertailtavien versioiden välillä ei ole.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_LOCATION_TRACK_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtLocationTrackModifications(
        @Parameter(description = EXT_OPENAPI_LOCATION_TRACK_OID_DESCRIPTION)
        @PathVariable(LOCATION_TRACK_OID_PARAM)
        oid: Oid<LocationTrack>,
        @Parameter(
            description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM,
            schema = Schema(type = "string", format = "uuid"),
        )
        @RequestParam(TRACK_LAYOUT_VERSION_FROM, required = true)
        trackLayoutVersionFrom: Uuid<Publication>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION_TO, required = false)
        trackLayoutVersionTo: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedLocationTrackResponseV1> =
        extLocationTrackService
            .getExtLocationTrackModifications(oid, trackLayoutVersionFrom, trackLayoutVersionTo, coordinateSystem)
            .let(::toResponse)

    @GetMapping("/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria")
    @Tag(name = EXT_LOCATION_TRACKS_TAG_V1)
    @Operation(summary = "Yksittäisen sijaintiraiteen geometrian haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Sijaintiraiteen geometrian haku onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Sijaintiraiteen OID-tunnus löytyi, mutta sille ei ole olemassa geometriaa annetussa rataverkon versiossa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_LOCATION_TRACK_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtLocationTrackGeometry(
        @Parameter(description = EXT_OPENAPI_LOCATION_TRACK_OID_DESCRIPTION)
        @PathVariable(LOCATION_TRACK_OID_PARAM)
        oid: Oid<LocationTrack>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>? = null,
        @Parameter(description = EXT_OPENAPI_RESOLUTION)
        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false)
        extResolution: ExtResolutionV1? = null,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid? = null,
        @Parameter(description = EXT_OPENAPI_ADDRESS_POINT_FILTER_START)
        @RequestParam(ADDRESS_POINT_FILTER_START, required = false)
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1? = null,
        @Parameter(description = EXT_OPENAPI_ADDRESS_POINT_FILTER_END)
        @RequestParam(ADDRESS_POINT_FILTER_END, required = false)
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1? = null,
    ): ResponseEntity<ExtLocationTrackGeometryResponseV1> =
        extLocationTrackService
            .getExtLocationTrackGeometry(
                oid,
                trackLayoutVersion,
                extResolution,
                coordinateSystem,
                addressFilterStart,
                addressFilterEnd,
            )
            .let(::toResponse)
}
