package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.util.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

const val EXT_ROUTING_TAG_V1 = "Reititys"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtRoutingControllerV1(private val extRoutingService: ExtRoutingServiceV1) {

    @GetMapping("/reititys")
    @Tag(name = EXT_ROUTING_TAG_V1)
    @Operation(summary = "Reitti kahden koordinaattisijainnin välillä")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Reititys onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description = "Annettujen sijaintien välille ei löydy reittiä.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
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
    fun getExtRoute(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION)
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        layoutVersion: ExtLayoutVersionV1?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1?,
        @Parameter(description = "Reitin alkusijainnin x-koordinaatti") @RequestParam(START_X) startX: Double,
        @Parameter(description = "Reitin alkusijainnin y-koordinaatti") @RequestParam(START_Y) startY: Double,
        @Parameter(description = "Reitin loppusijainnin x-koordinaatti") @RequestParam(END_X) endX: Double,
        @Parameter(description = "Reitin loppusijainnin y-koordinaatti") @RequestParam(END_Y) endY: Double,
    ): ResponseEntity<ExtRouteResponseV1> =
        extRoutingService.getExtRoute(layoutVersion, extCoordinateSystem, startX, startY, endX, endY).let(::toResponse)
}
