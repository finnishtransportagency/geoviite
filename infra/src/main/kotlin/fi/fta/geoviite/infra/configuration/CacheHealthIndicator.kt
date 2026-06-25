package fi.fta.geoviite.infra.configuration

import com.github.benmanes.caffeine.cache.stats.CacheStats
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.stereotype.Component

@Component
class CacheHealthIndicator(
    private val cacheManager: CacheManager,
    private val manualCacheStatsProviders: List<ManualCacheStatsProvider>,
) : HealthIndicator {

    override fun health(): Health {
        if (cacheManager is NoOpCacheManager) {
            return Health.up().withDetail("caching", "disabled").build()
        }

        val details = buildMap {
            cacheManager.cacheNames.forEach { name ->
                val nativeCache = (cacheManager.getCache(name) as? CaffeineCache)?.nativeCache
                put(
                    name,
                    if (nativeCache != null) nativeCache.stats().toDetailMap() else mapOf("stats" to "unavailable"),
                )
            }
            manualCacheStatsProviders
                .flatMap { it.cacheStats().entries }
                .forEach { (name, stats) -> put(name, stats.toDetailMap()) }
        }

        return Health.up().withDetails(details).build()
    }
}

private fun CacheStats.toDetailMap() =
    mapOf("hitRate" to hitRate(), "loadCount" to loadCount(), "evictionCount" to evictionCount())
