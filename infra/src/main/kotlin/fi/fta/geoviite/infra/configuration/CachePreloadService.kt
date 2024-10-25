package fi.fta.geoviite.infra.configuration

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.geocoding.GeocodingCacheService
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackSpatialCache
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import java.time.Duration
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@GeoviiteService
class CachePreloadService(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val layoutKmPostDao: LayoutKmPostDao,
    private val switchDao: LayoutSwitchDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
    private val geocodingCacheService: GeocodingCacheService,
    private val geocodingDao: GeocodingDao,
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun loadLayoutCache() {
        listOf(layoutTrackNumberDao, referenceLineDao, locationTrackDao, switchDao, layoutKmPostDao)
            .parallelStream()
            .forEach { dao -> refreshCache(dao) }
    }

    fun loadPlanHeaderCache() {
        refreshCache("PlanHeader", geometryDao::preloadHeaderCache)
    }

    fun loadAlignmentCache() {
        refreshCache("SegmentGeometries", alignmentDao::preloadSegmentGeometries)
        refreshCache("Alignment", alignmentDao::preloadAlignmentCache)
        refreshCache("Node", alignmentDao::preloadNodes)
        refreshCache("Edge", alignmentDao::preloadEdges)
        refreshCache("LocationTrackGeometry", alignmentDao::preloadLocationTrackGeometries)
    }

    fun loadGeocodingContextCache() {
        refreshCache("GeocodingContext") {
            val contexts =
                geocodingDao
                    .listLayoutGeocodingContextCacheKeys(MainLayoutContext.official)
                    .mapNotNull(geocodingCacheService::getGeocodingContext)
            contexts.parallelStream().forEach(GeocodingContext::preload)
            contexts.size
        }
    }

    fun loadLocationTrackSpatialCache() {
        refreshCache("LocationTrack.Spatial.MAIN_OFFICIAL") {
            locationTrackSpatialCache.get(MainLayoutContext.official).size
        }
    }

    private fun <T : LayoutAsset<T>> refreshCache(dao: LayoutAssetDao<T>) =
        refreshCache(dao.table.name, dao::preloadCache)

    private fun refreshCache(name: String, refresh: () -> Int) {
        logger.info("Refreshing cache: name=$name")
        val start = Instant.now()
        val rowCount = refresh()
        logger.info(
            "Cache refreshed: name=$name rows=$rowCount duration=${Duration.between(start, Instant.now()).toMillis()}ms"
        )
    }
}
