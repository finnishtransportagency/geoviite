import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.api.tracklayout.v1.ApiRequestStringV1
import fi.fta.geoviite.api.tracklayout.v1.COORDINATE_SYSTEM_PARAM
import fi.fta.geoviite.api.tracklayout.v1.CenterLineGeometryResponseV1
import fi.fta.geoviite.api.tracklayout.v1.CenterLineGeometryServiceV1
import fi.fta.geoviite.api.tracklayout.v1.LOCATION_TRACK_OID_PARAM
import fi.fta.geoviite.api.tracklayout.v1.LocationTrackRequestV1
import fi.fta.geoviite.api.tracklayout.v1.MODIFICATIONS_FROM_VERSION
import fi.fta.geoviite.api.tracklayout.v1.TRACK_NETWORK_VERSION
import fi.fta.geoviite.api.tracklayout.v1.createErrorResponse
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController([])
class CenterLineGeometryControllerV1(
    private val centerLineGeometryService: CenterLineGeometryServiceV1,
    private val localizationService: LocalizationService,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping(
        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}",
        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/",
    )
    fun singleTrackCenterLineGeometry(
        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: ApiRequestStringV1,
        @RequestParam(TRACK_NETWORK_VERSION) trackNetworkVersion: ApiRequestStringV1?,
        @RequestParam(MODIFICATIONS_FROM_VERSION) modificationsFromVersion: ApiRequestStringV1?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: ApiRequestStringV1?,
    ): CenterLineGeometryResponseV1 {
        val translation = localizationService.getLocalization(LocalizationLanguage.FI)

        val (validationErrors, validRequest) =
            LocationTrackRequestV1(locationTrackOid, coordinateSystem).let(centerLineGeometryService::validate)

        return validRequest?.let { processValidatedRequest(translation, validRequest) }
            ?: createErrorResponse(translation, validationErrors)
    }
}
