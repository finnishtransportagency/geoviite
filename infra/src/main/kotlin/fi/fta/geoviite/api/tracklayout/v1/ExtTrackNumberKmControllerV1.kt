package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
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

private const val EXT_TRACK_NUMBER_KMS_TAG_V1 = "Ratanumeron tasakilometripisteet"
private const val EXT_TRACK_NUMBER_KMS_COLLECTION_TAG_V1 = "Ratanumerokokoelman tasakilometripisteet"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    ["$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1", "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1"]
)
class ExtKmPostControllerV1
@Autowired
constructor(
    private val extKmPostService: ExtTrackNumberKmServiceV1,
    private val publicationService: PublicationService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/ratakilometrit")
    @Tag(name = EXT_TRACK_NUMBER_KMS_COLLECTION_TAG_V1)
    @Operation(summary = "Kaikkien ratanumeroiden ratakilometrien haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Ratakilometrikokoelman haku onnistui."),
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
    fun extGetTrackNumberKmsCollection(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ExtTrackKmsCollectionResponseV1 {
        return publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion).let {
            publication ->
            extKmPostService.createTrackNumberKmsCollectionResponse(publication, coordinateSystem ?: LAYOUT_SRID)
        }
    }

    @GetMapping("/ratanumerot/{${TRACK_NUMBER_OID}}/ratakilometrit")
    @Tag(name = EXT_TRACK_NUMBER_KMS_TAG_V1)
    @Operation(summary = "Yksittäisen ratanumeron kilometrien haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Ratanumeron kilometrien haku onnistui."),
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
    fun extGetTrackNumberKms(
        @Parameter(description = EXT_OPENAPI_TRACK_NUMBER_OID_DESCRIPTION)
        @PathVariable(TRACK_NUMBER_OID)
        oid: Oid<LayoutTrackNumber>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtTrackKmsResponseV1> {
        return publicationService
            .getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
            .let { publication ->
                extKmPostService.createTrackKmResponse(oid, publication, coordinateSystem ?: LAYOUT_SRID)
            }
            .let(::toResponse)
    }
}
