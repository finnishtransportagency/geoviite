package fi.fta.geoviite.infra.tracklayout.graph

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.NameBasedGenerator
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.configuration.ManualCacheStatsProvider
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.tracklayout.DbLayoutEdge
import fi.fta.geoviite.infra.tracklayout.DbLayoutNode
import fi.fta.geoviite.infra.tracklayout.DbSwitchNode
import fi.fta.geoviite.infra.tracklayout.DbTrackBoundaryNode
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TrackBoundary
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel.MICRO
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel.NANO
import java.util.UUID

private const val TOPOLOGY_CACHE_SIZE = 10L
private val TOPOLOGY_API_ARCS_UUID_GENERATOR: NameBasedGenerator =
    Generators.nameBasedGenerator(UUID.fromString("e1ad9cd7-78d2-5f98-a85e-e07870846250"))

@GeoviiteService
class TopologyService(
    private val locationTrackService: LocationTrackService,
    private val layoutSwitchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
) : ManualCacheStatsProvider {

    private val topologyCache: Cache<Publication, CachedTopology> =
        Caffeine.newBuilder().maximumSize(TOPOLOGY_CACHE_SIZE).recordStats().build()

    override fun cacheStats() = mapOf("layout-topology" to topologyCache.stats())

    fun getTopology(version: Publication, detailLevel: TopologyDetailLevel): Topology =
        topologyCache.get(version, ::createTopology).let { cachedTopology ->
            when (detailLevel) {
                NANO -> cachedTopology.nano
                MICRO -> cachedTopology.micro
            }
        }

    private fun createTopology(version: Publication): CachedTopology = CachedTopology(createNanoTopology(version))

    private fun createNanoTopology(version: Publication): Topology {
        val branch = version.layoutBranch.branch
        val tracks = locationTrackService.listOfficialWithGeometryAtMoment(branch, version.publicationTime)
        val switches =
            layoutSwitchService
                .listOfficialAtMoment(branch, version.publicationTime)
                .filter(LayoutSwitch::exists)
                .associateBy { switch -> switch.id as IntId }
        val structures = switchLibraryService.getSwitchStructuresById()
        val topologySwitchJoints =
            switches.values
                .flatMap { switch ->
                    structures.getValue(switch.switchStructureId).endJointNumbers.map { joint ->
                        SwitchJointKey(switch.id as IntId, joint)
                    }
                }
                .toSet()
        val switchOids =
            switches.values.associate { switch ->
                val switchId = switch.id as IntId<LayoutSwitch>
                switchId to layoutSwitchService.getExternalIdsByBranch(switchId).getValue(branch)
            }
        val edgeOccurrences = tracks.flatMap { (track, geometry) -> geometry.edges.map { edge -> edge to track } }
        val edgeGroups =
            edgeOccurrences
                .groupBy { (edge, _) -> edge.id }
                .values
                .map { occurrences ->
                    NanoEdgeData(
                        edge = occurrences.first().first,
                        tracks = occurrences.map { (_, track) -> track }.distinctBy { track -> track.id },
                    )
                }
        val nodesBySwitchJoint = collectNodesBySwitchJoint(edgeGroups, topologySwitchJoints)
        val switchLines =
            switches.values.flatMap { switch ->
                createSwitchLines(
                    switch = switch,
                    structure = structures.getValue(switch.switchStructureId),
                    oid = switchOids.getValue(switch.id as IntId<LayoutSwitch>),
                    nodesBySwitchJoint = nodesBySwitchJoint,
                    edgeGroups = edgeGroups,
                )
            }
        val regularEdges = edgeGroups.filterNot { (edge) -> edge.isSwitchInnerLink() }
        val nodeOccurrences = edgeGroups.flatMap { (edge) ->
            listOf(
                NodeOccurrence(edge.startNode.node, edge.id, Point(edge.start.x, edge.start.y)),
                NodeOccurrence(edge.endNode.node, edge.id, Point(edge.end.x, edge.end.y)),
            )
        }
        val topologyNodeIds =
            regularEdges
                .flatMap { (edge) -> listOf(nodeUuid(edge.startNode.node), nodeUuid(edge.endNode.node)) }
                .plus(switchLines.flatMap { line -> listOf(line.startNode, line.endNode) })
                .toSet()
        val databaseNodes =
            nodeOccurrences
                .filter { occurrence -> nodeUuid(occurrence.node) in topologyNodeIds }
                .groupBy { occurrence -> occurrence.node.id }
        val endpointConnections = mutableMapOf<UUID, MutableList<EndpointConnection>>()

        return buildTopology {
            databaseNodes.values.forEach { occurrences ->
                val dbNode = occurrences.first().node
                val location = nodeLocation(dbNode, occurrences, switches, topologySwitchJoints)
                addNode(
                    id = nodeUuid(dbNode),
                    type = dbNode.type,
                    location = location,
                    switches =
                        (dbNode as? DbSwitchNode)?.switchLinks?.filter { link ->
                            SwitchJointKey(link.id, link.jointNumber) in topologySwitchJoints
                        } ?: emptyList(),
                )
            }
            regularEdges.forEach { (edge, edgeTracks) ->
                val edgeId = edgeUuid(edge)
                addEdge(
                    id = edgeId,
                    startNode = nodeUuid(edge.startNode.node),
                    endNode = nodeUuid(edge.endNode.node),
                    length = edge.length.distance,
                    tracks = edgeTracks,
                )
                endpointConnections.add(edge.startNode, edgeId, EdgeEndpoint.START)
                endpointConnections.add(edge.endNode, edgeId, EdgeEndpoint.END)
            }
            switchLines.forEach { line ->
                addEdge(
                    id = line.id,
                    startNode = line.startNode,
                    endNode = line.endNode,
                    length = line.alignment.length(),
                    tracks = line.tracks,
                )
                endpointConnections
                    .getOrPut(line.startNode, ::mutableListOf)
                    .add(
                        EndpointConnection(
                            line.id,
                            EdgeEndpoint.START,
                            SwitchSide.Internal(line.startKey),
                        )
                    )
                endpointConnections
                    .getOrPut(line.endNode, ::mutableListOf)
                    .add(
                        EndpointConnection(
                            line.id,
                            EdgeEndpoint.END,
                            SwitchSide.Internal(line.endKey),
                        )
                    )
            }
            databaseNodes.values.forEach { occurrences ->
                val node = occurrences.first().node
                addNodeTransitions(node, endpointConnections[nodeUuid(node)].orEmpty())
            }
        }
    }

    private fun TopologyBuilder.addNodeTransitions(node: DbLayoutNode, connections: List<EndpointConnection>) {
        when (node) {
            is DbTrackBoundaryNode -> {
                if (node.portB == null) {
                    connections.forEach { connection ->
                        addUTurn(nodeUuid(node), connection.edgeId, connection.incomingDirection)
                    }
                } else {
                    addTransitionsBetween(
                        nodeUuid(node),
                        connections,
                    ) { first, second ->
                        val firstBoundary = (first.side as? BoundarySide)?.boundary
                        val secondBoundary = (second.side as? BoundarySide)?.boundary
                        firstBoundary != null && secondBoundary != null && firstBoundary != secondBoundary
                    }
                }
            }
            is DbSwitchNode -> addTransitions(nodeUuid(node), createSwitchTransitions(connections))
        }
    }

    private fun TopologyBuilder.addTransitionsBetween(
        node: UUID,
        connections: List<EndpointConnection>,
        connects: (EndpointConnection, EndpointConnection) -> Boolean,
    ) = addTransitions(node, createTransitionsBetween(connections, connects))

    private fun TopologyBuilder.addTransitions(node: UUID, transitions: List<GeneratedTransition>) =
        transitions.forEach { transition ->
            addTransition(
                node = node,
                incomingEdge = transition.incomingEdge,
                incomingDirection = transition.incomingDirection,
                outgoingEdge = transition.outgoingEdge,
                outgoingDirection = transition.outgoingDirection,
            )
        }
}

