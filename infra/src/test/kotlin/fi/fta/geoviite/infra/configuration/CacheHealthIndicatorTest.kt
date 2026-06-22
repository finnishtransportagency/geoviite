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

        val health = CacheHealthIndicator(manager).health()

        assertEquals(Status.UP, health.status)

        val details = health.details
        assertNotNull(details["foo"]) { "expected 'foo' cache in health details" }
        assertNotNull(details["bar"]) { "expected 'bar' cache in health details" }

        @Suppress("UNCHECKED_CAST")
        val fooStats = details["foo"] as Map<String, Any>
        assertNotNull(fooStats["hitRate"])
        assertNotNull(fooStats["loadCount"])
        assertNotNull(fooStats["evictionCount"])
    }

    @Test
    fun `status is UP with caching disabled marker when NoOpCacheManager is used`() {
        val health = CacheHealthIndicator(NoOpCacheManager()).health()

        assertEquals(Status.UP, health.status)
        assertEquals("disabled", health.details["caching"])
    }
}
