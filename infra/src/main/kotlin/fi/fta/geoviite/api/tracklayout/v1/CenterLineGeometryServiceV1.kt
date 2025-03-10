package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class CenterLineGeometryServiceV1
@Autowired
constructor(
    //    private val trackNumberService: LayoutTrackNumberService,
    //    private val trackNumberDao: LayoutTrackNumberDao,
    //    private val geocodingService: GeocodingService,
    //    private val locationTrackService: LocationTrackService,
    //    private val locationTrackDao: LocationTrackDao,
    //    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    localizationService: LocalizationService
) {
    val translation = localizationService.getLocalization(LocalizationLanguage.FI)

    fun validate(
        request: CenterLineGeometryRequestV1
    ): Pair<List<CenterLineGeometryErrorV1>?, ValidCenterLineGeometryRequestV1?> {
        val validRequest =
            ValidCenterLineGeometryRequestV1(
                locationTrackOid = request.locationTrackOid // TODO Check that this exists in the db
                //            coordinateSystem =

            )

        return null to validRequest

        //        return emptyList<CenterLineGeometryErrorV1>() to null // TODO
    }

    fun process(request: ValidCenterLineGeometryRequestV1): CenterLineGeometryResponseV1 {
        return CenterLineGeometryResponseOkV1(
            locationTrackOid = request.locationTrackOid
            //            addressPointIntervalMeters = request.addressPointIntervalMeters,
        )
        //
        //        return CenterLineGeometryResponseErrorV1(errors = emptyList())
    }
}