internal fun createSwitchTransitions(connections: List<EndpointConnection>): List<GeneratedTransition> =
    createTransitionsBetween(connections) { first, second ->
        switchSidesConnect(first.side as? SwitchSide, second.side as? SwitchSide)
    }

private fun createTransitionsBetween(
    connections: List<EndpointConnection>,
    connects: (EndpointConnection, EndpointConnection) -> Boolean,
): List<GeneratedTransition> = connections.flatMap { incoming ->
    connections.mapNotNull { outgoing ->
        if (incoming != outgoing && connects(incoming, outgoing)) {
            GeneratedTransition(
                incomingEdge = incoming.edgeId,
                incomingDirection = incoming.incomingDirection,
                outgoingEdge = outgoing.edgeId,
                outgoingDirection = outgoing.outgoingDirection,
            )
        } else {
            null
        }
    }
}

private fun switchSidesConnect(first: SwitchSide?, second: SwitchSide?): Boolean =
    when {
        first == null || second == null -> false
        first.key == second.key -> first::class != second::class
        else ->
            first is SwitchSide.Internal && second is SwitchSide.Internal && first.key.switchId != second.key.switchId
    }

/** At the end of a track, the only allowed transition is a U-turn back along the same edge. */
private fun TopologyBuilder.addUTurn(node: UUID, edge: UUID, incomingDirection: TopologyDirection) =
    addTransition(
        node = node,
        incomingEdge = edge,
        incomingDirection = incomingDirection,
        outgoingEdge = edge,
        outgoingDirection = incomingDirection.reverse(),
    )

