package fi.fta.geoviite.infra.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["geoviite.cache.enabled", "geoviite.cache.tasks.preload.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class CachePreloadScheduler @Autowired constructor(private val cachePreloadService: CachePreloadService) {

    companion object {
        const val CONFIG_INITIAL_DELAY = "\${geoviite.cache.tasks.preload.initial-delay}"
        const val CONFIG_INTERVAL = "\${geoviite.cache.tasks.preload.interval}"
    }

    @Scheduled(initialDelayString = CONFIG_INITIAL_DELAY, fixedDelayString = CONFIG_INTERVAL)
    private fun scheduleLayoutAssetReload() {
        cachePreloadService.loadLayoutCache()
    }

    @Scheduled(initialDelayString = CONFIG_INITIAL_DELAY, fixedDelayString = CONFIG_INTERVAL)
    private fun schedulePlanHeaderReload() {
        cachePreloadService.loadPlanHeaderCache()
    }

    @Scheduled(initialDelayString = CONFIG_INITIAL_DELAY, fixedDelayString = CONFIG_INTERVAL)
    private fun scheduleAlignmentReload() {
        cachePreloadService.loadAlignmentCache()
    }
}
