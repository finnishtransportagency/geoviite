package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

const val EXT_TOPOLOGY_TAG_V1 = "Topologia"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtTopologyControllerV1(private val extTopologyService: ExtTopologyServiceV1) {

    @GetMapping("/topologia")
    @Tag(name = EXT_TOPOLOGY_TAG_V1)
    @Operation(summary = "Rataverkon topologian haku suuntaamattomana verkkona")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Topologian haku onnistui."),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_TOPOLOGY_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtTopology(
        @Parameter(description = EXT_OPENAPI_TOPOLOGY_TRACK_LAYOUT_VERSION)
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        layoutVersion: ExtLayoutVersionV1? = null,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1? = null,
        @Parameter(description = EXT_OPENAPI_TOPOLOGY_RESOLUTION)
        @RequestParam(TOPOLOGY_RESOLUTION_PARAM, required = false)
        extResolution: ExtTopologyResolutionV1? = null,
    ): ExtTopologyResponseV1 = extTopologyService.getExtTopology(layoutVersion, extCoordinateSystem, extResolution)
}
