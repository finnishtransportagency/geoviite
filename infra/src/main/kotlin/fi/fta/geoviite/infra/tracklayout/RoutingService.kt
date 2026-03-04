package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import java.time.Instant

@GeoviiteService
class RoutingService(
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
) {
    private data class GraphCacheKey(val context: LayoutContext, val changeTimes: List<Instant>)

    private val graphCache: Cache<GraphCacheKey, RoutingGraph> =
        Caffeine.newBuilder().maximumSize(10).expireAfterAccess(layoutCacheDuration).build()

    fun getClosestTrackPoint(context: LayoutContext, location: Point, maxDistance: Double): ClosestTrackPoint? =
        locationTrackSpatialCache.get(context).getClosest(location, maxDistance).firstOrNull()?.let { hit ->
            toClosestTrackPoint(location, hit)
        }

    private fun getGraph(context: LayoutContext): RoutingGraph {
        val cacheKey =
            GraphCacheKey(
                context = context,
                changeTimes = listOf(locationTrackDao.fetchChangeTime(), switchDao.fetchChangeTime()),
            )
        return graphCache.get(cacheKey) { key ->
            buildGraph(
                trackGeoms =
                    locationTrackService.listWithGeometries(key.context, includeDeleted = false).map { (_, g) -> g },
                switches = switchService.list(key.context, includeDeleted = false),
                structures = switchLibraryService.getSwitchStructuresById(),
            )
        }
    }

    fun getRoute(
        context: LayoutContext,
        startLocation: Point,
        endLocation: Point,
        trackSeekDistance: Double,
    ): RouteResult? {
        val trackCache = locationTrackSpatialCache.get(context)
        val startTrackHit = trackCache.getClosest(startLocation, trackSeekDistance).firstOrNull()
        val endTrackHit = trackCache.getClosest(endLocation, trackSeekDistance).firstOrNull()
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
