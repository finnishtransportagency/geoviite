package fi.fta.geoviite.infra.configuration

import com.github.benmanes.caffeine.cache.stats.CacheStats

fun interface ManualCacheStatsProvider {
    fun cacheStats(): Map<String, CacheStats>
}
