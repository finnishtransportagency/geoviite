package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.CACHE_GEOCODING_CONTEXTS
import fi.fta.geoviite.infra.configuration.CACHE_PLAN_GEOCODING_CONTEXTS
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.PlanLayoutService
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.map.MapAlignmentType
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service


sealed interface GeocodingContextCacheKey

data class LayoutGeocodingContextCacheKey(
    val trackNumberVersion: RowVersion<TrackLayoutTrackNumber>,
    val referenceLineVersion: RowVersion<ReferenceLine>,
    val kmPostVersions: List<RowVersion<TrackLayoutKmPost>>,
) : GeocodingContextCacheKey {
    init {
        kmPostVersions.forEachIndexed { index, version ->
            kmPostVersions.getOrNull(index + 1)?.also { next ->
                require(next.id.intValue > version.id.intValue) {
                    "Cache key km-posts must be in order: " +
                            "index=$index " +
                            "trackNumberVersion=$trackNumberVersion " +
                            "kmPostVersion=$version " +
                            "nextKmPostVersion=$next"
                }
            }
        }
    }
}

data class GeometryGeocodingContextCacheKey(
    val trackNumberVersion: RowVersion<TrackLayoutTrackNumber>,
    val planVersion: RowVersion<GeometryPlan>,
) : GeocodingContextCacheKey

@Service
class GeocodingCacheService(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val kmPostDao: LayoutKmPostDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val planLayoutService: PlanLayoutService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    @Lazy
    lateinit var geocodingCacheService: GeocodingCacheService

    fun getGeocodingContext(key: GeocodingContextCacheKey): GeocodingContext? =
        getGeocodingContextWithReasons(key)?.geocodingContext

    fun getGeocodingContextWithReasons(key: GeocodingContextCacheKey): GeocodingContextCreateResult? =
        when (key) {
            is LayoutGeocodingContextCacheKey -> geocodingCacheService.getLayoutGeocodingContext(key)
            is GeometryGeocodingContextCacheKey -> geocodingCacheService.getGeometryGeocodingContext(key)
        }

    @Cacheable(CACHE_GEOCODING_CONTEXTS, sync = true)
    fun getLayoutGeocodingContext(key: LayoutGeocodingContextCacheKey): GeocodingContextCreateResult? {
        logger.daoAccess(AccessType.FETCH, GeocodingContext::class, "cacheKey" to key)
        val trackNumber = trackNumberDao.fetch(key.trackNumberVersion)
        val referenceLine = referenceLineDao.fetch(key.referenceLineVersion)
        val alignment = referenceLine.alignmentVersion?.let(alignmentDao::fetch)
            ?: throw IllegalStateException("DB ReferenceLine should have an alignment")
        // If the track number is deleted or reference line has no geometry, we cannot geocode.
        if (!trackNumber.exists || alignment.segments.isEmpty()) return null
        val kmPosts = key.kmPostVersions.map(kmPostDao::fetch).sortedBy { post -> post.kmNumber }
        return GeocodingContext.create(trackNumber, referenceLine.startAddress, alignment, kmPosts)
    }

    @Cacheable(CACHE_PLAN_GEOCODING_CONTEXTS, sync = true)
    fun getGeometryGeocodingContext(key: GeometryGeocodingContextCacheKey): GeocodingContextCreateResult? {
        logger.daoAccess(AccessType.FETCH, GeocodingContext::class, "cacheKey" to key)
        val trackNumber = trackNumberDao.fetch(key.trackNumberVersion)
        val plan = planLayoutService.getLayoutPlan(key.planVersion).first
        val startAddress = plan?.startAddress
        val referenceLine = plan?.let(::getGeometryGeocodingContextReferenceLine)

        return if (startAddress == null || referenceLine == null) null
        else GeocodingContext.create(trackNumber, startAddress, referenceLine, plan.kmPosts)
    }

    private fun getGeometryGeocodingContextReferenceLine(plan: GeometryPlanLayout): PlanLayoutAlignment? {
        val referenceLines = plan.alignments.filter { alignment ->
            alignment.header.alignmentType == MapAlignmentType.REFERENCE_LINE
        }
        return if (referenceLines.size == 1) referenceLines[0] else null
    }
}