private fun edgeUuid(edge: DbLayoutEdge): UUID =
    requireNotNull(edge.uuid) { "Database layout edge has no UUID: edge=${edge.id}" }

private fun nodeUuid(node: DbLayoutNode): UUID =
    requireNotNull(node.uuid) { "Database layout node has no UUID: node=${node.id}" }

private data class NanoEdgeData(val edge: DbLayoutEdge, val tracks: List<LocationTrack>)

private data class NodeOccurrence(val node: DbLayoutNode, val edgeId: IntId<LayoutEdge>, val location: Point)

private val DbSwitchNode.switchLinks: List<SwitchLink>
    get() = listOfNotNull(portA, portB)

internal data class SwitchJointKey(val switchId: IntId<LayoutSwitch>, val joint: JointNumber)

private data class SwitchLineData(
    val id: UUID,
    val alignment: SwitchStructureAlignment,
    val startNode: UUID,
    val endNode: UUID,
    val startKey: SwitchJointKey,
    val endKey: SwitchJointKey,
    val tracks: List<LocationTrack>,
)

internal sealed interface EndpointSide

private data class BoundarySide(val boundary: TrackBoundary) : EndpointSide

internal sealed interface SwitchSide : EndpointSide {
    val key: SwitchJointKey

    data class Internal(override val key: SwitchJointKey) : SwitchSide

    data class External(override val key: SwitchJointKey) : SwitchSide
}

internal enum class EdgeEndpoint {
    START,
    END,
}

internal data class EndpointConnection(
    val edgeId: UUID,
    val endpoint: EdgeEndpoint,
    val side: EndpointSide?,
) {
    val incomingDirection: TopologyDirection
        get() = if (endpoint == EdgeEndpoint.START) TopologyDirection.DESCENDING else TopologyDirection.ASCENDING

    val outgoingDirection: TopologyDirection
        get() = incomingDirection.reverse()
}

internal data class GeneratedTransition(
    val incomingEdge: UUID,
    val incomingDirection: TopologyDirection,
    val outgoingEdge: UUID,
    val outgoingDirection: TopologyDirection,
)

private fun MutableMap<UUID, MutableList<EndpointConnection>>.add(
    connection: fi.fta.geoviite.infra.tracklayout.DbNodeConnection,
    edgeId: UUID,
    endpoint: EdgeEndpoint,
) {
    val nodeId = nodeUuid(connection.node)
    val side =
        when (connection.node) {
            is DbTrackBoundaryNode -> connection.trackBoundaryIn?.let(::BoundarySide)
            is DbSwitchNode ->
                connection.switchOut?.let { link -> SwitchSide.External(SwitchJointKey(link.id, link.jointNumber)) }
        }
    getOrPut(nodeId, ::mutableListOf).add(EndpointConnection(edgeId, endpoint, side))
}

