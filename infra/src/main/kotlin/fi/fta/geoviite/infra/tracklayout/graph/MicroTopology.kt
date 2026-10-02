package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.tracklayout.LayoutNodeType
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import java.util.UUID

/**
 * Simplifies the nano level topology into the micro level one, where a switch is a single node rather than a set of
 * joint nodes (GVT-3703).
 *
 * The simplification is deterministic and does not change the nano level topology:
 * 1. A node survives if it is a track boundary node or if it has at least one MAIN role switch joint. A node that would
 *    otherwise be dropped is still kept if its surrounding edges cannot be combined into a single one, that is, if it
 *    does not have exactly two incident ends of two different edges.
 * 2. The edges around the dropped nodes are combined into micro edges, whose length is the sum of the combined lengths
 *    and whose track list is the union of the combined track lists.
 * 3. The transitions of the surviving nodes are copied as-is, with the nano edge references replaced by the micro edges
 *    that contain them, converted to the canonical direction of the micro edge.
 */
internal fun simplifyToMicroLevel(nano: Topology): Topology {
    val incidentEdges = collectIncidentEdges(nano)
    val microNodeIds =
        nano.nodes.filter { node -> isMicroNode(node, incidentEdges[node.id].orEmpty()) }.map(TopologyNode::id).toSet()
    val microEdges = collectChains(nano, microNodeIds, incidentEdges).map(::toMicroEdge)
    // A chain can form a closed loop of droppable nodes: its ends must be kept, or the edge would have no nodes.
    val keptNodeIds = microNodeIds + microEdges.flatMap { edge -> listOf(edge.startNode, edge.endNode) }
    val edgeRefs =
        microEdges
            .flatMap { micro -> micro.chain.map { link -> link.edge.id to MicroEdgeRef(micro.id, link.forward) } }
            .toMap()

    return buildTopology {
        nano.nodes
            .filter { node -> node.id in keptNodeIds }
            .forEach { node -> addNode(node.id, node.type, node.location, microSwitchLinks(node.switches)) }
        microEdges.forEach { edge ->
            addEdge(
                id = edge.id,
                startNode = edge.startNode,
                endNode = edge.endNode,
                length = edge.length,
                tracks = edge.tracks,
            )
        }
        nano.nodes
            .filter { node -> node.id in keptNodeIds }
            .forEach { node ->
                node.transitions.forEach { transition ->
                    val incoming = microTraversal(transition.incomingEdge, edgeRefs)
                    val outgoing = microTraversal(transition.outgoingEdge, edgeRefs)
                    addTransition(
                        node = node.id,
                        incomingEdge = incoming.edgeId,
                        incomingDirection = incoming.direction,
                        outgoingEdge = outgoing.edgeId,
                        outgoingDirection = outgoing.direction,
                    )
                }
            }
    }
}

private fun collectIncidentEdges(nano: Topology): Map<UUID, List<TopologyEdge>> =
    nano.edges
        .flatMap { edge -> listOf(edge.startNode.id to edge, edge.endNode.id to edge) }
        .groupBy({ (nodeId, _) -> nodeId }, { (_, edge) -> edge })

/**
 * Micro level nodes are the track boundaries and the switch presentation points. Other nodes are kept only if their
 * surroundings cannot be combined into a single edge: the edges of a dropped node are always replaced by exactly one
 * combined edge.
 */
private fun isMicroNode(node: TopologyNode, incidentEdges: List<TopologyEdge>): Boolean =
    node.type == LayoutNodeType.TRACK_BOUNDARY ||
        node.switches.any { link -> link.jointRole == SwitchJointRole.MAIN } ||
        incidentEdges.size != 2 ||
        incidentEdges[0].id == incidentEdges[1].id

/** Each switch is listed once, described by its MAIN role joint link if the node has one. */
private fun microSwitchLinks(links: List<SwitchLink>): List<SwitchLink> =
    links.groupBy(SwitchLink::id).map { (_, switchLinks) ->
        switchLinks.firstOrNull { link -> link.jointRole == SwitchJointRole.MAIN } ?: switchLinks.first()
    }

