package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
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
import org.springframework.web.bind.annotation.RequestParam

private const val EXT_TRACK_NUMBER_TAG_V1 = "Ratanumero"
private const val EXT_TRACK_NUMBER_COLLECTION_TAG_V1 = "Ratanumerokokoelma"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    ["$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1", "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1"]
)
class ExtTrackNumberControllerV1
@Autowired
constructor(private val extTrackNumberCollectionService: ExtTrackNumberCollectionServiceV1) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/ratanumerot")
    @Tag(name = EXT_TRACK_NUMBER_COLLECTION_TAG_V1)
    @Operation(summary = "Ratanumerokokoelman haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Ratanumerokokoelman haku onnistui."),
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
    fun extGetTrackNumberCollection(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false)
        coordinateSystem: Srid?,
    ): ExtTrackNumberCollectionResponseV1 {
        return extTrackNumberCollectionService.createTrackNumberCollectionResponse(
            trackLayoutVersion = trackLayoutVersion,
            coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
        )
    }

    @GetMapping("/ratanumerot/muutokset", params = [MODIFICATIONS_FROM_VERSION])
    @Tag(name = EXT_TRACK_NUMBER_COLLECTION_TAG_V1)
    @Operation(summary = "Ratanumerokokoelman muutosten haku")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description =
                        "Ratanumerokokoelman muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
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
                    description = "Yhtä tai useampaa rataverkon versiota ei ole olemassa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun extGetTrackNumberCollectionModifications(
        @Parameter(
            description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM,
            schema = Schema(type = "string", format = "uuid"),
        )
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true)
        modificationsFromVersion: Uuid<Publication>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(
            name = COORDINATE_SYSTEM_PARAM,
            description = EXT_OPENAPI_COORDINATE_SYSTEM,
            schema = Schema(type = "string", format = "string"),
        )
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedTrackNumberCollectionResponseV1> {
        return extTrackNumberCollectionService
            .createTrackNumberCollectionModificationResponse(
                modificationsFromVersion = modificationsFromVersion,
                trackLayoutVersion = trackLayoutVersion,
                coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
            )
            ?.let { modifiedResponse -> ResponseEntity.ok(modifiedResponse) } ?: ResponseEntity.noContent().build()
    }
}
