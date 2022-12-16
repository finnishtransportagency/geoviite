package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.CACHE_ADDRESS_POINTS
import fi.fta.geoviite.infra.linking.PublicationVersions
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class AddressPointCacheKey(
    val locationTrackVersion: RowVersion<LocationTrack>,
    val alignmentVersion: RowVersion<LayoutAlignment>,
    val geocodingContextCacheKey: GeocodingContextCacheKey,
)

@Component
class AddressPointsCache(
    val alignmentDao: LayoutAlignmentDao,
    val locationTrackDao: LocationTrackDao,
    val geocodingDao: GeocodingDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional(readOnly = true)
    fun getAddressPointCacheKey(
        publishType: PublishType,
        locationTrackId: IntId<LocationTrack>,
    ): AddressPointCacheKey? {
        return locationTrackDao.fetchVersion(locationTrackId, publishType)?.let { trackVersion ->
            val track = locationTrackDao.fetch(trackVersion)
            val contextCacheKey = geocodingDao.getGeocodingContextCacheKey(publishType, track.trackNumberId)
            if (track.alignmentVersion != null && contextCacheKey != null) {
                AddressPointCacheKey(trackVersion, track.alignmentVersion, contextCacheKey)
            } else {
                null
            }
        }
    }

    @Transactional(readOnly = true)
    fun getAddressPointCacheKey(
        locationTrackId: IntId<LocationTrack>,
        publicationVersions: PublicationVersions,
    ): AddressPointCacheKey? {
        val locationTrackVersion = publicationVersions.findLocationTrack(locationTrackId)?.draftVersion
            ?: locationTrackDao.fetchVersion(locationTrackId, OFFICIAL)
        return locationTrackVersion?.let { trackVersion ->
            val track = locationTrackDao.fetch(trackVersion)
            val contextCacheKey = geocodingDao.getGeocodingContextCacheKey(track.trackNumberId, publicationVersions)
            if (track.alignmentVersion != null && contextCacheKey != null) {
                AddressPointCacheKey(trackVersion, track.alignmentVersion, contextCacheKey)
            } else {
                null
            }
        }
    }

    /** This is for caching address points. Please don't call this directly if possible, please
    prefer GeocodingService's getAddressPoints method instead
     **/
    @Cacheable(CACHE_ADDRESS_POINTS, sync = true)
    fun getAddressPoints(cacheKey: AddressPointCacheKey): AlignmentAddresses? {
        logger.serviceCall("getAddressPoints", "cacheKey" to cacheKey)
        val alignment = alignmentDao.fetch(cacheKey.alignmentVersion)
        return geocodingDao.getGeocodingContext(cacheKey.geocodingContextCacheKey)
            ?.getAddressPoints(alignment)
    }
}
