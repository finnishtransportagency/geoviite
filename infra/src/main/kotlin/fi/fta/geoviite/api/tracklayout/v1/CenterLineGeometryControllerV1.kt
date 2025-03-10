package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.authorization.AUTH_API_GEOMETRY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@PreAuthorize(AUTH_API_GEOMETRY)
@GeoviiteExtApiController([])
class CenterLineGeometryControllerV1(private val centerLineGeometryService: CenterLineGeometryServiceV1) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping(
        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}",
        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/",
    )
    fun singleTrackCenterLineGeometry(
        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: ApiRequestStringV1,
        @RequestParam(CHANGE_TIME_PARAM, required = false) changesAfterTimestamp: ApiRequestStringV1?,
        @RequestParam(TRACK_KILOMETER_START_PARAM, required = false) trackKilometerStart: ApiRequestStringV1?,
        @RequestParam(TRACK_KILOMETER_END_PARAM, required = false) trackKilometerInclusiveEnd: ApiRequestStringV1?,
        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: ApiRequestStringV1?,
        @RequestParam(ADDRESS_POINT_INTERVAL_PARAM, required = false) addressPointIntervalMeters: Double?,
        @RequestParam(INCLUDE_GEOMETRY_PARAM, required = false) includeGeometry: Boolean = false,
    ): CenterLineGeometryResponseV1 {
        val (validationErrors, validRequest) =
            CenterLineGeometryRequestV1(
                    locationTrackOid,
                    changesAfterTimestamp,
                    trackKilometerStart,
                    trackKilometerInclusiveEnd,
                    coordinateSystem,
                    addressPointIntervalMeters,
                    includeGeometry,
                )
                .let(centerLineGeometryService::validate)

        return when {
            validRequest != null -> {
                centerLineGeometryService.process(validRequest)
            }

            else -> {
                CenterLineGeometryResponseErrorV1(
                    errors = requireNotNull(validationErrors) // TODO
                )
            }
        }
    }
}
