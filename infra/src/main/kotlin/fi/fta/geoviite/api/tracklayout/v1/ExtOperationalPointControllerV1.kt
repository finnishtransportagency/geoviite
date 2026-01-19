package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
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

private const val EXT_OPERATIONAL_POINTS_TAG_V1 = "Toiminnalliset pisteet"

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController(
    [
        "/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/paikannuspohja/v1",
        "$EXT_TRACK_LAYOUT_BASE_PATH/dev/paikannuspohja/v1",
    ]
)
class ExtOperationalPointControllerV1(private val extOperationalPointService: ExtOperationalPointServiceV1) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/toiminnalliset-pisteet")
    @Tag(name = EXT_OPERATIONAL_POINTS_TAG_V1)
    @Operation(summary = "Toiminnallisten pisteiden kokoelman haku")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Toiminnallisten pisteiden kokoelman haku onnistui."),
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
    fun getExtOperationalPointCollection(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION)
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        layoutVersion: ExtLayoutVersionV1?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1?,
    ): ExtOperationalPointCollectionResponseV1 =
        extOperationalPointService.getExtOperationalPointCollection(layoutVersion, extCoordinateSystem)

    @GetMapping("/toiminnalliset-pisteet/muutokset")
    @Tag(name = EXT_OPERATIONAL_POINTS_TAG_V1)
    @Operation(summary = "Toiminnallisten pisteiden kokoelman muutosten haku")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description =
                        "Toiminnallisten pisteiden kokoelman muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
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
    fun getExtOperationalPointCollectionModifications(
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM)
        @RequestParam(TRACK_LAYOUT_VERSION_FROM, required = true)
        layoutVersionFrom: ExtLayoutVersionV1,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO)
        @RequestParam(TRACK_LAYOUT_VERSION_TO, required = false)
        layoutVersionTo: ExtLayoutVersionV1?,
        @Parameter(name = COORDINATE_SYSTEM, description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1?,
    ): ResponseEntity<ExtModifiedOperationalPointCollectionResponseV1> =
        extOperationalPointService
            .getExtOperationalPointCollectionModifications(layoutVersionFrom, layoutVersionTo, extCoordinateSystem)
            .let(::toResponse)

    @GetMapping("/toiminnalliset-pisteet/{$OPERATIONAL_POINT_OID_PARAM}")
    @Tag(name = EXT_OPERATIONAL_POINTS_TAG_V1)
    @Operation(summary = "Yksittäisen toiminnallisen pisteen haku OID-tunnuksella")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Toiminnallisen pisteen haku onnistui."),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Toiminnallisen pisteen OID-tunnus löytyi, mutta se ei ole olemassa annetussa rataverkon versiossa.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_OPERATIONAL_POINT_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtOperationalPoint(
        @Parameter(description = EXT_OPENAPI_OPERATIONAL_POINT_OID_DESCRIPTION)
        @PathVariable(OPERATIONAL_POINT_OID_PARAM)
        oid: ExtOidV1<OperationalPoint>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION)
        @RequestParam(TRACK_LAYOUT_VERSION, required = false)
        layoutVersion: ExtLayoutVersionV1?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1?,
    ): ResponseEntity<ExtOperationalPointResponseV1> =
        extOperationalPointService.getExtOperationalPoint(oid, layoutVersion, extCoordinateSystem).let(::toResponse)

    @GetMapping("/toiminnalliset-pisteet/{$OPERATIONAL_POINT_OID_PARAM}/muutokset")
    @Tag(name = EXT_OPERATIONAL_POINTS_TAG_V1)
    @Operation(
        summary = "Yksittäisen toiminnallisen pisteen muutosten haku OID-tunnuksella",
        description =
            """
                Esimerkkejä HTTP-paluukoodien arvoista tietyissä tilanteissa:
                
                - 200, kun toiminnallinen piste on luotu annetun rataverkon version ja uusimman rataverkon välillä (null -> Toiminnallisen pisteen versio A).
                - 200, kun toiminnallinen piste muuttuu rataverkon versioiden välillä, joissa kummassakin se on olemassa (Toiminnallisen pisteen versio A -> Toiminnallisen pisteen versio B).
                - 204, kun kysytään muutoksia rataverkon versioiden välillä, joissa kummassakaan haettua toiminnallista pistettä ei ole olemassa (null -> null).
                - 204, kun muutoksia toiminnalliseen pisteeseen ei ole tapahtunut rataverkon versioiden välillä (Toiminnallisen pisteen versio A -> Toiminnallisen pisteen versio A).
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description =
                        "Toiminnallisen pisteen muutokset haettiin onnistuneesti kahden rataverkon version välillä.",
                ),
                ApiResponse(
                    responseCode = "204",
                    description =
                        "Toiminnallisen pisteen OID-tunnus löytyi, mutta muutoksia vertailtavien versioiden välillä ei ole.",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = EXT_OPENAPI_INVALID_ARGUMENTS,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = EXT_OPENAPI_OPERATIONAL_POINT_OR_TRACK_LAYOUT_VERSION_NOT_FOUND,
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = EXT_OPENAPI_SERVER_ERROR,
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ]
    )
    fun getExtOperationalPointModifications(
        @Parameter(description = EXT_OPENAPI_OPERATIONAL_POINT_OID_DESCRIPTION)
        @PathVariable(OPERATIONAL_POINT_OID_PARAM)
        oid: ExtOidV1<OperationalPoint>,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM)
        @RequestParam(TRACK_LAYOUT_VERSION_FROM, required = true)
        layoutVersionFrom: ExtLayoutVersionV1,
        @Parameter(description = EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO)
        @RequestParam(TRACK_LAYOUT_VERSION_TO, required = false)
        layoutVersionTo: ExtLayoutVersionV1?,
        @Parameter(description = EXT_OPENAPI_COORDINATE_SYSTEM)
        @RequestParam(COORDINATE_SYSTEM, required = false)
        extCoordinateSystem: ExtSridV1?,
    ): ResponseEntity<ExtModifiedOperationalPointResponseV1> =
        extOperationalPointService
            .getExtOperationalPointModifications(oid, layoutVersionFrom, layoutVersionTo, extCoordinateSystem)
            .let(::toResponse)
}
