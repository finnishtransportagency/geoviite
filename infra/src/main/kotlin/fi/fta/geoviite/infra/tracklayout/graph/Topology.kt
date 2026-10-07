package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.tracklayout.DbLayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.SwitchStructureAlignmentM
import java.util.Collections
import java.util.UUID

/** Level of detail at which the track layout topology is described. */
enum class TopologyDetailLevel {
    NANO,
    MICRO,
}

/** Direction of travel along a topology edge, in terms of the edge's own m-values. */
enum class TopologyDirection {
    ASCENDING,
    DESCENDING;

    fun reverse(): TopologyDirection =
        when (this) {
            ASCENDING -> DESCENDING
            DESCENDING -> ASCENDING
        }
}

/**
 * The topology of a single track layout version: an undirected graph of nodes and edges, with the allowed directed
 * transitions through each node. Edges and transitions refer to their nodes and edges directly, so the graph can be
 * navigated without id lookups.
 *
 * Instances are created with [buildTopology], which resolves the references and validates the graph. A finished
 * topology is immutable and safe to cache and share.
 */
data class Topology
internal constructor(
    val nodes: List<TopologyNode>,
    val edges: List<TopologyEdge>,
    /**
     * Database-edge coverage used to construct routing graphs. Only nano level topologies describe this, as the coarser
     * levels combine database edges and can no longer resolve exact locations along them.
     */
    internal val routingData: TopologyRoutingData,
)

internal data class TopologyRoutingData(val edgesByLayoutEdgeId: Map<IntId<LayoutEdge>, TopologyLayoutEdgeData>)

internal data class TopologyLayoutEdgeData(
    val edge: DbLayoutEdge,
    val tracks: Set<TrackSection>,
    val switchConnections: Map<TopologySwitchAlignment, TopologySwitchSegment>,
)

data class TopologySwitchAlignment(
    val switchId: IntId<LayoutSwitch>,
    val alignment: SwitchStructureAlignment,
)

data class TopologySwitchSegment(
    val edgeId: IntId<LayoutEdge>,
    val direction: TopologyDirection,
    val mRange: Range<LineM<SwitchStructureAlignmentM>>,
)

/** An edge of the graph, covered by the location tracks whose geometry runs along it. */
data class TopologyEdge
internal constructor(
    val id: UUID,
    val startNode: TopologyNode,
    val endNode: TopologyNode,
    val length: Double,
    val trackReferences: List<TopologyLocationTrackReference>,
    val routingReference: TopologyEdgeRoutingReference?,
)

data class TopologyLocationTrackReference(val track: LocationTrack, val oid: Oid<LocationTrack>?)

sealed interface TopologyEdgeRoutingReference

data class LayoutEdgeRoutingReference(val edgeId: IntId<LayoutEdge>) : TopologyEdgeRoutingReference

data class SwitchAlignmentRoutingReference(
    val switchId: IntId<LayoutSwitch>,
    val alignment: SwitchStructureAlignment,
    val forward: Boolean,
) : TopologyEdgeRoutingReference

/** A node of the graph: either the end of a track or a switch. */
data class TopologyNode
internal constructor(
    val id: UUID,
    val type: LayoutNodeType,
    val location: Point,
    val switchReferences: List<TopologySwitchReference>,
    /** The allowed movements through this node. A movement not listed here is forbidden. */
    val transitions: List<TopologyTransition>,
)

data class TopologySwitchReference(val switch: SwitchLink, val oid: Oid<LayoutSwitch>?)

/** A single allowed directed movement through a node: arriving along one edge and continuing along another. */
data class TopologyTransition(val incomingEdge: TopologyEdgeTraversal, val outgoingEdge: TopologyEdgeTraversal)

/** Travel along one edge in one direction. */
data class TopologyEdgeTraversal(val edge: TopologyEdge, val direction: TopologyDirection) {
    val startNode: TopologyNode
        get() = if (direction == TopologyDirection.ASCENDING) edge.startNode else edge.endNode

    val endNode: TopologyNode
        get() = if (direction == TopologyDirection.ASCENDING) edge.endNode else edge.startNode
}

/**
 * Builds an immutable [Topology]. Inside the builder scope the graph is described with UUID references, which are
 * resolved into direct object references when the scope exits. The returned topology is fully resolved and immutable.
 */
fun buildTopology(build: TopologyBuilder.() -> Unit): Topology = TopologyBuilder().apply(build).build()

class TopologyBuilder internal constructor() {

    private val nodes = linkedMapOf<UUID, NodeData>()
    private val edges = linkedMapOf<UUID, EdgeData>()
    private val transitions = mutableListOf<TransitionData>()
    private val layoutEdges = linkedMapOf<IntId<LayoutEdge>, LayoutEdgeData>()
    private val switchSegments = mutableListOf<SwitchSegmentData>()

    fun addNode(
        id: UUID,
        type: LayoutNodeType,
        location: Point,
        switchReferences: List<TopologySwitchReference>,
    ) {
        require(!nodes.containsKey(id)) { "Topology must not contain duplicate node UUIDs: node=$id" }
        nodes[id] =
            NodeData(
                type,
                location,
                Collections.unmodifiableList(switchReferences),
            )
    }

