package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.DetailLevel.MICRO
import fi.fta.geoviite.infra.tracklayout.DetailLevel.NANO
import java.util.*

enum class DetailLevel {
    NANO,
    MICRO,
}

sealed class GraphEdgeData {
    abstract val id: DomainId<LayoutEdge>
    abstract val startNode: DbEdgeNode
    abstract val endNode: DbEdgeNode
    abstract val tracks: List<IntId<LocationTrack>>
    abstract val length: Double
    abstract val start: Point
    abstract val end: Point
}

data class DbEdgeData(val edge: DbLayoutEdge, override val tracks: List<IntId<LocationTrack>>) : GraphEdgeData() {
    override val id: DomainId<LayoutEdge>
        get() = edge.id

    override val startNode: DbEdgeNode
        get() = edge.startNode

    override val endNode: DbEdgeNode
        get() = edge.endNode

    override val length: Double
        get() = edge.length

    override val start: Point by lazy { edge.start.toPoint() }
    override val end: Point by lazy { edge.end.toPoint() }
}

data class SimplifiedEdgeData(val edges: List<DbEdgeData>) : GraphEdgeData() {
    override val id: DomainId<LayoutEdge> by lazy { StringId("M_${Objects.hash(edges.map { e -> e.id })}") }

    override val startNode: DbEdgeNode
        get() = edges.first().startNode

    override val endNode: DbEdgeNode
        get() = edges.last().endNode

    override val tracks: List<IntId<LocationTrack>>
        get() = edges.first().tracks

    override val length: Double by lazy { edges.sumOf { e -> e.length } }

    override val start: Point
        get() = edges.first().start

    override val end: Point
        get() = edges.last().end

    init {
        require(edges.isNotEmpty())
        require(edges.all { e -> e.tracks == tracks })
        require(edges.zipWithNext().all { (prev, next) -> next.startNode.node == prev.endNode.node })
    }
}

data class LayoutGraph
private constructor(
    val context: LayoutContext,
    val detailLevel: DetailLevel,
    val nodes: Map<IntId<LayoutNode>, LayoutGraphNode>,
    val edges: Map<DomainId<LayoutEdge>, LayoutGraphEdge>,
) {
    private constructor(
        context: LayoutContext,
        detailLevel: DetailLevel,
        edges: List<GraphEdgeData>,
    ) : this(context, detailLevel, creteGraphNodes(edges), createGraphEdges(edges))

    companion object {
        fun of(context: LayoutContext, detailLevel: DetailLevel, edges: List<DbEdgeData>): LayoutGraph =
            when (detailLevel) {
                NANO -> LayoutGraph(context, NANO, edges)
                MICRO -> LayoutGraph(context, MICRO, simplifyEdgesToMicro(edges))
            }
    }
}

private fun creteGraphNodes(edges: List<GraphEdgeData>): Map<IntId<LayoutNode>, LayoutGraphNode> =
    edges
        .flatMap { e -> listOf(LayoutGraphNode(e.startNode, e.start), LayoutGraphNode(e.endNode, e.end)) }
        .associateBy { it.id }

private fun createGraphEdges(edges: List<GraphEdgeData>): Map<DomainId<LayoutEdge>, LayoutGraphEdge> =
    edges.associate { e -> e.id to LayoutGraphEdge(e) }

data class LayoutGraphEdge(
    val id: DomainId<LayoutEdge>,
    val startNode: IntId<LayoutNode>,
    val endNode: IntId<LayoutNode>,
    val length: Double,
    val tracks: List<IntId<LocationTrack>>,
) {
    constructor(edge: GraphEdgeData) : this(edge.id, edge.startNode.id, edge.endNode.id, edge.length, edge.tracks)
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

private fun simplifyEdgesToMicro(nanoEdges: List<DbEdgeData>): List<GraphEdgeData> {
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
                if (all.size == 1) all.first() else SimplifiedEdgeData(all)
            }
        }
    }
}

private fun collectLeadingEdgesToCombine(
    startingEdges: Map<IntId<LayoutNode>, List<DbEdgeData>>,
    endingEdges: Map<IntId<LayoutNode>, List<DbEdgeData>>,
    nanoEdge: DbEdgeData,
): List<DbEdgeData> =
    generateSequence(nanoEdge) { e ->
            // If the node type makes it a micro-level node, we cant simplify over it
            if (e.edge.startNode.detailLevel == MICRO) {
                null
            } else {
                val starting = startingEdges[e.edge.startNode.id]
                val ending = endingEdges[e.edge.startNode.id]
                ending
                    ?.firstOrNull()
                    // It only makes sense to combine edges if the node does not branch in either direction
                    ?.takeUnless { starting != listOf(e) || ending.size != 1 }
                    // Don't combine edges if they're not covered by the same tracks
                    ?.takeIf { prev -> prev.tracks == e.tracks }
            }
        }
        .drop(1)
        .toList()
        .reversed()

private fun collectTrailingEdgesToCombine(
    startingEdges: Map<IntId<LayoutNode>, List<DbEdgeData>>,
    endingEdges: Map<IntId<LayoutNode>, List<DbEdgeData>>,
    nanoEdge: DbEdgeData,
): List<DbEdgeData> =
    generateSequence(nanoEdge) { e ->
            // If the node type makes it a micro-level node, we cant simplify over it
            if (e.edge.endNode.detailLevel == MICRO) {
                null
            } else {
                val starting = startingEdges[e.edge.endNode.id]
                val ending = endingEdges[e.edge.endNode.id]
                starting
                    ?.firstOrNull()
                    // It only makes sense to combine edges if the node does not branch in either direction
                    ?.takeUnless { ending != listOf(e) || starting.size != 1 }
                    // Don't combine edges if they're not covered by the same tracks
                    ?.takeIf { next -> next.tracks == e.tracks }
            }
        }
        .drop(1)
        .toList()
