package fi.fta.geoviite.infra.configuration

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.stereotype.Component

@Component
class CacheHealthIndicator(private val cacheManager: CacheManager) : HealthIndicator {

    override fun health(): Health {
        if (cacheManager is NoOpCacheManager) {
            return Health.up().withDetail("caching", "disabled").build()
        }

        val details =
            cacheManager.cacheNames.associateWith { name ->
                val nativeCache = (cacheManager.getCache(name) as? CaffeineCache)?.nativeCache
                if (nativeCache != null) {
                    val stats = nativeCache.stats()
                    mapOf(
                        "hitRate" to stats.hitRate(),
                        "loadCount" to stats.loadCount(),
                        "evictionCount" to stats.evictionCount(),
                    )
                } else {
                    mapOf("stats" to "unavailable")
                }
            }

        return Health.up().withDetails(details).build()
    }
}
