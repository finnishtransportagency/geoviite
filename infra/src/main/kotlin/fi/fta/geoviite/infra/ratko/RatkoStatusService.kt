package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.configuration.CACHE_RATKO_HEALTH_STATUS
import fi.fta.geoviite.infra.integration.RatkoAssetType.*
import fi.fta.geoviite.infra.integration.RatkoPushErrorWithAsset
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.ratko.RatkoClient.RatkoStatus
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class RatkoStatusService @Autowired constructor(
    private val ratkoClient: RatkoClient?,
    private val ratkoPushDao: RatkoPushDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
) {
    @Cacheable(CACHE_RATKO_HEALTH_STATUS, sync = true)
    fun getRatkoOnlineStatus(): RatkoStatus {
        logger.serviceCall("getRatkoOnlineStatus")
        return ratkoClient?.getRatkoOnlineStatus() ?: RatkoStatus(false)
    }

    fun getRatkoPushError(publicationId: IntId<Publication>): RatkoPushErrorWithAsset? {
        logger.serviceCall("getRatkoPushError", "publicationId" to publicationId)
        return ratkoPushDao.getLatestRatkoPushErrorFor(publicationId)?.let { ratkoError ->
            val asset = when (ratkoError.assetType) {
                TRACK_NUMBER -> trackNumberService.get(OFFICIAL, ratkoError.assetId as IntId<TrackLayoutTrackNumber>)
                LOCATION_TRACK -> locationTrackService.get(OFFICIAL, ratkoError.assetId as IntId<LocationTrack>)
                SWITCH -> switchService.get(OFFICIAL, ratkoError.assetId as IntId<TrackLayoutSwitch>)
            }
            checkNotNull(asset) { "No asset found for id! ${ratkoError.assetType} ${ratkoError.assetId}" }

            RatkoPushErrorWithAsset(
                ratkoError.id,
                ratkoError.ratkoPushId,
                ratkoError.errorType,
                ratkoError.operation,
                ratkoError.assetType,
                asset
            )
        }
    }
}
