package fi.fta.geoviite.infra.configuration

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

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun scheduleDraftableReload() {
        if (cacheEnabled && cachePreloadEnabled) {
            switchStructureDao.fetchSwitchStructures()
            listOf(
                layoutTrackNumberDao, referenceLineDao, locationTrackDao, switchDao, layoutKmPostDao
            ).parallelStream().forEach { dao -> refreshCache(dao) }
        }
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun schedulePlanHeaderReload() {
        if (cacheEnabled && cachePreloadEnabled) {
            refreshCache("PlanHeader", geometryDao::preloadHeaderCache)
        }
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun scheduleAlignmentReload() {
        if (cacheEnabled && cachePreloadEnabled) {
            refreshCache("SegmentGeometries", alignmentDao::preloadSegmentGeometries)
            refreshCache("Alignment", alignmentDao::preloadAlignmentCache)
        }
    }

    private fun <T : LayoutAsset<T>> refreshCache(dao: LayoutAssetDao<T>) =
        refreshCache(dao.table.name, dao::preloadCache)

    private fun refreshCache(name: String, refresh: () -> Unit) {
        logger.info("Refreshing cache: name=$name")
        val start = Instant.now()
        refresh()
        logger.info("Cache refreshed: name=$name duration=${Duration.between(start, Instant.now()).toMillis()}ms")
    }
}
