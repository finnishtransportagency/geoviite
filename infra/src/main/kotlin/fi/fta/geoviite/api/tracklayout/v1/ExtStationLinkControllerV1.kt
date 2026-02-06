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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtStationLinkControllerV1(private val extStationLinkService: ExtStationLinkServiceV1) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/liikennepaikkavalit")
    @Tag(name = EXT_OPERATIONAL_POINTS_TAG_V1)
    @Operation(summary = "Liikennepaikkavälin haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Liikennepaikkavälin haku onnistui."),
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
    fun getExtStationLinkCollection(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION)
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        layoutVersion: ExtLayoutVersionV1?
    ): ExtStationLinkCollectionResponseV1 = extStationLinkService.getExtStationLinkCollection(layoutVersion)
}