/** Splits the nano edges into the chains of edges that each become a single micro edge. */
private fun collectChains(
    nano: Topology,
    microNodeIds: Set<UUID>,
    incidentEdges: Map<UUID, List<TopologyEdge>>,
): List<List<ChainLink>> {
    val handled = mutableSetOf<UUID>()
    return nano.edges.mapNotNull { edge ->
        if (edge.id in handled) {
            null
        } else {
            canonicalChain(collectChain(edge, microNodeIds, incidentEdges)).also { chain ->
                chain.forEach { link -> handled.add(link.edge.id) }
            }
        }
    }
}

private fun collectChain(
    seed: TopologyEdge,
    microNodeIds: Set<UUID>,
    incidentEdges: Map<UUID, List<TopologyEdge>>,
): List<ChainLink> {
    val chain = ArrayDeque(listOf(ChainLink(seed, forward = true)))
    val included = mutableSetOf(seed.id)
    generateSequence { nextInChain(chain.last(), microNodeIds, incidentEdges, included) }
        .forEach { link ->
            chain.addLast(link)
            included.add(link.edge.id)
        }
    generateSequence { nextInChain(chain.first().reversed(), microNodeIds, incidentEdges, included) }
        .forEach { link ->
            chain.addFirst(link.reversed())
            included.add(link.edge.id)
        }
    return chain.toList()
}

/** The edge that continues the chain through the end node of the given link, if that node is dropped. */
private fun nextInChain(
    current: ChainLink,
    microNodeIds: Set<UUID>,
    incidentEdges: Map<UUID, List<TopologyEdge>>,
    included: Set<UUID>,
): ChainLink? {
    val node = current.endNode
    if (node.id in microNodeIds) return null
    val next = incidentEdges.getValue(node.id).singleOrNull { edge -> edge.id != current.edge.id } ?: return null
    return if (next.id in included) null else ChainLink(next, forward = next.startNode.id == node.id)
}

/**
 * Orients the chain into its canonical direction: the direction of the edge that is not switch internal. If there are
 * several such edges or none at all, the chain follows the own ascending direction of the candidate with the
 * lexicographically smallest UUID.
 */
private fun canonicalChain(chain: List<ChainLink>): List<ChainLink> {
    val candidates = chain.filterNot { link -> isSwitchInternal(link.edge) }.ifEmpty { chain }
    val anchor = candidates.minBy { link -> link.edge.id.toString() }
    return if (anchor.forward) chain else chain.reversed().map(ChainLink::reversed)
}

private fun isSwitchInternal(edge: TopologyEdge): Boolean {
    val startSwitches = edge.startNode.switches.mapTo(mutableSetOf(), SwitchLink::id)
    return edge.endNode.switches.any { link -> link.id in startSwitches }
}

private fun toMicroEdge(chain: List<ChainLink>): MicroEdgeData {
    val edges = chain.map(ChainLink::edge)
    return MicroEdgeData(
        id = if (edges.size == 1) edges.first().id else microEdgeUuid(edges.map(TopologyEdge::id)),
        startNode = chain.first().startNode.id,
        endNode = chain.last().endNode.id,
        length = edges.sumOf(TopologyEdge::length),
        tracks = edges.flatMap(TopologyEdge::tracks).distinctBy { track -> track.id },
        chain = chain,
    )
}

private fun microTraversal(traversal: TopologyEdgeTraversal, edgeRefs: Map<UUID, MicroEdgeRef>): MicroTraversalRef {
    val ref = edgeRefs.getValue(traversal.edge.id)
    return MicroTraversalRef(
        edgeId = ref.edgeId,
        direction = if (ref.forward) traversal.direction else traversal.direction.reverse(),
    )
}

/** One nano edge within a chain, travelled either along or against its own ascending direction. */
private data class ChainLink(val edge: TopologyEdge, val forward: Boolean) {
    val startNode: TopologyNode
        get() = if (forward) edge.startNode else edge.endNode

    val endNode: TopologyNode
        get() = if (forward) edge.endNode else edge.startNode

    fun reversed() = ChainLink(edge, !forward)
}

private data class MicroEdgeData(
    val id: UUID,
    val startNode: UUID,
    val endNode: UUID,
    val length: Double,
    val tracks: List<LocationTrack>,
    val chain: List<ChainLink>,
)

/** The micro edge that contains a nano edge, and whether the nano edge runs along the micro edge's own direction. */
private data class MicroEdgeRef(val edgeId: UUID, val forward: Boolean)

private data class MicroTraversalRef(val edgeId: UUID, val direction: TopologyDirection)