private fun collectNodesBySwitchJoint(
    edgeGroups: List<NanoEdgeData>,
    topologySwitchJoints: Set<SwitchJointKey>,
): Map<SwitchJointKey, DbSwitchNode> =
    edgeGroups
        .flatMap { (edge) -> listOf(edge.startNode.node, edge.endNode.node) }
        .filterIsInstance<DbSwitchNode>()
        .flatMap { node -> node.switchLinks.map { link -> SwitchJointKey(link.id, link.jointNumber) to node } }
        .filter { (key) -> key in topologySwitchJoints }
        .groupBy({ (key) -> key }, { (_, node) -> node })
        .mapValues { (key, nodes) ->
            require(nodes.map(DbLayoutNode::id).distinct().size == 1) {
                "Switch joint is connected to multiple layout nodes: switch=${key.switchId} joint=${key.joint}"
            }
            nodes.first()
        }

private fun createSwitchLines(
    switch: LayoutSwitch,
    structure: SwitchStructure,
    oid: Oid<LayoutSwitch>,
    nodesBySwitchJoint: Map<SwitchJointKey, DbSwitchNode>,
    edgeGroups: List<NanoEdgeData>,
): List<SwitchLineData> {
    val switchId = switch.id as IntId
    return structure.alignments.mapNotNull { alignment ->
        val startKey = SwitchJointKey(switchId, alignment.jointNumbers.first())
        val endKey = SwitchJointKey(switchId, alignment.jointNumbers.last())
        val startNode = nodesBySwitchJoint[startKey] ?: return@mapNotNull null
        val endNode = nodesBySwitchJoint[endKey] ?: return@mapNotNull null
        val tracks =
            edgeGroups
                .filter { (edge) ->
                    edge.startNode.switchIn?.id == switchId &&
                        edge.endNode.switchIn?.id == switchId &&
                        edge.startNode.switchIn?.jointNumber?.let(alignment::contains) == true &&
                        edge.endNode.switchIn?.jointNumber?.let(alignment::contains) == true
                }
                .flatMap(NanoEdgeData::tracks)
                .distinctBy { track -> track.id }
        SwitchLineData(
            id = switchLineUuid(oid, alignment.jointNumbers),
            alignment = alignment,
            startNode = nodeUuid(startNode),
            endNode = nodeUuid(endNode),
            startKey = startKey,
            endKey = endKey,
            tracks = tracks,
        )
    }
}

private fun nodeLocation(
    node: DbLayoutNode,
    occurrences: List<NodeOccurrence>,
    switches: Map<IntId<LayoutSwitch>, LayoutSwitch>,
    topologySwitchJoints: Set<SwitchJointKey>,
): Point =
    (node as? DbSwitchNode)
        ?.switchLinks
        ?.filter { link -> SwitchJointKey(link.id, link.jointNumber) in topologySwitchJoints }
        ?.sortedBy { link -> link.id.intValue }
        ?.firstNotNullOfOrNull { link -> switches[link.id]?.getJoint(link.jointNumber)?.location }
        ?: occurrences.minBy { occurrence -> occurrence.edgeId.intValue }.location

fun switchLineUuid(switchOid: Oid<LayoutSwitch>, jointNumbers: List<JointNumber>): UUID =
    TOPOLOGY_API_ARCS_UUID_GENERATOR.generate(
        "${switchOid}:${jointNumbers.joinToString(",") { joint -> joint.intValue.toString() }}"
    )

/**
 * Holds the topology of a single track layout version. The nano level topology is the full-detail base, from which the
 * coarser micro level topology is derived only if it is actually requested.
 */
private data class CachedTopology(val nano: Topology) {
    val micro: Topology by lazy { TODO("GVT-3703: simplify the nano level topology into the micro level topology") }
}
