package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.configuration.CACHE_GEOCODING_CONTEXTS
import fi.fta.geoviite.infra.configuration.CACHE_PLAN_GEOCODING_CONTEXTS
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.PlanLayoutService
import fi.fta.geoviite.infra.map.MapAlignmentType
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignmentM
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

sealed interface GeocodingContextCacheKey

data class LayoutGeocodingContextCacheKey(
    val trackNumberId: IntId<LayoutTrackNumber>,
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    val referenceLineVersion: LayoutRowVersion<ReferenceLine>,
    val kmPostVersions: List<LayoutRowVersion<LayoutKmPost>>,
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

data class GeometryGeocodingContextCacheKey(val trackNumber: TrackNumber, val planVersion: RowVersion<GeometryPlan>) :
    GeocodingContextCacheKey

@GeoviiteService
class GeocodingCacheService(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val kmPostDao: LayoutKmPostDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val planLayoutService: PlanLayoutService,
    private val geocodingDao: GeocodingDao,
) {
    @Autowired @Lazy lateinit var geocodingCacheService: GeocodingCacheService

    @Transactional(readOnly = true)
    fun getGeocodingContext(key: LayoutGeocodingContextCacheKey): GeocodingContext<ReferenceLineM>? =
        getGeocodingContextWithReasons(key)?.geocodingContext

    @Transactional(readOnly = true)
    fun getGeocodingContext(key: GeometryGeocodingContextCacheKey): GeocodingContext<PlanLayoutAlignmentM>? =
        getGeocodingContextWithReasons(key)?.geocodingContext

    @Transactional(readOnly = true)
    fun getGeocodingContextWithReasons(
        key: LayoutGeocodingContextCacheKey
    ): ValidatedGeocodingContext<ReferenceLineM>? = geocodingCacheService.getLayoutGeocodingContext(key)

    @Transactional(readOnly = true)
    fun getGeocodingContextWithReasons(
        key: GeometryGeocodingContextCacheKey
    ): ValidatedGeocodingContext<PlanLayoutAlignmentM>? = geocodingCacheService.getGeometryGeocodingContext(key)

    @Transactional(readOnly = true)
    @Cacheable(CACHE_GEOCODING_CONTEXTS, sync = true)
    fun getLayoutGeocodingContext(key: LayoutGeocodingContextCacheKey): ValidatedGeocodingContext<ReferenceLineM>? {
        val trackNumber = trackNumberDao.fetch(key.trackNumberVersion)
        val referenceLine = referenceLineDao.fetch(key.referenceLineVersion)
        val alignment =
            referenceLine.alignmentVersion?.let(alignmentDao::fetch)
                ?: throw IllegalStateException("DB ReferenceLine should have an alignment")
        // If the track number is deleted or reference line has no geometry, we cannot geocode.
        if (!trackNumber.exists || alignment.segments.isEmpty()) return null
        val kmPosts = kmPostDao.fetchMany(key.kmPostVersions).sortedBy { post -> post.kmNumber }
        return GeocodingContext.create(trackNumber.number, referenceLine.startAddress, alignment, kmPosts)
    }

    @Transactional(readOnly = true)
    @Cacheable(CACHE_PLAN_GEOCODING_CONTEXTS, sync = true)
    fun getGeometryGeocodingContext(
        key: GeometryGeocodingContextCacheKey
    ): ValidatedGeocodingContext<PlanLayoutAlignmentM>? {
        val plan = planLayoutService.getLayoutPlan(key.planVersion).first
        val startAddress = plan?.startAddress
        val referenceLine = plan?.let(::getGeometryGeocodingContextReferenceLine)

        return if (startAddress == null || referenceLine == null) {
            null
        } else {
            GeocodingContext.create(key.trackNumber, startAddress, referenceLine, plan.kmPosts)
        }
    }

    private fun getGeometryGeocodingContextReferenceLine(plan: GeometryPlanLayout): PlanLayoutAlignment? {
        val referenceLines =
            plan.alignments.filter { alignment -> alignment.header.alignmentType == MapAlignmentType.REFERENCE_LINE }
        return if (referenceLines.size == 1) referenceLines[0] else null
    }

    @Transactional(readOnly = true)
    fun getGeocodingContextCreateResult(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): ValidatedGeocodingContext<ReferenceLineM>? =
        geocodingDao
            .getLayoutGeocodingContextCacheKey(layoutContext, trackNumberId)
            ?.let(geocodingCacheService::getGeocodingContextWithReasons)

    @Transactional(readOnly = true)
    fun getGeocodingContextAtMoment(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContext<ReferenceLineM>? =
        geocodingDao
            .getLayoutGeocodingContextCacheKey(branch, trackNumberId, moment)
            ?.let(geocodingCacheService::getGeocodingContext)

    @Transactional(readOnly = true)
    fun getGeocodingContext(
        trackNumber: TrackNumber,
        plan: RowVersion<GeometryPlan>,
    ): GeocodingContext<PlanLayoutAlignmentM>? =
        getGeocodingContext(GeometryGeocodingContextCacheKey(trackNumber, plan))
}
