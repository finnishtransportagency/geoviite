package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import java.time.Instant

@GeoviiteService
class RoutingService(
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    private val locationTrackDao: LocationTrackDao,
    private val trackService: LocationTrackService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val publicationDao: PublicationDao,
) {
    data class GraphCacheKey(val context: LayoutContext, val changeTime: Instant)

    private val graphCache: Cache<GraphCacheKey, RoutingGraph> =
        Caffeine.newBuilder().maximumSize(20).expireAfterAccess(layoutCacheDuration).build()

    fun getClosestTrackPoint(context: LayoutContext, location: Point, maxDistance: Double): ClosestTrackPoint? =
        locationTrackSpatialCache.get(context).getClosestTrack(location, maxDistance)?.let { hit ->
            toClosestTrackPoint(location, hit)
        }

    fun getGraph(branch: LayoutBranch, moment: Instant): RoutingGraph = getGraph(GraphCacheKey(branch.official, moment))

    fun getGraph(context: LayoutContext): RoutingGraph {
        val changeTime =
            when (context.state) {
                OFFICIAL -> publicationDao.fetchLatestPublicationTime(context.branch) ?: Instant.EPOCH
                DRAFT -> maxOf(locationTrackDao.fetchChangeTime(), switchDao.fetchChangeTime())
            }
        return getGraph(GraphCacheKey(context, changeTime))
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
    ): RouteResult? {
        val trackCache = locationTrackSpatialCache.get(context)
        val startTrackHit = trackCache.getClosestTrack(startLocation, trackSeekDistance)
        val endTrackHit = trackCache.getClosestTrack(endLocation, trackSeekDistance)
        return if (startTrackHit != null && endTrackHit != null) {
            getGraph(context).findPath(startTrackHit, endTrackHit)?.let { route ->
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
}

private fun toClosestTrackPoint(requestedPoint: Point, hit: LocationTrackCacheHit): ClosestTrackPoint =
    ClosestTrackPoint(
        locationTrackId = hit.track.id as IntId<LocationTrack>,
        requestedLocation = requestedPoint,
        trackLocation = hit.closestPoint,
        distance = hit.distance,
    )
