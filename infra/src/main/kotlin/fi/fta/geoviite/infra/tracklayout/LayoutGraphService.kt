package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.BoundingBox

@GeoviiteService
class LayoutGraphService(private val locationTrackService: LocationTrackService) {
    fun getGraph(context: LayoutContext, detailLevel: DetailLevel, bbox: BoundingBox): LayoutGraph {
        // The simplification doesn't work correctly if we just take the edges near the bbox
        // Instead, we must take all edges from all geometries near it
        val edgeData =
            locationTrackService
                .listNearWithGeometries(context, bbox)
                .flatMap { (track, geom) -> geom.edges.map { e -> e to (track.id as IntId) } }
                .groupBy { it.first.id }
                .map { (_, edgesAndTrackIds) ->
                    DbEdgeData(edgesAndTrackIds[0].first, edgesAndTrackIds.map { it.second }.toSet())
                }
        return LayoutGraph.of(context, detailLevel, edgeData)
    }
}
