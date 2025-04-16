package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.DbLayoutNode
import fi.fta.geoviite.infra.tracklayout.EmptyPort
import fi.fta.geoviite.infra.tracklayout.LayoutNode
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.SWITCH
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_BOUNDARY
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.NodeHash
import fi.fta.geoviite.infra.tracklayout.NodePort
import fi.fta.geoviite.infra.tracklayout.PlaceholderNode
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TrackBoundary

data class NodeCombinations(
    val replacements: Map<NodeHash, LayoutNode>,
    val targetTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
)

data class NodeReplacementTarget(val node: LayoutNode, val tracks: List<Pair<LocationTrack, LocationTrackGeometry>>) {
    constructor(
        node: LayoutNode,
        track: LocationTrack,
        geometry: LocationTrackGeometry,
    ) : this(node, listOf(track to geometry))

    init {
        require(node !is PlaceholderNode) {
            "${PlaceholderNode::class.simpleName} cannot be joined with other nodes. Set geometry track ID first."
        }
    }

    fun contains(trackId: DomainId<LocationTrack>): Boolean = tracks.any { (track, _) -> track.id == trackId }
}

data class DbNodeConnection(val node: DbLayoutNode, val trackVersions: List<LayoutRowVersion<LocationTrack>>) {
    fun filterOut(trackIds: Set<IntId<LocationTrack>>): DbNodeConnection? {
        val newTrackVersions = trackVersions.filter { (trackId, _) -> !trackIds.contains(trackId.id) }
        return when {
            newTrackVersions.isEmpty() -> null
            newTrackVersions.size == trackVersions.size -> this
            else -> this.copy(trackVersions = newTrackVersions)
        }
    }
}

fun mergeNodeConnections(connections: List<NodeReplacementTarget>): List<NodeReplacementTarget> =
    connections
        .groupBy { c -> c.node.contentHash }
        .map { (_, sameNodeConnections) ->
            NodeReplacementTarget(
                node = sameNodeConnections.first().node,
                tracks = sameNodeConnections.flatMap { c -> c.tracks }.distinctBy { (t, _) -> t.id },
            )
        }

fun resolveNodeCombinations(connections: List<NodeReplacementTarget>): NodeCombinations {
    val replacements = combineEligibleNodes(connections.map { c -> c.node })
    val replaced = connections.mapNotNull { c -> replacements[c.node]?.let { replacement -> c to replacement } }
    return NodeCombinations(
        replaced.associate { (connection, replacement) -> connection.node.contentHash to replacement },
        replaced.flatMap { (connection, _) -> connection.tracks }.distinctBy { (t, _) -> t.id },
    )
}

fun mergeNodeCombinations(combinations: List<NodeCombinations>): NodeCombinations {
    val merged =
        buildMap<NodeHash, LayoutNode> {
            combinations
                .flatMap { r -> r.replacements.entries }
                .forEach { (target: NodeHash, replacement: LayoutNode) ->
                    compute(target) { _, prev ->
                        prev?.also {
                            require(prev == replacement) {
                                "Ambiguous node replacement: target=$target opt1=$prev opt2=$replacement"
                            }
                        } ?: replacement
                    }
                }
        }
    val tracks = combinations.flatMap { r -> r.targetTracks }.distinctBy { (track, _) -> track.id }
    return NodeCombinations(merged, tracks)
}

private val nodeCombinationPriority =
    Comparator<LayoutNode> { o1, o2 ->
        o1.type.ordinal.compareTo(o2.type.ordinal).takeIf { it != 0 }
            ?: (-(o1.ports.size.compareTo(o2.ports.size))).takeIf { it != 0 }
            ?: comparePorts(o1.portA, o2.portA).takeIf { it != 0 }
            ?: comparePorts(o1.portB, o2.portB)
    }

private fun comparePorts(port1: NodePort?, port2: NodePort?): Int =
    when {
        port1 == null && port2 == null -> 0
        port1 == null -> 1
        port2 == null -> -1
        port1.isBefore(port2) -> -1
        port2.isBefore(port1) -> 1
        else -> 0
    }

fun combineEligibleNodes(nodes: List<LayoutNode>): Map<LayoutNode, LayoutNode> {
    // Match targets in priority order for deterministic results in case multiple combinations are possible
    val targets = nodes.toSortedSet(nodeCombinationPriority)
    return nodes
        // Do the combinations in priority order to produce any high-priority combination nodes for further attempts
        .sortedWith(nodeCombinationPriority)
        // Double-port nodes cannot be further connected
        .filter { node -> node.ports.size == 1 }
        // Combine with the first eligible option
        .mapNotNull { node ->
            targets
                // Since we're combining a single-port node -> there can only be port A
                .firstNotNullOfOrNull { other -> tryToCombinePortToNode(node.portA, other) }
                // If the best match is a combination-switch node, there's multiple switches to connect to
                // For a track boundary, we couldn't know which one to use -> don't connect at all
                ?.takeIf { newNode -> node.type != TRACK_BOUNDARY || newNode.type != SWITCH || newNode.portB == null }
                ?.also(targets::add)
                ?.let(node::to)
        }
        .associate { it }
}

private fun tryToCombinePortToNode(ownPort: NodePort, otherNode: LayoutNode): LayoutNode? =
    when (ownPort) {
        // Switch link can be connected to any other switch link that is either:
        // * A single-switch node with a link to a different switch
        // * A double-switch node where one of the links is this one (switch & joint)
        // Note: this might create multiple tmp-nodes for the same link-combination, but they will be joined upon saving
        is SwitchLink -> {
            val otherA = otherNode.portA as? SwitchLink
            val otherB = otherNode.portB as? SwitchLink
            when {
                otherA == null -> null
                otherB == null -> otherA.takeIf { it.id != ownPort.id }?.let { LayoutNode.of(ownPort, it) }
                else -> otherNode.takeIf { otherA == ownPort || otherB == ownPort }
            }
        }
        // Track boundary can be connected to any switch link node or an existing boundary-combo
        is TrackBoundary ->
            otherNode.takeIf { it.type == SWITCH || (it.ports.size == 2 && it.containsBoundary(ownPort)) }
        // Empty ports don't have sufficient information for combination.
        // PlaceHolder nodes must be reified to TrackBoundary with an ID first
        is EmptyPort -> error("Empty port cannot be combined")
    }
