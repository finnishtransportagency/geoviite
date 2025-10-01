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
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
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

private const val EXT_KM_POST_TAG_V1 = "Tasakilometripiste"
private const val EXT_KM_POST_COLLECTION_TAG_V1 = "Tasakilometripistekokoelma"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    ["$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1", "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1"]
)
class ExtKmPostControllerV1
@Autowired
constructor(private val extKmPostService: ExtKmPostServiceV1, private val publicationService: PublicationService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/tasakilometripisteet")
    @Tag(name = EXT_KM_POST_COLLECTION_TAG_V1)
    @Operation(summary = "Tasakilometripistekokoelman haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Tasakilometripistekokoelman haku onnistui."),
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
    fun extGetKmPostCollection(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ExtKmPostCollectionResponseV1 {
        return publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion).let {
            publication ->
            extKmPostService.createKmPostCollectionResponse(publication, coordinateSystem ?: LAYOUT_SRID)
        }
    }

    @GetMapping("/tasakilometripisteet/muutokset")
    @Tag(name = EXT_KM_POST_COLLECTION_TAG_V1)
    @Operation(summary = "Tasakilometripistekokoelman muutosten haku")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description =
                        "Tasakilometripistekokoelman muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
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
    fun extGetKmPostCollectionModifications(
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
    ): ResponseEntity<ExtModifiedKmPostCollectionResponseV1> {
        return publicationService
            .getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
            .let { publications ->
                if (publications.areDifferent()) {
                    extKmPostService.createKmPostCollectionModificationResponse(
                        publications,
                        coordinateSystem ?: LAYOUT_SRID,
                    )
                } else {
                    publicationsAreTheSame(trackLayoutVersionFrom)
                }
            }
            .let(::toResponse)
    }

    @GetMapping("/tasakilometripisteet/{${KM_POST_OID}}")
    @Tag(name = EXT_KM_POST_TAG_V1)
    @Operation(summary = "Yksittäisen ratanumeron haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Ratanumeron haku onnistui."),
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
                    description = EXT_OPENAPI_KM_POST_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun extGetKmPost(
        @Parameter(description = EXT_OPENAPI_KM_POST_OID_DESCRIPTION) @PathVariable(KM_POST_OID) oid: Oid<LayoutKmPost>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtKmPostResponseV1> {
        return publicationService
            .getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
            .let { publication ->
                extKmPostService.createKmPostResponse(oid, publication, coordinateSystem ?: LAYOUT_SRID)
            }
            .let(::toResponse)
    }

    @GetMapping("/tasakilometripisteet/{${KM_POST_OID}}/muutokset")
    @Tag(name = EXT_KM_POST_TAG_V1)
    @Operation(
        summary = "Yksittäisen ratanumeron muutosten haku OID-tunnuksella",
        description =
            """
                Esimerkkejä HTTP-paluukoodien arvoista tietyissä tilanteissa:
                
                - 200, kun ratanumero on luotu annetun rataverkon version ja uusimman rataverkon välillä (null -> Ratanumeron versio A).
                - 200, kun ratanumero muuttuu rataverkon versioiden välillä, joissa kummassakin se on olemassa (Ratanumeron versio A -> Ratanumeron versio B).
                - 204, kun kysytään muutoksia rataverkon versioiden välillä, joissa kummassakaan haettua ratanumeroa ei ole olemassa (null -> null).
                - 204, kun muutoksia ratanumeroon ei ole tapahtunut rataverkon versioiden välillä (Ratanumeron versio A -> Ratanumeron versio A).
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Ratanumeron muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
                ),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Ratanumeron OID-tunnus löytyi, mutta muutoksia vertailtavien versioiden välillä ei ole.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_KM_POST_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun extGetKmPostModifications(
        @Parameter(description = EXT_OPENAPI_KM_POST_OID_DESCRIPTION) @PathVariable(KM_POST_OID) oid: Oid<LayoutKmPost>,
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
    ): ResponseEntity<ExtModifiedKmPostResponseV1> {
        return publicationService
            .getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
            .let { publications ->
                if (publications.areDifferent()) {
                    extKmPostService.createKmPostModificationResponse(
                        oid,
                        publications,
                        coordinateSystem ?: LAYOUT_SRID,
                    )
                } else {
                    publicationsAreTheSame(trackLayoutVersionFrom)
                }
            }
            .let(::toResponse)
    }
}
