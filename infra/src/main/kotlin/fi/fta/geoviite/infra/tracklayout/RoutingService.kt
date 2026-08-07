package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.configuration.ManualCacheStatsProvider
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import java.time.Instant

@GeoviiteService
class RoutingService(
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val trackService: LocationTrackService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val publicationDao: PublicationDao,
) : ManualCacheStatsProvider {
    data class GraphCacheKey(val context: LayoutContext, val changeTime: Instant)

    private val graphCache: Cache<GraphCacheKey, RoutingGraph> =
        Caffeine.newBuilder().maximumSize(20).expireAfterAccess(layoutCacheDuration).recordStats().build()

    override fun cacheStats() = mapOf("routing-graph" to graphCache.stats())

    fun getClosestTrackPoint(context: LayoutContext, location: Point, maxDistance: Double): ClosestTrackPoint? =
        getClosestTrack(contextCacheKey(context), location, maxDistance)?.let { hit ->
            toClosestTrackPoint(location, hit)
        }

    fun getGraph(branch: LayoutBranch, moment: Instant): RoutingGraph = getGraph(GraphCacheKey(branch.official, moment))

    fun getGraph(context: LayoutContext): RoutingGraph = getGraph(contextCacheKey(context))

    private fun contextCacheKey(context: LayoutContext): GraphCacheKey {
        val changeTime =
            when (context.state) {
                OFFICIAL -> publicationDao.fetchLatestPublicationTime(context.branch) ?: Instant.EPOCH
                DRAFT -> maxOf(locationTrackDao.fetchChangeTime(), switchDao.fetchChangeTime())
            }
        return GraphCacheKey(context, changeTime)
    }

    private fun getGraph(key: GraphCacheKey): RoutingGraph = graphCache.get(key, ::createGraph)

    private fun createGraph(key: GraphCacheKey): RoutingGraph {
        val branch = key.context.branch
        val moment = key.changeTime
        val tracksAndGeoms =
            when (key.context.state) {
                OFFICIAL -> trackService.listOfficialWithGeometryAtMoment(branch, moment, includeDeleted = false)
                DRAFT -> trackService.listWithGeometries(key.context, includeDeleted = false)
            }
        val switches =
            when (key.context.state) {
                OFFICIAL -> switchDao.listOfficialAtMoment(branch, moment).filter { it.exists }
                DRAFT -> switchDao.list(key.context, includeDeleted = false)
            }
        return buildGraph(tracksAndGeoms.map { (_, g) -> g }, switches, switchLibraryService.getSwitchStructuresById())
    }

    fun getRoute(
        context: LayoutContext,
        startLocation: Point,
        endLocation: Point,
        trackSeekDistance: Double,
    ): RouteResult? = getRoute(contextCacheKey(context), startLocation, endLocation, trackSeekDistance)

    fun getRoute(
        branch: LayoutBranch,
        moment: Instant,
        startLocation: Point,
        endLocation: Point,
        trackSeekDistance: Double,
    ): RouteResult? = getRoute(GraphCacheKey(branch.official, moment), startLocation, endLocation, trackSeekDistance)

    private fun getRoute(
        key: GraphCacheKey,
        startLocation: Point,
        endLocation: Point,
        trackSeekDistance: Double,
    ): RouteResult? {
        val graph = getGraph(key)
        val startTrackHit = getClosestTrack(key, startLocation, trackSeekDistance)
        val endTrackHit = getClosestTrack(key, endLocation, trackSeekDistance)
        return if (startTrackHit != null && endTrackHit != null) {
            graph.findPath(startTrackHit, endTrackHit)?.let { route ->
                RouteResult(
                    startConnection = toClosestTrackPoint(startLocation, startTrackHit),
                    endConnection = toClosestTrackPoint(endLocation, endTrackHit),
                    route = route,
                )
            }
        } else {
            null
        }
    }

    private fun getClosestTrack(key: GraphCacheKey, location: Point, thresholdMeters: Double): PointNearTrack? {
        val bbox = boundingBoxAroundPoint(location, thresholdMeters)
        val versions =
            when (key.context.state) {
                OFFICIAL -> locationTrackDao.fetchOfficialVersionsNearAtMoment(key.context.branch, bbox, key.changeTime)
                DRAFT -> locationTrackDao.fetchVersionsNear(key.context, bbox)
            }
        return versions.mapNotNull { version -> createHit(version, location, thresholdMeters) }.minOrNull()
    }

    private fun createHit(
        version: LayoutRowVersion<LocationTrack>,
        location: Point,
        thresholdMeters: Double,
    ): PointNearTrack? {
        val geometry = alignmentDao.fetch(version)
        return geometry.getClosestPoint(location)?.let { (closestPoint, _) ->
            val distance = lineLength(location, closestPoint)
            if (distance < thresholdMeters) {
                PointNearTrack(locationTrackDao.fetch(version), geometry, closestPoint, distance)
            } else {
                null
            }
        }
    }
}

private fun toClosestTrackPoint(requestedPoint: Point, hit: PointNearTrack): ClosestTrackPoint =
    ClosestTrackPoint(
        locationTrackId = hit.track.id as IntId<LocationTrack>,
        requestedLocation = requestedPoint,
        trackLocation = hit.closestPoint,
        distance = hit.distance,
    )
