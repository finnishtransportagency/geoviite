package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
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

const val EXT_DESIGNS_TAG_V1 = "Suunnitelmat"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtDesignControllerV1(private val extDesignService: ExtDesignServiceV1) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/suunnitelmat")
    @Tag(name = EXT_DESIGNS_TAG_V1)
    @Operation(summary = "Suunnitelmakokoelman haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Suunnitelmakokoelman haku onnistui."),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtDesignCollection(): ExtDesignCollectionResponseV1 = extDesignService.getExtDesignCollection()

    @GetMapping("/suunnitelmat/{${DESIGN_OID}}")
    @Tag(name = EXT_DESIGNS_TAG_V1)
    @Operation(summary = "Yksittäisen suunnitelman haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Suunnitelman haku onnistui."),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_DESIGN_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtDesign(
        @Parameter(description = EXT_OPENAPI_DESIGN_OID_DESCRIPTION)
        @PathVariable(DESIGN_OID)
        designOid: ExtOidV1<LayoutDesign>
    ): ExtDesignResponseV1 = extDesignService.getExtDesign(designOid)

    @GetMapping("/suunnitelmat/{${DESIGN_OID}}/muutokset")
    @Tag(name = EXT_DESIGNS_TAG_V1)
    @Operation(
        summary = "Yksittäisen suunnitelman muutosten haku OID-tunnuksella",
        description = EXT_OPENAPI_DESIGN_MODIFICATIONS,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Suunnitelman muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
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
                    description = EXT_OPENAPI_ONE_OR_MORE_SEARCH_PARAMETER_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtDesignModifications(
        @Parameter(description = EXT_OPENAPI_DESIGN_OID_DESCRIPTION)
        @PathVariable(DESIGN_OID)
        designOid: ExtOidV1<LayoutDesign>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM)
        @RequestParam(TRACK_LAYOUT_VERSION_FROM, required = true)
        layoutVersionFrom: ExtLayoutVersionV1,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO)
        @RequestParam(TRACK_LAYOUT_VERSION_TO, required = false)
        layoutVersionTo: ExtLayoutVersionV1?,
    ): ResponseEntity<ExtModifiedDesignResponseV1> =
        extDesignService.getExtDesignModifications(designOid, layoutVersionFrom, layoutVersionTo).let(::toResponse)

    @GetMapping("/suunnitelmat/muutokset")
    @Tag(name = EXT_DESIGNS_TAG_V1)
    @Operation(summary = "Suunnitelmakokoelman muutosten haku", description = EXT_OPENAPI_DESIGN_MODIFICATIONS)
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description =
                        "Suunnitelmakokoelman muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
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
    fun getExtDesignCollectionModifications(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM)
        @RequestParam(TRACK_LAYOUT_VERSION_FROM, required = true)
        layoutVersionFrom: ExtLayoutVersionV1,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO)
        @RequestParam(TRACK_LAYOUT_VERSION_TO, required = false)
        layoutVersionTo: ExtLayoutVersionV1?,
    ): ResponseEntity<ExtModifiedDesignCollectionResponseV1> =
        extDesignService.getExtDesignCollectionModifications(layoutVersionFrom, layoutVersionTo).let(::toResponse)
}
