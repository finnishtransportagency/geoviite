package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
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

const val EXT_TRACK_LAYOUT_VERSIONS_TAG_V1 = "Rataverkon versiot"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtTrackLayoutVersionControllerV1 @Autowired constructor(private val publicationService: PublicationService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/versiot/{${TRACK_LAYOUT_VERSION}}")
    @Tag(name = EXT_TRACK_LAYOUT_VERSIONS_TAG_V1)
    @Operation(summary = "Rataverkon version haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Rataverkon version haku onnistui."),
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
    fun extGetTrackLayoutVersion(
        @Parameter(description = EXT_OPENAPI_TRACK_NUMBER_OID_DESCRIPTION)
        @PathVariable(TRACK_LAYOUT_VERSION)
        version: Uuid<Publication>
    ): ExtTrackLayoutVersionV1 {
        return publicationService.getPublicationWithType(LayoutBranchType.MAIN, version).let(::ExtTrackLayoutVersionV1)
    }

    @GetMapping("/versiot/uusin")
    @Tag(name = EXT_TRACK_LAYOUT_VERSIONS_TAG_V1)
    @Operation(summary = "Rataverkon uusimman version haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Rataverkon uusimman version haku onnistui."),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun extGetLatestTrackLayoutVersion(): ExtTrackLayoutVersionV1 {
        return publicationService.getLatestPublication(LayoutBranchType.MAIN).let(::ExtTrackLayoutVersionV1)
    }

    @GetMapping("/versiot")
    @Tag(name = EXT_TRACK_LAYOUT_VERSIONS_TAG_V1)
    @Operation(summary = "Rataverkon versiokokoelman haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Rataverkon versiokokoelman haku onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description = "Rataverkon versioita ei ole saatavilla.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun extGetTrackLayoutVersionCollection(): ResponseEntity<ExtTrackLayoutVersionCollectionResponseV1> {
        return publicationService
            .listPublications(LayoutBranchType.MAIN)
            .takeIf { publications -> publications.isNotEmpty() }
            ?.let { publications ->
                ExtTrackLayoutVersionCollectionResponseV1(
                    trackLayoutVersionFrom = publications.first().uuid,
                    trackLayoutVersionTo = publications.last().uuid,
                    versions = publications.map(::ExtTrackLayoutVersionV1),
                )
            }
            .let(::toResponse)
    }

    @GetMapping("/versiot/muutokset")
    @Tag(name = EXT_TRACK_LAYOUT_VERSIONS_TAG_V1)
    @Operation(summary = "Rataverkon versiokokoelman muutosten haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Rataverkon versiokokoelman muutosten haku onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description = "Muutoksia vertailtavien versioiden välillä ei ole.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun extGetModifiedTrackLayoutVersionCollection(
        @Parameter(
            description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM,
            schema = Schema(type = "string", format = "uuid"),
        )
        @RequestParam(TRACK_LAYOUT_VERSION_FROM, required = true)
        trackLayoutVersionFrom: Uuid<Publication>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION_TO, required = false)
        trackLayoutVersionTo: Uuid<Publication>?,
    ): ResponseEntity<ExtTrackLayoutVersionCollectionResponseV1> {
        return publicationService
            .getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
            .takeIf { versions -> versions.areDifferent() }
            ?.let { versions -> publicationService.listPublications(LayoutBranchType.MAIN, versions) }
            ?.takeIf { publications -> publications.size > 1 }
            ?.let { publications ->
                ExtTrackLayoutVersionCollectionResponseV1(
                    trackLayoutVersionFrom = publications.first().uuid,
                    trackLayoutVersionTo = publications.last().uuid,
                    versions = publications.drop(1).map(::ExtTrackLayoutVersionV1),
                )
            }
            .let(::toResponse)
    }
}
