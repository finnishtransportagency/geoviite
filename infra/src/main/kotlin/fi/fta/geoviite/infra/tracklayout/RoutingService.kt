package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService

@GeoviiteService
class RoutingService(
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
) {

    fun getClosestTrackPoint(context: LayoutContext, location: Point, maxDistance: Double): ClosestTrackPoint? =
        locationTrackSpatialCache.get(context).getClosest(location, maxDistance).firstOrNull()?.let { hit ->
            toClosestTrackPoint(location, hit)
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
            val graph = buildGraph(
                trackGeoms = locationTrackService.listWithGeometries(context, includeDeleted = false).map { (_,g) -> g },
                switches = switchService.list(context, includeDeleted = false),
                structures = switchLibraryService.getSwitchStructuresById(),
            )
            graph.findPath(startTrackHit, endTrackHit)?.let { route ->
                toRouteResult(startLocation, startTrackHit, route)
            }
        } else {
            null
        }
    }
}

private fun toRouteResult(requestedPoint: Point, hit: LocationTrackCacheHit, route: Route): RouteResult =
    RouteResult(
        startConnection = toClosestTrackPoint(requestedPoint, hit),
        endConnection = toClosestTrackPoint(requestedPoint, hit),
        route = route,
    )

private fun toClosestTrackPoint(requestedPoint: Point, hit: LocationTrackCacheHit): ClosestTrackPoint =
    ClosestTrackPoint(
        locationTrackId = hit.track.id as IntId<LocationTrack>,
        requestedLocation = requestedPoint,
        trackLocation = hit.closestPoint,
        distance = hit.distance,
    )