    fun addEdge(
        id: UUID,
        startNode: UUID,
        endNode: UUID,
        length: Double,
        trackReferences: List<TopologyLocationTrackReference>,
        routingReference: TopologyEdgeRoutingReference? = null,
    ) {
        require(!edges.containsKey(id)) { "Topology must not contain duplicate edge UUIDs: edge=$id" }
        edges[id] =
            EdgeData(
                startNode,
                endNode,
                length,
                Collections.unmodifiableList(trackReferences),
                routingReference,
            )
    }

    fun addTransition(
        node: UUID,
        incomingEdge: UUID,
        incomingDirection: TopologyDirection,
        outgoingEdge: UUID,
        outgoingDirection: TopologyDirection,
    ) {
        transitions.add(TransitionData(node, incomingEdge, incomingDirection, outgoingEdge, outgoingDirection))
    }

    fun addLayoutEdgeRouting(edge: DbLayoutEdge, tracks: Set<TrackSection>) {
        require(!layoutEdges.containsKey(edge.id)) {
            "Topology must not contain duplicate routing data for a layout edge: edge=${edge.id}"
        }
        layoutEdges[edge.id] = LayoutEdgeData(edge, Collections.unmodifiableSet(tracks))
    }

    fun addSwitchAlignmentSegment(alignment: TopologySwitchAlignment, segment: TopologySwitchSegment) {
        switchSegments.add(SwitchSegmentData(alignment, segment))
    }

    internal fun build(): Topology {
        val builtNodes = nodes.map { (id, data) ->
            TopologyNode(
                id = id,
                type = data.type,
                location = data.location,
                switchReferences = data.switchReferences,
                transitions = Collections.unmodifiableList(data.transitions),
            )
        }
        val nodesById = builtNodes.associateBy(TopologyNode::id)
        val builtEdges = edges.map { (id, data) ->
            TopologyEdge(
                id = id,
                startNode =
                    requireNotNull(nodesById[data.startNode]) {
                        "Topology edge refers to an unknown start node: edge=$id node=${data.startNode}"
                    },
                endNode =
                    requireNotNull(nodesById[data.endNode]) {
                        "Topology edge refers to an unknown end node: edge=$id node=${data.endNode}"
                    },
                length = data.length,
                trackReferences = data.trackReferences,
                routingReference = data.routingReference,
            )
        }
        val edgesById = builtEdges.associateBy(TopologyEdge::id)
        transitions.groupBy(TransitionData::node).forEach { (nodeId, nodeTransitions) ->
            val node =
                requireNotNull(nodesById[nodeId]) { "Topology transition refers to an unknown node: node=$nodeId" }
            nodes
                .getValue(nodeId)
                .transitions
                .addAll(nodeTransitions.distinct().map { data -> resolve(data, node, edgesById) })
        }
        return Topology(
            nodes = builtNodes,
            edges = builtEdges,
            routingData = buildRoutingData(),
        )
    }

    private fun buildRoutingData(): TopologyRoutingData {
        val segmentsByLayoutEdge =
            switchSegments
                .groupBy { data -> data.segment.edgeId }
                .mapValues { (layoutEdgeId, segments) ->
                    require(layoutEdges.containsKey(layoutEdgeId)) {
                        "Topology switch alignment segment refers to an unknown layout edge: edge=$layoutEdgeId"
                    }
                    segments.associate { data -> data.alignment to data.segment }
                }
        return TopologyRoutingData(
            layoutEdges.mapValues { (layoutEdgeId, data) ->
                TopologyLayoutEdgeData(
                    edge = data.edge,
                    tracks = data.tracks,
                    switchConnections = segmentsByLayoutEdge[layoutEdgeId].orEmpty(),
                )
            }
        )
    }

    private fun resolve(
        data: TransitionData,
        node: TopologyNode,
        edgesById: Map<UUID, TopologyEdge>,
    ): TopologyTransition {
        val incoming = TopologyEdgeTraversal(getEdge(edgesById, data.incomingEdge, node), data.incomingDirection)
        val outgoing = TopologyEdgeTraversal(getEdge(edgesById, data.outgoingEdge, node), data.outgoingDirection)
        require(incoming.endNode === node) {
            "Topology transition must arrive at its own node: node=${node.id} edge=${data.incomingEdge}"
        }
        require(outgoing.startNode === node) {
            "Topology transition must continue from its own node: node=${node.id} edge=${data.outgoingEdge}"
        }
        return TopologyTransition(incomingEdge = incoming, outgoingEdge = outgoing)
    }

    private fun getEdge(edgesById: Map<UUID, TopologyEdge>, id: UUID, node: TopologyNode): TopologyEdge =
        requireNotNull(edgesById[id]) { "Topology transition refers to an unknown edge: node=${node.id} edge=$id" }

    private data class NodeData(
        val type: LayoutNodeType,
        val location: Point,
        val switchReferences: List<TopologySwitchReference>,
        val transitions: MutableList<TopologyTransition> = mutableListOf(),
    )

    private data class EdgeData(
        val startNode: UUID,
        val endNode: UUID,
        val length: Double,
        val trackReferences: List<TopologyLocationTrackReference>,
        val routingReference: TopologyEdgeRoutingReference?,
    )

    private data class TransitionData(
        val node: UUID,
        val incomingEdge: UUID,
        val incomingDirection: TopologyDirection,
        val outgoingEdge: UUID,
        val outgoingDirection: TopologyDirection,
    )

    private data class LayoutEdgeData(val edge: DbLayoutEdge, val tracks: Set<TrackSection>)

    private data class SwitchSegmentData(
        val alignment: TopologySwitchAlignment,
        val segment: TopologySwitchSegment,
    )
}
