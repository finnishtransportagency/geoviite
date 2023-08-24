package fi.fta.geoviite.infra.configuration

import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

const val CACHE_WARMUP_DELAY = 1000L
const val CACHE_RELOAD_INTERVAL = 45 * 60 * 1000L

@ConditionalOnWebApplication
@Component
class CachePreloader(
    @Value("\${geoviite.cache.enabled}") private val cacheEnabled: Boolean,
    @Value("\${geoviite.cache.preload}") private val cachePreloadEnabled: Boolean,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val layoutKmPostDao: LayoutKmPostDao,
    private val switchDao: LayoutSwitchDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchStructureDao: SwitchStructureDao,
    private val geometryDao: GeometryDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    data class PreloadedCache<T, S>(
        val name: String,
        val fetchVersions: () -> List<RowVersion<T>>,
        val fetchRow: (RowVersion<T>) -> S,
    )

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun scheduleBasicReload() {
        if (cacheEnabled && cachePreloadEnabled) {
            switchStructureDao.fetchSwitchStructures()
            listOf(
                PreloadedCache("TrackNumber", layoutTrackNumberDao::fetchAllVersions, layoutTrackNumberDao::fetch),
                PreloadedCache("ReferenceLine", referenceLineDao::fetchAllVersions, referenceLineDao::fetch),
//                PreloadedCache("LocationTrack", locationTrackDao::fetchAllVersions, locationTrackDao::fetch),
//                PreloadedCache("Switch", switchDao::fetchAllVersions, switchDao::fetch),
//                PreloadedCache("KM-Post", layoutKmPostDao::fetchAllVersions, layoutKmPostDao::fetch),
                PreloadedCache("PlanHeader", geometryDao::fetchPlanVersions, geometryDao::getPlanHeader),
            ).parallelStream().forEach { cache -> refreshCache(cache) }

//            refreshCache("TrackNumber", layoutTrackNumberDao::fetchAllVersions, layoutTrackNumberDao::fetch)
//            refreshCache("ReferenceLine", referenceLineDao::fetchAllVersions, referenceLineDao::fetch)
//            refreshCache("LocationTrack", locationTrackDao::fetchAllVersions, locationTrackDao::fetch)
//            refreshCache("Switch", switchDao::fetchAllVersions, switchDao::fetch)
//            refreshCache("KM-Post", layoutKmPostDao::fetchAllVersions, layoutKmPostDao::fetch)
//            refreshCache("PlanHeader", geometryDao::fetchPlanVersions, geometryDao::getPlanHeader)
        }
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun preloadTracks() {
//        refreshCache("LocationTrack-preload") { locationTrackDao.preloadLocationTracks() }
        refreshCache("LocationTrack", locationTrackDao::fetchAllVersions, locationTrackDao::fetch)
//        refreshCache("LocationTrack", locationTrackDao::preloadCache)
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun preloadSwitches() {
        refreshCache("Switch", switchDao::preloadCache)
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun preloadKmPosts() {
        refreshCache("KM-Post", layoutKmPostDao::preloadCache)
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun scheduleAlignmentReload() {
        if (cacheEnabled && cachePreloadEnabled) {
            refreshCache("SegmentGeometries") { alignmentDao.preloadSegmentGeometries() }
            refreshCache("Alignment", alignmentDao::fetchVersions, alignmentDao::fetch)
        }
    }

    private fun <T, S> refreshCache(cache: PreloadedCache<T, S>) =
        refreshCache(cache.name) { cache.fetchVersions().forEach { v -> cache.fetchRow(v) } }

    private fun <T, S> refreshCache(
        name: String,
        fetchVersions: () -> List<RowVersion<T>>,
        fetchRow: (RowVersion<T>) -> S,
    ) = refreshCache(name) {
        fetchVersions()
//            .parallelStream()
            .forEach { version -> fetchRow(version) }
    }

    private fun refreshCache(name: String, refresh: () -> Unit) {
        logger.info("Refreshing cache: name=$name")
        val start = Instant.now()
        refresh()
        logger.info("Cache refreshed: name=$name duration=${Duration.between(start, Instant.now()).toMillis()}ms")
    }
}
