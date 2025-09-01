package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.Resolution
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

private const val EXT_TRACK_NUMBER_TAG_V1 = "Ratanumero"
private const val EXT_TRACK_NUMBER_COLLECTION_TAG_V1 = "Ratanumerokokoelma"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    ["$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1", "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1"]
)
class ExtTrackNumberControllerV1
@Autowired
constructor(
    private val extTrackNumberService: ExtTrackNumberServiceV1,
    private val extTrackNumberGeometryService: ExtTrackNumberGeometryServiceV1,
    private val extTrackNumberCollectionService: ExtTrackNumberCollectionServiceV1,
    private val publicationService: PublicationService,
) {
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
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ExtTrackNumberCollectionResponseV1 {
        return publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion).let {
            publication ->
            extTrackNumberCollectionService.createTrackNumberCollectionResponse(
                publication,
                coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
            )
        }
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
            name = COORDINATE_SYSTEM,
            description = EXT_OPENAPI_COORDINATE_SYSTEM,
            schema = Schema(type = "string", format = "string"),
        )
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedTrackNumberCollectionResponseV1> {
        return toResponse(
            publicationService
                .getPublicationsToCompare(modificationsFromVersion, trackLayoutVersion)
                .takeIf { publications -> publications.areDifferent() }
                ?.let { publications ->
                    extTrackNumberCollectionService.createTrackNumberCollectionModificationResponse(
                        publications,
                        coordinateSystem ?: LAYOUT_SRID,
                    )
                } ?: publicationsAreTheSame(modificationsFromVersion)
        )
    }

    @GetMapping("/ratanumerot/{${TRACK_NUMBER_OID}}")
    @Tag(name = EXT_TRACK_NUMBER_TAG_V1)
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
    fun extGetTrackNumber(
        @Parameter(description = EXT_OPENAPI_TRACK_NUMBER_OID_DESCRIPTION)
        @PathVariable(TRACK_NUMBER_OID)
        oid: Oid<LayoutTrackNumber>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtTrackNumberResponseV1> {
        return publicationService
            .getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
            .let { publication ->
                extTrackNumberService.createTrackNumberResponse(oid, publication, coordinateSystem ?: LAYOUT_SRID)
            }
            .let(::toResponse)
    }

    @GetMapping("/ratanumerot/{${TRACK_NUMBER_OID}}/muutokset", params = [MODIFICATIONS_FROM_VERSION])
    @Tag(name = EXT_TRACK_NUMBER_TAG_V1)
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
    fun extGetTrackNumberModifications(
        @Parameter(description = EXT_OPENAPI_TRACK_NUMBER_OID_DESCRIPTION)
        @PathVariable(TRACK_NUMBER_OID)
        trackNumberOid: Oid<LayoutTrackNumber>,
        @Parameter(
            description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM,
            schema = Schema(type = "string", format = "uuid"),
        )
        @RequestParam(MODIFICATIONS_FROM_VERSION, required = true)
        modificationsFromVersion: Uuid<Publication>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid?,
    ): ResponseEntity<ExtModifiedTrackNumberResponseV1> {
        return toResponse(
            publicationService
                .getPublicationsToCompare(modificationsFromVersion, trackLayoutVersion)
                .takeIf { publications -> publications.areDifferent() }
                ?.let { publications ->
                    extTrackNumberService.createTrackNumberModificationResponse(
                        trackNumberOid,
                        publications,
                        coordinateSystem ?: LAYOUT_SRID,
                    )
                } ?: publicationsAreTheSame(modificationsFromVersion)
        )
    }

    @GetMapping("/ratanumerot/{${TRACK_NUMBER_OID}}/geometria")
    @Tag(name = EXT_TRACK_NUMBER_TAG_V1)
    @Operation(summary = "Yksittäisen ratanumeron geometrian haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Ratanumeron geometrian haku onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Ratanumeron OID-tunnus löytyi, mutta sille ei ole olemassa geometriaa annetussa rataverkon versiossa.",
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
    fun extGetTrackNumberGeometry(
        @Parameter(description = EXT_OPENAPI_TRACK_NUMBER_OID_DESCRIPTION)
        @PathVariable(TRACK_NUMBER_OID)
        oid: Oid<LayoutTrackNumber>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION, schema = Schema(type = "string", format = "uuid"))
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        trackLayoutVersion: Uuid<Publication>? = null,
        @Parameter(description = EXT_OPENAPI_RESOLUTION)
        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false)
        extResolution: ExtResolutionV1? = null,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM, schema = Schema(type = "string", format = "string"))
        @RequestParam(COORDINATE_SYSTEM, required = false)
        coordinateSystem: Srid? = null,
        @Parameter(description = EXT_OPENAPI_REFERENCE_LINE_KILOMETER_START)
        @RequestParam(TRACK_KILOMETER_START, required = false)
        trackKmStart: KmNumber? = null,
        @Parameter(description = EXT_OPENAPI_REFERENCE_LINE_KILOMETER_END)
        @RequestParam(TRACK_KILOMETER_END, required = false)
        trackKmEnd: KmNumber? = null,
    ): ResponseEntity<ExtTrackNumberGeometryResponseV1> {
        return publicationService
            .getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
            .let { publication ->
                extTrackNumberGeometryService.createGeometryResponse(
                    oid,
                    publication,
                    extResolution?.toResolution() ?: Resolution.ONE_METER,
                    coordinateSystem ?: LAYOUT_SRID,
                    ExtTrackKilometerIntervalV1(trackKmStart, trackKmEnd),
                )
            }
            .let(::toResponse)
    }
}
