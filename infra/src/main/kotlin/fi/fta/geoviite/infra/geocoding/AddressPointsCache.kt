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
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import java.util.*
import org.springframework.transaction.annotation.Transactional

data class AddressPointCacheKey(
    val locationTrackVersion: LayoutRowVersion<LocationTrack>,
    val geocodingContextCacheKey: LayoutGeocodingContextCacheKey,
    val resolution: Resolution,
)

data class AddressPointCalculationData(
    val key: AddressPointCacheKey,
    val geometry: DbLocationTrackGeometry,
    val geocodingContext: GeocodingContext<ReferenceLineM>,
)

const val ADDRESS_POINT_CACHE_SIZE = 2000L

@GeoviiteService
class AddressPointsCache(
    val alignmentDao: LayoutAlignmentDao,
    val locationTrackDao: LocationTrackDao,
    val geocodingDao: GeocodingDao,
    val geocodingCacheService: GeocodingCacheService,
) {
    private val cache: Cache<AddressPointCacheKey, Optional<AddressPointsResult<LocationTrackM>>> =
        Caffeine.newBuilder().maximumSize(ADDRESS_POINT_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    @Transactional(readOnly = true)
    fun getAddressPointCacheKey(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        resolution: Resolution,
    ): AddressPointCacheKey? {
        return locationTrackDao.fetchVersion(layoutContext, locationTrackId)?.let { trackVersion ->
            val track = locationTrackDao.fetch(trackVersion)
            geocodingDao.getLayoutGeocodingContextCacheKey(layoutContext, track.trackNumberId)?.let { contextCacheKey ->
                AddressPointCacheKey(trackVersion, contextCacheKey, resolution)
            }
        }
    }

    /**
     * This is for caching address points. Please don't call this directly if possible, please prefer GeocodingService's
     * getAddressPoints method instead
     */
    fun getAddressPoints(cacheKey: AddressPointCacheKey): AddressPointsResult<LocationTrackM>? =
        cache
            .get(cacheKey) {
                Optional.ofNullable(
                    getAddressPointCalculationData(cacheKey)?.let { input ->
                        input.geocodingContext.getAddressPoints(input.geometry, input.key.resolution)
                    }
                )
            }
            .orElse(null)

    fun getAddressPointCalculationData(cacheKey: AddressPointCacheKey): AddressPointCalculationData? =
        geocodingCacheService.getGeocodingContext(cacheKey.geocodingContextCacheKey)?.let { geocodingContext ->
            AddressPointCalculationData(cacheKey, alignmentDao.fetch(cacheKey.locationTrackVersion), geocodingContext)
        }
}
