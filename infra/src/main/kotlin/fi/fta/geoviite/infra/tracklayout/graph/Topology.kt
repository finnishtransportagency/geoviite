package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.SwitchLink
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
)

/** An edge of the graph, covered by the location tracks whose geometry runs along it. */
data class TopologyEdge
internal constructor(
    val id: UUID,
    val startNode: TopologyNode,
    val endNode: TopologyNode,
    val length: Double,
    val tracks: List<LocationTrack>,
)

/** A node of the graph: either the end of a track or a switch. */
data class TopologyNode
internal constructor(
    val id: UUID,
    val type: LayoutNodeType,
    val location: Point,
    val switches: List<SwitchLink>,
    /** The allowed movements through this node. A movement not listed here is forbidden. */
    val transitions: List<TopologyTransition>,
)

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

    fun addNode(id: UUID, type: LayoutNodeType, location: Point, switches: List<SwitchLink> = emptyList()) {
        require(!nodes.containsKey(id)) { "Topology must not contain duplicate node UUIDs: node=$id" }
        nodes[id] = NodeData(type, location, switches)
    }

    fun addEdge(id: UUID, startNode: UUID, endNode: UUID, length: Double, tracks: List<LocationTrack>) {
        require(tracks.distinctBy { track -> track.id }.size == tracks.size) {
            "Topology edge must not contain duplicate location tracks: edge=$id"
        }
        require(!edges.containsKey(id)) { "Topology must not contain duplicate edge UUIDs: edge=$id" }
        edges[id] = EdgeData(startNode, endNode, length, Collections.unmodifiableList(tracks.toList()))
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

    internal fun build(): Topology {
        val builtNodes = nodes.map { (id, data) ->
            TopologyNode(
                id = id,
                type = data.type,
                location = data.location,
                switches = data.switches,
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
                tracks = data.tracks,
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
        val switches: List<SwitchLink>,
        val transitions: MutableList<TopologyTransition> = mutableListOf(),
    )

    private data class EdgeData(
        val startNode: UUID,
        val endNode: UUID,
        val length: Double,
        val tracks: List<LocationTrack>,
    )

    private data class TransitionData(
        val node: UUID,
        val incomingEdge: UUID,
        val incomingDirection: TopologyDirection,
        val outgoingEdge: UUID,
        val outgoingDirection: TopologyDirection,
    )
}
