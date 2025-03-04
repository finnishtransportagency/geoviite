package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point

@GeoviiteService
class LayoutGraphService(private val locationTrackService: LocationTrackService, private val routingDao: RoutingDao) {

    fun getGraph(context: LayoutContext, detailLevel: DetailLevel, bbox: BoundingBox): LayoutGraph {
        // The simplification doesn't work correctly if we just take the edges near the bbox
        // Instead, we must take all edges from all geometries near it
        val edgeData =
            locationTrackService
                .listNearWithGeometries(context, bbox)
                .flatMap { (track, geom) -> geom.edges.map { e -> e to (track.id as IntId) } }
                .groupBy({ it.first }, { it.second })
                .map { (e, tracks) -> DbEdgeData(e, tracks.toSet()) }
        return LayoutGraph.of(context, detailLevel, edgeData)
    }

    fun getRoute(start: Point, end: Point): Route = routingDao.getRoute(start, end)
}
