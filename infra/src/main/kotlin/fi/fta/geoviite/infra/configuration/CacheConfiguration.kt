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
const val CACHE_GEOMETRY_PLAN_HEADER = "geometry-plan-header"
const val CACHE_GEOMETRY_SWITCH = "geometry-switch"
const val CACHE_GEOMETRY_PLAN_LAYOUT = "geometry-plan-layout"

const val CACHE_LAYOUT_TRACK_NUMBER = "layout-track-number"
const val CACHE_LAYOUT_ALIGNMENT = "layout-alignment"
const val CACHE_LAYOUT_LOCATION_TRACK = "layout-location-track"
const val CACHE_LAYOUT_REFERENCE_LINE = "layout-reference-line"
const val CACHE_LAYOUT_SWITCH = "layout-switch"
const val CACHE_LAYOUT_KM_POST = "layout-km-post"

const val CACHE_ROLES = "roles"
const val CACHE_COORDINATE_SYSTEMS = "coordinate-systems"
const val CACHE_FEATURE_TYPES = "feature-types"
const val CACHE_COMMON_SWITCH_STRUCTURE = "switch-structure"
const val CACHE_COMMON_SWITCH_OWNER = "switch-owner"
const val CACHE_KKJ_ETRS_TRIANGULATION_NETWORK = "kkj-etrs-triangles"

const val CACHE_GEOCODING_CONTEXTS = "geocoding-contexts"

const val CACHE_RATKO_HEALTH_STATUS = "ratko-health-status"

const val CACHE_ADDRESS_POINTS = "track-address-points"

const val CACHE_PUBLISHED_LOCATION_TRACKS = "published-location-tracks"
const val CACHE_PUBLISHED_SWITCHES = "published-switches"

@EnableCaching
@Configuration
class CacheConfiguration @Autowired constructor(
    @Value("\${geoviite.cache.enabled}") private val cacheEnabled: Boolean,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val planCacheDuration: Duration = Duration.ofMinutes(60)
    private val layoutCacheDuration: Duration = Duration.ofMinutes(60)
    private val staticDataCacheDuration: Duration = Duration.ofHours(24)

    private val healthCheckLifetime: Duration = Duration.ofSeconds(10)

    @Bean
    fun cacheManager(): CacheManager {
        logger.info("Setting up cache manager")

        return if (cacheEnabled) {
            val manager = CaffeineCacheManager()

            manager.registerCustomCache(CACHE_ROLES, cache(10, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_COORDINATE_SYSTEMS, cache(1, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_FEATURE_TYPES, cache(1, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_COMMON_SWITCH_STRUCTURE, cache(1, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_KKJ_ETRS_TRIANGULATION_NETWORK, cache(1, staticDataCacheDuration))

            manager.registerCustomCache(CACHE_GEOCODING_CONTEXTS, cache(500, layoutCacheDuration))

            manager.registerCustomCache(CACHE_GEOMETRY_PLAN, cache(100, planCacheDuration))
            manager.registerCustomCache(CACHE_GEOMETRY_PLAN_LAYOUT, cache(100, planCacheDuration))
            manager.registerCustomCache(CACHE_GEOMETRY_PLAN_HEADER, cache(10000, planCacheDuration))
            manager.registerCustomCache(CACHE_GEOMETRY_SWITCH, cache(10000, planCacheDuration))

            manager.registerCustomCache(CACHE_LAYOUT_ALIGNMENT, cache(10000, layoutCacheDuration))
            manager.registerCustomCache(CACHE_LAYOUT_LOCATION_TRACK, cache(10000, layoutCacheDuration))
            manager.registerCustomCache(CACHE_LAYOUT_REFERENCE_LINE, cache(1000, layoutCacheDuration))
            manager.registerCustomCache(CACHE_LAYOUT_TRACK_NUMBER, cache(1000, layoutCacheDuration))
            manager.registerCustomCache(CACHE_LAYOUT_KM_POST, cache(10000, layoutCacheDuration))
            manager.registerCustomCache(CACHE_LAYOUT_SWITCH, cache(10000, layoutCacheDuration))
            manager.registerCustomCache(CACHE_ADDRESS_POINTS, cache(2000, layoutCacheDuration))

            manager.registerCustomCache(CACHE_RATKO_HEALTH_STATUS, ephemeralCache(1, healthCheckLifetime))

            manager.registerCustomCache(CACHE_PUBLISHED_LOCATION_TRACKS, cache(500, staticDataCacheDuration))
            manager.registerCustomCache(CACHE_PUBLISHED_SWITCHES, cache(500, staticDataCacheDuration))


            manager
        } else {
            NoOpCacheManager()
        }
    }
}

private fun <Key, Value> cache(maxSize: Int, duration: Duration): Cache<Key, Value> =
    Caffeine.newBuilder()
        .maximumSize(maxSize.toLong())
        .expireAfterAccess(duration)
        .build()

private fun <Key, Value> ephemeralCache(maxSize: Int, lifetime: Duration): Cache<Key, Value> =
    Caffeine.newBuilder()
        .maximumSize(maxSize.toLong())
        .expireAfterWrite(lifetime)
        .build()
