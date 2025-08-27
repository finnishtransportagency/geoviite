package fi.fta.geoviite.infra.configuration

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

const val CACHE_GEOMETRY_PLAN = "geometry-plan"
const val CACHE_GEOMETRY_SWITCH = "geometry-switch"

const val CACHE_ROLES = "roles"
const val CACHE_GVT_COORDINATE_SYSTEMS = "gvt-coordinate-systems"
const val CACHE_ANY_COORDINATE_SYSTEMS = "any-coordinate-systems"
const val CACHE_FEATURE_TYPES = "feature-types"
const val CACHE_COMMON_SWITCH_OWNER = "switch-owner"
const val CACHE_COMMON_LOCATION_TRACK_OWNER = "location-track-owner"
const val CACHE_KKJ_TM35FIN_TRIANGULATION_NETWORK = "kkj-tm35fin-triangles"

const val CACHE_GEOCODING_CONTEXTS = "geocoding-contexts"
const val CACHE_PLAN_GEOCODING_CONTEXTS = "plan-geocoding-contexts"

const val CACHE_RATKO_HEALTH_STATUS = "ratko-health-status"

val planCacheDuration: Duration = Duration.ofMinutes(60)
val layoutCacheDuration: Duration = Duration.ofMinutes(60)
val staticDataCacheDuration: Duration = Duration.ofHours(24)

@EnableCaching
@Configuration
class CacheConfiguration
@Autowired
constructor(@Value("\${geoviite.cache.enabled}") private val cacheEnabled: Boolean) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val healthCheckLifetime: Duration = Duration.ofSeconds(10)

    @Bean
    fun cacheManager(): CacheManager {
        logger.info("Setting up cache manager")

        return if (cacheEnabled) {
            val manager = CaffeineCacheManager()

            manager.registerCustomCache(CACHE_ROLES, cache(10, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_GVT_COORDINATE_SYSTEMS, cache(1, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_ANY_COORDINATE_SYSTEMS, cache(1000, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_FEATURE_TYPES, cache(1, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_KKJ_TM35FIN_TRIANGULATION_NETWORK, cache(2, staticDataCacheDuration))

            manager.registerCustomCache(CACHE_GEOCODING_CONTEXTS, cache(1000, layoutCacheDuration))

            manager.registerCustomCache(CACHE_GEOMETRY_PLAN, cache(100, planCacheDuration))
            manager.registerCustomCache(CACHE_GEOMETRY_SWITCH, cache(10000, planCacheDuration))
            manager.registerCustomCache(CACHE_PLAN_GEOCODING_CONTEXTS, cache(50, planCacheDuration))

            manager.registerCustomCache(CACHE_RATKO_HEALTH_STATUS, ephemeralCache(1, healthCheckLifetime))

            manager
        } else {
            NoOpCacheManager()
        }
    }
}

private fun <Key : Any, Value> cache(maxSize: Int, duration: Duration): Cache<Key, Value> =
    Caffeine.newBuilder().maximumSize(maxSize.toLong()).expireAfterAccess(duration).recordStats().build()

private fun <Key : Any, Value> ephemeralCache(maxSize: Int, lifetime: Duration): Cache<Key, Value> =
    Caffeine.newBuilder().maximumSize(maxSize.toLong()).expireAfterWrite(lifetime).recordStats().build()
