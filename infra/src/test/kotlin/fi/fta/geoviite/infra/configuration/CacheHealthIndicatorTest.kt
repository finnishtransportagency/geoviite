package fi.fta.geoviite.infra.configuration

import com.github.benmanes.caffeine.cache.Caffeine
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.support.NoOpCacheManager

class CacheHealthIndicatorTest {

    @Test
    fun `status is UP with cache stats per cache when caching is enabled`() {
        val manager = CaffeineCacheManager()
        manager.registerCustomCache("foo", Caffeine.newBuilder().recordStats().build())
        manager.registerCustomCache("bar", Caffeine.newBuilder().recordStats().build())

        val health = CacheHealthIndicator(manager, emptyList()).health()

        assertEquals(Status.UP, health.status)

        val details = health.details
        assertNotNull(details["foo"]) { "expected 'foo' cache in health details" }
        assertNotNull(details["bar"]) { "expected 'bar' cache in health details" }

        @Suppress("UNCHECKED_CAST") val fooStats = details["foo"] as Map<String, Any>
        assertNotNull(fooStats["hitRate"])
        assertNotNull(fooStats["loadCount"])
        assertNotNull(fooStats["evictionCount"])
    }

    @Test
    fun `manual cache provider stats appear in health details alongside spring-managed caches`() {
        val manager = CaffeineCacheManager()
        manager.registerCustomCache("spring-cache", Caffeine.newBuilder().recordStats().build())

        val manualCache = Caffeine.newBuilder().recordStats().build<String, String>()
        val provider = ManualCacheStatsProvider { mapOf("manual-cache" to manualCache.stats()) }

        val health = CacheHealthIndicator(manager, listOf(provider)).health()

        assertEquals(Status.UP, health.status)
        val details = health.details
        assertNotNull(details["spring-cache"]) { "expected 'spring-cache' in health details" }
        assertNotNull(details["manual-cache"]) { "expected 'manual-cache' in health details" }

        @Suppress("UNCHECKED_CAST") val manualStats = details["manual-cache"] as Map<String, Any>
        assertNotNull(manualStats["hitRate"])
        assertNotNull(manualStats["loadCount"])
        assertNotNull(manualStats["evictionCount"])
    }

    @Test
    fun `status is UP with caching disabled marker when NoOpCacheManager is used`() {
        val health = CacheHealthIndicator(NoOpCacheManager(), emptyList()).health()

        assertEquals(Status.UP, health.status)
        assertEquals("disabled", health.details["caching"])
    }
}
