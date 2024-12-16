package fi.fta.geoviite.infra.geocoding

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import org.springframework.transaction.annotation.Transactional

data class AddressPointCacheKey(
    val locationTrackVersion: LayoutRowVersion<LocationTrack>,
    val geocodingContextCacheKey: GeocodingContextCacheKey,
)

data class AddressPointCalculationData(
    val key: AddressPointCacheKey,
    val alignment: DbLocationTrackGeometry,
    val geocodingContext: GeocodingContext,
)

const val ADDRESS_POINT_CACHE_SIZE = 2000L

@GeoviiteService
class AddressPointsCache(
    val alignmentDao: LayoutAlignmentDao,
    val locationTrackDao: LocationTrackDao,
    val geocodingDao: GeocodingDao,
    val geocodingCacheService: GeocodingCacheService,
) {
    private val cache: Cache<AddressPointCacheKey, AlignmentAddresses?> =
        Caffeine.newBuilder().maximumSize(ADDRESS_POINT_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    @Transactional(readOnly = true)
    fun getAddressPointCacheKey(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): AddressPointCacheKey? {
        return locationTrackDao.fetchVersion(layoutContext, locationTrackId)?.let { trackVersion ->
            val track = locationTrackDao.fetch(trackVersion)
            geocodingDao.getLayoutGeocodingContextCacheKey(layoutContext, track.trackNumberId)?.let { contextCacheKey ->
                AddressPointCacheKey(trackVersion, contextCacheKey)
            }
        }
    }

    /**
     * This is for caching address points. Please don't call this directly if possible, please prefer GeocodingService's
     * getAddressPoints method instead
     */
    fun getAddressPoints(cacheKey: AddressPointCacheKey): AlignmentAddresses? {
        return getAddressPointCalculationData(cacheKey)?.let(::getAddressPoints)
    }

    fun getAddressPointCalculationData(cacheKey: AddressPointCacheKey): AddressPointCalculationData? =
        geocodingCacheService.getGeocodingContext(cacheKey.geocodingContextCacheKey)?.let { geocodingContext ->
            AddressPointCalculationData(cacheKey, alignmentDao.get(cacheKey.locationTrackVersion), geocodingContext)
        }

    fun getAddressPoints(input: AddressPointCalculationData): AlignmentAddresses? =
        cache.get(input.key) { input.geocodingContext.getAddressPoints(input.alignment) }
}
