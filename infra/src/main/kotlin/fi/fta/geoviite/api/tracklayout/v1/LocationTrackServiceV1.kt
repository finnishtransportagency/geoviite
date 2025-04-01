package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class LocationTrackServiceV1 @Autowired constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    //    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    localizationService: LocalizationService,
){
    fun validate(request: LocationTrackRequestV1): Pair<<List<String /*TODO */>, ValidLocationTrackRequestV1) {
        return emptyList() to ValidLocationTrackRequestV1(
                
        )
    }
}
