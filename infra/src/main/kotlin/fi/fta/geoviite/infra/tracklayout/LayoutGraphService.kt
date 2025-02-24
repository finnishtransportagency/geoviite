package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.BoundingBox

@GeoviiteService
class LayoutGraphService(private val dao: LayoutAlignmentDao) {
    fun getGraph(context: LayoutContext, detailLevel: DetailLevel, bbox: BoundingBox): LayoutGraph {
        return LayoutGraph.of(context, detailLevel, dao.getActiveContextEdges(context, bbox))
    }
}
