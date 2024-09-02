package fi.fta.geoviite.infra.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["geoviite.cache.enabled", "geoviite.cache.preload"],
    havingValue = "true",
    matchIfMissing = false,
)
class CachePreloaderScheduler @Autowired constructor(
    private val cachePreloader: CachePreloader,
) {
    companion object {
        private const val CACHE_WARMUP_DELAY = 1000L
        private const val CACHE_RELOAD_INTERVAL = 45 * 60 * 1000L
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun scheduleLayoutAssetReload() {
        cachePreloader.loadLayoutCache()
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun schedulePlanHeaderReload() {
        cachePreloader.loadPlanHeaderCache()
    }

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun scheduleAlignmentReload() {
        cachePreloader.loadAlignmentCache()
    }
}
