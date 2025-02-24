package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.DetailLevel.MICRO
import fi.fta.geoviite.infra.tracklayout.DetailLevel.NANO

enum class DetailLevel {
    NANO,
    MICRO,
}

data class LayoutGraph
private constructor(
    val context: LayoutContext,
    val detailLevel: DetailLevel,
    val nodes: Map<IntId<LayoutNode>, LayoutGraphNode>,
    val edges: Map<IntId<LayoutEdge>, LayoutGraphEdge>,
) {
    private constructor(
        context: LayoutContext,
        detailLevel: DetailLevel,
        edges: List<GraphEdgeData>,
    ) : this(context, detailLevel, creteGraphNodes(edges), createGraphEdges(edges))

    companion object {
        fun of(context: LayoutContext, detailLevel: DetailLevel, edges: List<GraphEdgeData>): LayoutGraph =
            when (detailLevel) {
                NANO -> LayoutGraph(context, NANO, edges)
                MICRO -> LayoutGraph(context, MICRO, simplifyEdgesToMicro(edges))
            }
    }
}

private fun creteGraphNodes(edges: List<GraphEdgeData>): Map<IntId<LayoutNode>, LayoutGraphNode> =
    edges
        .flatMap { (edge, _) ->
            listOf(LayoutGraphNode(edge.startNode, edge.start), LayoutGraphNode(edge.endNode, edge.end))
        }
        .associateBy { it.id }

private fun createGraphEdges(edges: List<GraphEdgeData>): Map<IntId<LayoutEdge>, LayoutGraphEdge> =
    edges.associate { (edge, tracks) -> edge.id to LayoutGraphEdge(edge, tracks) }

data class LayoutGraphEdge(
    val id: IntId<LayoutEdge>,
    val startNode: IntId<LayoutNode>,
    val endNode: IntId<LayoutNode>,
    val length: Double,
    val tracks: List<IntId<LocationTrack>>,
) {
    constructor(
        edge: DbLayoutEdge,
        tracks: List<IntId<LocationTrack>>,
    ) : this(edge.id, edge.startNode.id, edge.endNode.id, edge.length, tracks)
}

data class LayoutGraphNode(
    val id: IntId<LayoutNode>,
    val type: LayoutNodeType,
    val detailLevel: DetailLevel,
    val switches: List<SwitchLink>,
    val location: Point,
) {
    constructor(
        node: DbEdgeNode,
        location: IPoint,
    ) : this(node.id, node.type, node.detailLevel, listOfNotNull(node.switchIn, node.switchOut), location.toPoint())
}

private fun simplifyEdgesToMicro(nanoEdges: List<GraphEdgeData>): List<GraphEdgeData> {
    val startingEdges = nanoEdges.groupBy { e -> e.edge.startNode.id }
    val endingEdges = nanoEdges.groupBy { e -> e.edge.endNode.id }
    val handledEdges = mutableSetOf<IntId<LayoutEdge>>()
    return nanoEdges.mapNotNull { nanoEdge ->
        if (handledEdges.contains(nanoEdge.edge.id)) {
            null
        } else {
            val leadingToCombine = collectLeadingEdgesToCombine(startingEdges, endingEdges, nanoEdge)
            val trailingToCombine = collectTrailingEdgesToCombine(startingEdges, endingEdges, nanoEdge)
            (leadingToCombine + nanoEdge + trailingToCombine).let { all ->
                handledEdges.addAll(all.map { e -> e.edge.id })
                if (all.size == 1) all.first() else combinedEdge(all)
            }
        }
    }
}

private fun combinedEdge(nanoEdges: List<GraphEdgeData>): GraphEdgeData =
    GraphEdgeData(
        DbLayoutEdge(
            id = IntId(0), // TODO : micro-level edge ids?
            startNode = nanoEdges.first().edge.startNode,
            endNode = nanoEdges.last().edge.endNode,
            segments = nanoEdges.flatMap { e -> e.edge.segments },
        ),
        nanoEdges.first().tracks,
    )

private fun collectLeadingEdgesToCombine(
    startingEdges: Map<IntId<LayoutNode>, List<GraphEdgeData>>,
    endingEdges: Map<IntId<LayoutNode>, List<GraphEdgeData>>,
    nanoEdge: GraphEdgeData,
): List<GraphEdgeData> =
    generateSequence(nanoEdge) { e ->
            // If the node type makes it a micro-level node, we cant simplify over it
            if (e.edge.startNode.detailLevel == DetailLevel.MICRO) {
                null
            } else {
                val starting = startingEdges[e.edge.startNode.id]
                val ending = endingEdges[e.edge.startNode.id]
                // It only makes sense to combine edges if the node does not branch in either direction
                if (starting != listOf(e) || ending?.size != 1 || ending.first().tracks != e.tracks) null
                else ending.first()
            }
        }
        .toList()
        .reversed()

private fun collectTrailingEdgesToCombine(
    startingEdges: Map<IntId<LayoutNode>, List<GraphEdgeData>>,
    endingEdges: Map<IntId<LayoutNode>, List<GraphEdgeData>>,
    nanoEdge: GraphEdgeData,
): List<GraphEdgeData> =
    generateSequence(nanoEdge) { e ->
            // If the node type makes it a micro-level node, we cant simplify over it
            if (e.edge.endNode.detailLevel == DetailLevel.MICRO) {
                null
            } else {
                val starting = startingEdges[e.edge.endNode.id]
                val ending = endingEdges[e.edge.endNode.id]
                // It only makes sense to combine edges if the node does not branch in either direction
                if (ending != listOf(e) || starting?.size != 1) null else starting.first()
            }
        }
        .toList()
