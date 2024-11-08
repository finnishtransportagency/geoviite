package fi.fta.geoviite.infra.configuration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["geoviite.cache.enabled", "geoviite.cache.tasks.preload.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class CachePreloadScheduler
@Autowired
constructor(
    @Value("\${geoviite.cache.tasks.preload.geocoding-contexts}") private val preloadGeocodingContexts: Boolean,
    @Value("\${geoviite.cache.tasks.preload.plan-headers}") private val preloadPlanHeaders: Boolean,
    private val cachePreloadService: CachePreloadService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val CONFIG_INITIAL_DELAY = "\${geoviite.cache.tasks.preload.initial-delay}"
        const val CONFIG_INTERVAL = "\${geoviite.cache.tasks.preload.interval}"
    }

    @Scheduled(initialDelayString = CONFIG_INITIAL_DELAY, fixedDelayString = CONFIG_INTERVAL)
    private fun scheduleReload() {
        val startTime = System.currentTimeMillis()
        logger.info("Preloading caches: geocodingContexts=$preloadGeocodingContexts planHeaders=$preloadPlanHeaders")
        // First stage caches that can be loaded in parallel
        listOf(
                { cachePreloadService.loadAlignmentCache() },
                { cachePreloadService.loadLayoutCache() },
                { if (preloadPlanHeaders) cachePreloadService.loadPlanHeaderCache() },
            )
            .parallelStream()
            .forEach { preload -> preload() }
        // Second stage caches that use the first stage and need to be loaded after them
        listOf(
                { cachePreloadService.loadLocationTrackSpatialCache() },
                { if (preloadGeocodingContexts) cachePreloadService.loadGeocodingContextCache() },
            )
            .parallelStream()
            .forEach { preload -> preload() }
        logger.info("Preloading caches done: duration=${System.currentTimeMillis() - startTime}ms")
    }
}
