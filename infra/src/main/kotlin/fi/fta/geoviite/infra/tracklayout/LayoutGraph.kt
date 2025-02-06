package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point

data class LayoutGraph(
    val context: LayoutContext,
    val nodes: Map<IntId<LayoutNode>, LayoutGraphNode>,
    val edges: Map<IntId<LayoutEdge>, LayoutGraphEdge>,
) {
    constructor(
        context: LayoutContext,
        edges: List<Pair<LayoutEdge, List<IntId<LocationTrack>>>>,
    ) : this(context, creteGraphNodes(edges.map { (e, _) -> e }), createGraphEdges(edges))
}

private fun creteGraphNodes(edges: List<LayoutEdge>): Map<IntId<LayoutNode>, LayoutGraphNode> =
    edges
        .flatMap { edge ->
            listOf(LayoutGraphNode(edge.startNode, edge.start), LayoutGraphNode(edge.endNode, edge.end))
        }
        .associateBy { it.id }

private fun createGraphEdges(
    edges: List<Pair<LayoutEdge, List<IntId<LocationTrack>>>>
): Map<IntId<LayoutEdge>, LayoutGraphEdge> =
    edges.associate { (edge, tracks) -> edge.id to LayoutGraphEdge(edge, tracks) }

data class LayoutGraphEdge(
    val id: IntId<LayoutEdge>,
    val startNode: IntId<LayoutNode>,
    val endNode: IntId<LayoutNode>,
    val length: Double,
    val tracks: List<IntId<LocationTrack>>,
) {
    constructor(
        edge: LayoutEdge,
        tracks: List<IntId<LocationTrack>>,
    ) : this(edge.id, edge.startNode.id, edge.endNode.id, edge.length, tracks)
}

data class LayoutGraphNode(
    val id: IntId<LayoutNode>,
    val type: LayoutNodeType,
    val switches: List<SwitchLink>,
    val location: Point,
) {
    constructor(
        node: LayoutNode,
        location: IPoint,
    ) : this(node.id, node.nodeType, listOfNotNull(node.switchIn, node.switchOut), location.toPoint())
}
