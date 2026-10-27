package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService

@GeoviiteService
class LayoutGraphService(private val locationTrackService: LocationTrackService) {

    fun getGraph(context: LayoutContext, detailLevel: DetailLevel): LayoutGraph =
        createGraph(locationTrackService.listWithGeometries(context), detailLevel)

    fun getGraph(context: LayoutContext, detailLevel: DetailLevel, bbox: BoundingBox): LayoutGraph =
        createGraph(locationTrackService.listNearWithGeometries(context, bbox), detailLevel)
}

private fun createGraph(
    tracks: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    detailLevel: DetailLevel,
): LayoutGraph {
    val edgeData =
        tracks
            .flatMap { (track, geom) -> geom.edgesWithM.map { (e, m) -> e to TrackSection(track.id as IntId, m) } }
            .groupBy { it.first.id }
            .map { (_, edgesAndTrackIds) ->
                DbEdgeData(edgesAndTrackIds[0].first, edgesAndTrackIds.map { it.second }.toSet())
            }
    return LayoutGraph.of(detailLevel, edgeData)
}
