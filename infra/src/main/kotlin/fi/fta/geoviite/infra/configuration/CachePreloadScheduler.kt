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

    @Scheduled(
        initialDelayString = "\${geoviite.cache.tasks.preload.initial-delay}",
        fixedDelayString = "\${geoviite.cache.tasks.preload.interval}",
    )
    fun scheduleLayoutAssetReload() {
        cachePreloadService.loadLayoutCache()
    }

    @Scheduled(
        initialDelayString = "\${geoviite.cache.tasks.preload.initial-delay}",
        fixedDelayString = "\${geoviite.cache.tasks.preload.interval}",
    )
    fun schedulePlanHeaderReload() {
        cachePreloadService.loadPlanHeaderCache()
    }

    @Scheduled(
        initialDelayString = "\${geoviite.cache.tasks.preload.initial-delay}",
        fixedDelayString = "\${geoviite.cache.tasks.preload.interval}",
    )
    fun scheduleAlignmentReload() {
        cachePreloadService.loadAlignmentCache()
    }
}
