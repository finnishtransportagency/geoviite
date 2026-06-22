package fi.fta.geoviite.infra.configuration

import kotlin.test.assertContains
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.support.NoOpCacheManager

class CacheConfigurationTest {

    @Test
    fun `all Cacheable cache names are registered when caching is enabled`() {
        val cacheManager = CacheConfiguration(cacheEnabled = true).cacheManager()

        val registeredNames = cacheManager.cacheNames

        listOf(
                CACHE_ROLES,
                CACHE_GVT_COORDINATE_SYSTEMS,
                CACHE_ANY_COORDINATE_SYSTEMS,
                CACHE_FEATURE_TYPES,
                CACHE_COMMON_SWITCH_OWNER,
                CACHE_COMMON_LOCATION_TRACK_OWNER,
                CACHE_KKJ_TM35FIN_TRIANGULATION_NETWORK,
                CACHE_GEOCODING_CONTEXTS,
                CACHE_GEOMETRY_PLAN,
                CACHE_GEOMETRY_SWITCH,
                CACHE_PLAN_GEOCODING_CONTEXTS,
            )
            .forEach { name -> assertContains(registeredNames, name, "Cache '$name' is not registered") }
    }

    @Test
    fun `NoOpCacheManager is returned when caching is disabled`() {
        val cacheManager = CacheConfiguration(cacheEnabled = false).cacheManager()
        assertIs<NoOpCacheManager>(cacheManager)
    }

    @Test
    fun `CaffeineCacheManager is returned when caching is enabled`() {
        val cacheManager = CacheConfiguration(cacheEnabled = true).cacheManager()
        assertIs<CaffeineCacheManager>(cacheManager)
    }
}
