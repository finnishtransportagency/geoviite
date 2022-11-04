package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.configuration.CACHE_ADDRESS_POINTS
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.util.RowVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.Instant

data class AddressPointCacheKey(
    val locationTrackVersion: RowVersion<LocationTrack>,
    val publishType: PublishType,
    val changeTime: Instant
)

data class TrackGeocodingData(
    val locationTrack: LocationTrack,
    val alignment: LayoutAlignment,
    val context: GeocodingContext,
)

@Service
class AddressPointService(
    val alignmentDao: LayoutAlignmentDao,
    val locationTrackDao: LocationTrackDao,
    val geocodingDao: GeocodingDao
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getAddressPointCacheKey(
        publishType: PublishType,
        locationTrack: LocationTrack
    ): AddressPointCacheKey? =
        geocodingDao.getGeocodingContextChangeTime(publishType, locationTrack.trackNumberId)
            ?.let { geocodingContextChangeTime ->
                check(locationTrack.id is IntId) { "Trying to calculate address points for unsaved locationtrack" }
                AddressPointCacheKey(
                    locationTrackVersion = locationTrackDao.fetchVersionOrThrow(locationTrack.id, publishType),
                    publishType = publishType,
                    changeTime = geocodingContextChangeTime
                )
            }

    /** This is for caching address points. Please don't call this directly if possible, please
    prefer GeocodingService's getAddressPoints method instead
     **/
    @Cacheable(CACHE_ADDRESS_POINTS, sync = true)
    fun getAddressPointsInternal(
        cacheKey: AddressPointCacheKey
    ): AlignmentAddresses? {
        logger.serviceCall(
            "getAddressPoints",
            "locationTrack" to cacheKey.locationTrackVersion.id, "publishType" to cacheKey.publishType
        )
        return getTrackGeocodingData(
            cacheKey.publishType,
            cacheKey.locationTrackVersion,
            cacheKey.changeTime
        )?.let { data ->
            data.context.getAddressPoints(data.alignment)
        }
    }

    fun getTrackGeocodingData(publishType: PublishType, id: IntId<LocationTrack>) =
        locationTrackDao.fetchVersion(id, publishType)?.let { version ->
            val locationTrack = locationTrackDao.fetch(version)
            getAddressPointCacheKey(publishType, locationTrack)
                ?.let {
                    getTrackGeocodingData(publishType, version, it.changeTime)
                }
        }

    fun getTrackGeocodingData(
        publishType: PublishType,
        locationTrackRowVersion: RowVersion<LocationTrack>,
        geocodingContextChangeTime: Instant
    ): TrackGeocodingData? {
        val locationTrack = locationTrackDao.fetch(locationTrackRowVersion)
        val alignment = locationTrack.alignmentVersion?.let(alignmentDao::fetch)
        return if (alignment != null && alignment.segments.isNotEmpty()) {
            geocodingDao.getGeocodingContext(
                publishType = publishType,
                trackNumberId = locationTrack.trackNumberId,
                changeTime = geocodingContextChangeTime,
            )?.let { context ->
                TrackGeocodingData(locationTrack, alignment, context)
            }
        } else {
            null
        }
    }
}
