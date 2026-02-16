package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
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
    abstract val startNode: DbNodeConnection
    abstract val endNode: DbNodeConnection
    abstract val tracks: Set<IntId<LocationTrack>>
    abstract val length: Double
    abstract val start: Point
    abstract val end: Point
}

data class DbEdgeData(val edge: DbLayoutEdge, override val tracks: Set<IntId<LocationTrack>>) : GraphEdgeData() {
    override val id: DomainId<LayoutEdge>
        get() = edge.id

    override val startNode: DbNodeConnection
        get() = edge.startNode

    override val endNode: DbNodeConnection
        get() = edge.endNode

    override val length: Double
        get() = edge.length.distance

    override val start: Point by lazy { edge.start.toPoint() }
    override val end: Point by lazy { edge.end.toPoint() }
}

data class SimplifiedEdgeData(val edges: List<DbEdgeData>) : GraphEdgeData() {
    override val id: DomainId<LayoutEdge> by lazy { StringId("M_${Objects.hash(edges.map { e -> e.id })}") }

    override val startNode: DbNodeConnection
        get() = edges.first().startNode

    override val endNode: DbNodeConnection
        get() = edges.last().endNode

    override val tracks: Set<IntId<LocationTrack>>
        get() = edges.flatMap { e -> e.tracks }.toSet()

    override val length: Double by lazy { edges.sumOf { e -> e.length } }

    override val start: Point
        get() = edges.first().start

    override val end: Point
        get() = edges.last().end

    init {
        require(edges.isNotEmpty())
        require(edges.zipWithNext().all { (prev, next) -> next.startNode.node == prev.endNode.node })
    }
}

data class LayoutGraph
private constructor(
    val detailLevel: DetailLevel,
    val nodes: Map<IntId<LayoutNode>, LayoutGraphNode>,
    val edges: Map<DomainId<LayoutEdge>, LayoutGraphEdge>,
) {
    val key = Objects.hash(nodes.keys, edges.keys)

    private constructor(
        detailLevel: DetailLevel,
        edges: List<GraphEdgeData>,
    ) : this(detailLevel, creteGraphNodes(edges), createGraphEdges(edges))

    companion object {
        fun of(detailLevel: DetailLevel, edges: List<DbEdgeData>): LayoutGraph =
            when (detailLevel) {
                NANO -> LayoutGraph(NANO, edges)
                MICRO -> LayoutGraph(MICRO, simplifyEdgesToMicro(edges))
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
    val tracks: Set<IntId<LocationTrack>>,
) {
    constructor(edge: GraphEdgeData) : this(edge.id, edge.startNode.id, edge.endNode.id, edge.length, edge.tracks)

    fun flip() = LayoutGraphEdge(flipId(id), endNode, startNode, length, tracks)
}

private fun <T> flipId(id: DomainId<T>): DomainId<T> =
    when (id) {
        is IntId -> StringId("R_${id.intValue}")
        is StringId -> StringId("R_${id.stringValue.also { require(!it.startsWith("R_")) } }}")
        else -> throw IllegalArgumentException("Unsupported id type for flipping: ${id::class}")
    }

data class LayoutGraphNode(
    val id: IntId<LayoutNode>,
    val type: LayoutNodeType,
    val detailLevel: DetailLevel,
    val switches: List<SwitchLink>,
    val location: Point,
) {
    constructor(
        node: DbNodeConnection,
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
            if (e.edge.startNode.detailLevel == MICRO) {
                null
            } else {
                val starting = startingEdges[e.edge.startNode.id]
                val ending = endingEdges[e.edge.startNode.id]
                // It only makes sense to combine edges if the node does not branch in either direction
                ending?.firstOrNull()?.takeUnless { starting != listOf(e) || ending.size != 1 }
                // It would be possible here to prevent edge combining if the tracks are not the same.
                // This results a partial nano/micro mix, but the resulting edges always link fully to the tracks.
                // This version creates a more pure micro-level graph, but the edge tracks may not actually be
                // end-to-end
                // ?.takeIf { prev -> prev.tracks == e.tracks }
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
            if (e.edge.endNode.detailLevel == MICRO) {
                null
            } else {
                val starting = startingEdges[e.edge.endNode.id]
                val ending = endingEdges[e.edge.endNode.id]
                // It only makes sense to combine edges if the node does not branch in either direction
                starting?.firstOrNull()?.takeUnless { ending != listOf(e) || starting.size != 1 }
                // It would be possible here to prevent edge combining if the tracks are not the same.
                // This results a partial nano/micro mix, but the resulting edges always link fully to the tracks.
                // This version creates a more pure micro-level graph, but the edge tracks may not actually be
                // end-to-end
                // ?.takeIf { next -> next.tracks == e.tracks }
            }
        }
        .drop(1)
        .toList()
