package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtTrackNumberKmControllerV1
@Autowired
constructor(private val extTrackNumberKmsService: ExtTrackNumberKmServiceV1) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/ratanumerot/ratakilometrit")
    @Tag(name = EXT_TRACK_NUMBERS_TAG_V1)
    @Operation(summary = "Ratanumerokokoelman ratakilometrien haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Ratanumerokokoelman ratakilometrien haku onnistui."),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "Annettua rataverkon versiota ei ole olemassa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtTrackNumberKmsCollection(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION)
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        layoutVersion: ExtLayoutVersionV1?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1?,
    ): ExtTrackKmsCollectionResponseV1 =
        extTrackNumberKmsService.getExtTrackNumberKmsCollection(layoutVersion, extCoordinateSystem)

    @GetMapping("/ratanumerot/{${TRACK_NUMBER_OID}}/ratakilometrit")
    @Tag(name = EXT_TRACK_NUMBERS_TAG_V1)
    @Operation(summary = "Yksittäisen ratanumeron ratakilometrien haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Ratanumeron ratakilometrien haku onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Ratanumeron OID-tunnus löytyi, mutta se ei ole olemassa annetussa rataverkon versiossa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_TRACK_NUMBER_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtTrackNumberKms(
        @Parameter(description = EXT_OPENAPI_TRACK_NUMBER_OID_DESCRIPTION)
        @PathVariable(TRACK_NUMBER_OID)
        oid: ExtOidV1<LayoutTrackNumber>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION)
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        layoutVersion: ExtLayoutVersionV1?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1?,
    ): ResponseEntity<ExtTrackKmsResponseV1> =
        extTrackNumberKmsService.getExtTrackNumberKms(oid, layoutVersion, extCoordinateSystem).let(::toResponse)
}
