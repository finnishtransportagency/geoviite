package fi.fta.geoviite.api.tracklayout.v1

// @PreAuthorize(AUTH_API_GEOMETRY)
// @GeoviiteExtApiController([])
// class CenterLineGeometryControllerV1(
//    private val centerLineGeometryService: CenterLineGeometryServiceV1,
//    private val localizationService: LocalizationService,
// ) {
//    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    //    @GetMapping(
    //        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria",
    //        "/geoviite/paikannuspohja/v1/sijaintiraiteet/{$LOCATION_TRACK_OID_PARAM}/geometria/",
    //    )
    //    fun singleTrackCenterLineGeometry(
    //        @PathVariable(LOCATION_TRACK_OID_PARAM) locationTrackOid: ApiRequestStringV1,
    //        @RequestParam(CHANGE_TIME_PARAM, required = false) changesAfterTimestamp: ApiRequestStringV1?,
    //        @RequestParam(TRACK_KILOMETER_START_PARAM, required = false) trackKilometerStart: ApiRequestStringV1?,
    //        @RequestParam(TRACK_KILOMETER_END_PARAM, required = false) trackKilometerInclusiveEnd:
    // ApiRequestStringV1?,
    //        @RequestParam(COORDINATE_SYSTEM_PARAM, required = false) coordinateSystem: ApiRequestStringV1?,
    //        @RequestParam(ADDRESS_POINT_RESOLUTION, required = false) addressPointInterval: ApiRequestStringV1?,
    //        @RequestParam(INCLUDE_GEOMETRY_PARAM, required = false) includeGeometry: Boolean = false,
    //    ): CenterLineGeometryResponseV1 {
    //        val translation = localizationService.getLocalization(LocalizationLanguage.FI)
    //
    //        val (validationErrors, validRequest) =
    //            CenterLineGeometryRequestV1(
    //                    locationTrackOid,
    //                    changesAfterTimestamp,
    //                    trackKilometerStart,
    //                    trackKilometerInclusiveEnd,
    //                    coordinateSystem,
    //                    addressPointInterval,
    //                    includeGeometry,
    //                )
    //                .let(centerLineGeometryService::validate)
    //
    //        return validRequest?.let { processValidatedRequest(translation, validRequest) }
    //            ?: createErrorResponse(translation, validationErrors)
    //    }
    //
    //    @GetMapping("/geoviite/paikannuspohja/v1/sijaintiraiteet/kaikki")
    //    fun singleTrackCenterLineGeometry(): List<CenterLineGeometryResponseV1> {
    //        val translation = localizationService.getLocalization(LocalizationLanguage.FI)
    //
    //        //        val (validationErrors, validRequest) =
    //        //            CenterLineGeometryRequestV1(
    //        //                locationTrackOid,
    //        //                changesAfterTimestamp,
    //        //                trackKilometerStart,
    //        //                trackKilometerInclusiveEnd,
    //        //                coordinateSystem,
    //        //                addressPointInterval,
    //        //                includeGeometry,
    //        //            )
    //        //                .let(centerLineGeometryService::validate)
    //
    //        return centerLineGeometryService.all()
    //
    //        //        return validRequest?.let { processValidatedRequest(translation, validRequest) }
    //        //            ?: createErrorResponse(translation, validationErrors)
    //    }
    //
    //    private fun processValidatedRequest(
    //        translation: Translation,
    //        request: ValidCenterLineGeometryRequestV1,
    //    ): CenterLineGeometryResponseV1 {
    //        val (responseOk, processingErrors) = centerLineGeometryService.process(request)
    //        return responseOk ?: createErrorResponse(translation, processingErrors)
    //    }
// }
//
// private fun createErrorResponse(
//    translation: Translation,
//    errors: List<CenterLineGeometryErrorV1>,
// ): CenterLineGeometryResponseErrorV1 {
//    return CenterLineGeometryResponseErrorV1(errors = errors.map { error -> error.toResponseError(translation) })
// }
