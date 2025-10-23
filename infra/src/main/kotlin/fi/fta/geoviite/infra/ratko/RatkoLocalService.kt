package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.configuration.CACHE_RATKO_HEALTH_STATUS
import fi.fta.geoviite.infra.integration.RatkoAssetType.LOCATION_TRACK
import fi.fta.geoviite.infra.integration.RatkoAssetType.SWITCH
import fi.fta.geoviite.infra.integration.RatkoAssetType.TRACK_NUMBER
import fi.fta.geoviite.infra.integration.RatkoPushErrorWithAsset
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.ratko.RatkoClient.RatkoStatus
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable

@GeoviiteService
class RatkoLocalService
@Autowired
constructor(
    private val ratkoClient: RatkoClient?,
    private val ratkoPushDao: RatkoPushDao,
    private val ratkoOperationalPointDao: RatkoOperationalPointDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
) {

    @Cacheable(CACHE_RATKO_HEALTH_STATUS, sync = true)
    fun getRatkoOnlineStatus(): RatkoStatus {
        return ratkoClient?.getRatkoOnlineStatus() ?: RatkoStatus(RatkoConnectionStatus.NOT_CONFIGURED, null)
    }

    fun getRatkoPushError(publicationId: IntId<Publication>): RatkoPushErrorWithAsset? {
        return ratkoPushDao.getLatestRatkoPushErrorFor(publicationId)?.let { ratkoError ->
            val asset =
                when (ratkoError.assetType) {
                    TRACK_NUMBER ->
                        trackNumberService.get(
                            MainLayoutContext.official,
                            ratkoError.assetId as IntId<LayoutTrackNumber>,
                        )
                    LOCATION_TRACK ->
                        locationTrackService.get(MainLayoutContext.official, ratkoError.assetId as IntId<LocationTrack>)
                    SWITCH -> switchService.get(MainLayoutContext.official, ratkoError.assetId as IntId<LayoutSwitch>)
                }
            checkNotNull(asset) { "No asset found for id! ${ratkoError.assetType} ${ratkoError.assetId}" }

            RatkoPushErrorWithAsset(
                ratkoError.id,
                ratkoError.ratkoPushId,
                ratkoError.errorType,
                ratkoError.operation,
                ratkoError.assetType,
                asset,
            )
        }
    }

    fun getOperationalPoints(bbox: BoundingBox): List<RatkoOperationalPoint> {
        return ratkoOperationalPointDao.getOperationalPoints(bbox)
    }
}
