package fi.fta.geoviite.infra.tracklayout.graph

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.NameBasedGenerator
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.configuration.ManualCacheStatsProvider
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.LayoutContextTransition
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.tracklayout.DbLayoutEdge
import fi.fta.geoviite.infra.tracklayout.DbLayoutNode
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.DbSwitchNode
import fi.fta.geoviite.infra.tracklayout.DbTrackBoundaryNode
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.SwitchStructureAlignmentM
import fi.fta.geoviite.infra.tracklayout.TrackBoundary
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel.MICRO
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel.NANO
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

private const val TOPOLOGY_CACHE_SIZE = 10L
private val TOPOLOGY_API_ARCS_UUID_GENERATOR: NameBasedGenerator =
    Generators.nameBasedGenerator(UUID.fromString("e1ad9cd7-78d2-5f98-a85e-e07870846250"))

@GeoviiteService
class TopologyService(
    private val locationTrackService: LocationTrackService,
    private val layoutSwitchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
) : ManualCacheStatsProvider {

    private val topologyCache: Cache<TopologyRequest, CachedTopology> =
        Caffeine.newBuilder().maximumSize(TOPOLOGY_CACHE_SIZE).recordStats().build()

    override fun cacheStats() = mapOf("layout-topology" to topologyCache.stats())

    fun getTopology(version: Publication, detailLevel: TopologyDetailLevel): Topology =
        getTopology(version.layoutBranch.branch, version.publicationTime, detailLevel)

    fun getTopology(branch: LayoutBranch, moment: Instant, detailLevel: TopologyDetailLevel): Topology =
        getTopology(TopologyRequest.Snapshot(branch, moment), detailLevel)

    fun getTopology(
        context: LayoutContext,
        changeTime: Instant,
        detailLevel: TopologyDetailLevel,
    ): Topology =
        getTopology(
            if (context.state == OFFICIAL) {
                TopologyRequest.Snapshot(context.branch, changeTime)
            } else {
                TopologyRequest.Layout(context, changeTime)
            },
            detailLevel,
        )

    fun getTopology(
        context: LayoutContextTransition,
        tracks: Set<LayoutRowVersion<LocationTrack>>,
        switches: Set<LayoutRowVersion<LayoutSwitch>>,
        detailLevel: TopologyDetailLevel,
    ): Topology = getTopology(TopologyRequest.Validation(context, tracks, switches), detailLevel)

    private fun getTopology(request: TopologyRequest, detailLevel: TopologyDetailLevel): Topology =
        topologyCache.get(request, ::createTopology).let { cachedTopology ->
            when (detailLevel) {
                NANO -> cachedTopology.nano
                MICRO -> cachedTopology.micro
            }
        }

    private fun createTopology(request: TopologyRequest): CachedTopology {
        val data =
            when (request) {
                is TopologyRequest.Snapshot ->
                    TopologyData(
                        branch = request.branch,
                        tracks =
                            locationTrackService.listOfficialWithGeometryAtMoment(
                                request.branch,
                                request.moment,
                            ),
                        switches =
                            layoutSwitchService
                                .listOfficialAtMoment(request.branch, request.moment)
                                .filter(LayoutSwitch::exists),
                    )
                is TopologyRequest.Layout ->
                    TopologyData(
                        branch = request.context.branch,
                        tracks = locationTrackService.listWithGeometries(request.context, includeDeleted = false),
                        switches = layoutSwitchService.list(request.context, includeDeleted = false),
                    )
                is TopologyRequest.Validation ->
                    TopologyData(
                        branch = request.context.candidateBranch,
                        tracks = locationTrackService.getManyWithGeometries(request.tracks.toList()),
                        switches = layoutSwitchService.getMany(request.switches.toList()),
                    )
            }
        return CachedTopology(createNanoTopology(data))
    }

    private fun createNanoTopology(topologyData: TopologyData): Topology {
        val (branch, tracks, topologySwitches) = topologyData
        val switches = topologySwitches.associateBy { switch -> switch.id as IntId }
        val trackIds = tracks.map { (track) -> track.id as IntId<LocationTrack> }.distinct()
        val trackExternalIds = locationTrackService.getExternalIdsByBranch(trackIds)
        val trackOids = trackIds.associateWith { trackId ->
            val oids = trackExternalIds[trackId].orEmpty()
            oids[branch] ?: oids[LayoutBranch.main]
        }
        val switchIds = switches.keys.toList()
        val switchExternalIds = layoutSwitchService.getExternalIdsByBranch(switchIds)
        val switchRefs =
            switches.values.associate { switch ->
                val switchId = switch.id as IntId
                val oids = switchExternalIds[switchId].orEmpty()
                val switchOid = oids[branch] ?: oids[LayoutBranch.main]
                switchId to (switchOid?.let(::OidSwitchRef) ?: IdSwitchRef(switchId))
            }
        return createNanoTopology(
            tracks,
            switches,
            switchLibraryService.getSwitchStructuresById(),
            switchRefs,
            trackOids,
        )
    }
}

/**
 * Builds the nano level topology: a direct graph representation of the database layout, with switch alignments added as
 * their own edges.
 */
internal fun createNanoTopology(
    tracks: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    switches: Map<IntId<LayoutSwitch>, LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
    switchRefs: Map<IntId<LayoutSwitch>, SwitchRef>,
    trackOids: Map<IntId<LocationTrack>, Oid<LocationTrack>?> = emptyMap(),
): Topology {
    val topologySwitchJoints = switches.values.switchJoints(structures)
    val edgeGroups = tracks.tracksByEdges()
    val nodesBySwitchJoint = edgeGroups.collectNodesBySwitchJoint(topologySwitchJoints)
    val synthesizedSwitchNodes = mutableMapOf<UUID, SynthesizedSwitchNode>()
    val switchLines =
        switches.values.flatMap { switch ->
            createSwitchLines(
                switch = switch,
                structure = structures.getValue(switch.switchStructureId),
                uuidReference = switchRefs.getValue(switch.id as IntId),
                nodesBySwitchJoint = nodesBySwitchJoint,
                edgeGroups = edgeGroups,
                synthesizedNodes = synthesizedSwitchNodes,
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
            .flatMap { (edge) -> listOf(edge.startNode.node.uuid, edge.endNode.node.uuid) }
            .plus(switchLines.flatMap { line -> listOf(line.startNode, line.endNode) })
            .toSet()
    val databaseNodes =
        nodeOccurrences
            .filter { occurrence -> occurrence.node.uuid in topologyNodeIds }
            .groupBy { occurrence -> occurrence.node.id }
    val endpointConnections = mutableMapOf<UUID, MutableList<EndpointConnection>>()

    return buildTopology {
        addLayoutRouting(
            trackGeometries = tracks.map { (_, geometry) -> geometry },
            switches = switches,
            structures = structures,
        )
        databaseNodes.values.forEach { occurrences ->
            val dbNode = occurrences.first().node
            val location = nodeLocation(dbNode, occurrences, switches, topologySwitchJoints)
            val nodeSwitches =
                (dbNode as? DbSwitchNode)?.switchLinks?.filter { link ->
                    SwitchJointKey(link.id, link.jointNumber) in topologySwitchJoints
                } ?: emptyList()
            addNode(
                id = dbNode.uuid,
                type = dbNode.type,
                location = location,
                switchReferences =
                    nodeSwitches.map { link ->
                        TopologySwitchReference(link, (switchRefs[link.id] as? OidSwitchRef)?.oid)
                    },
            )
        }
        synthesizedSwitchNodes.values
            .filter { node -> node.id in topologyNodeIds }
            .forEach { node ->
                addNode(
                    id = node.id,
                    type = fi.fta.geoviite.infra.tracklayout.LayoutNodeType.SWITCH,
                    location = node.location,
                    switchReferences = listOf(node.switchReference),
                )
            }
        regularEdges.forEach { (edge, edgeTracks) ->
            val edgeId = edge.uuid
            addEdge(
                id = edgeId,
                startNode = edge.startNode.node.uuid,
                endNode = edge.endNode.node.uuid,
                length = edge.length.distance,
                trackReferences =
                    edgeTracks.map { track ->
                        TopologyLocationTrackReference(track, trackOids[track.id as IntId<LocationTrack>])
                    },
                routingReference = LayoutEdgeRoutingReference(edge.id),
            )
            endpointConnections.add(edge.startNode, edgeId, EdgeEndpoint.START)
            endpointConnections.add(edge.endNode, edgeId, EdgeEndpoint.END)
        }
        switchLines.forEach { line ->
            addEdge(
                id = line.id,
                startNode = line.startNode,
                endNode = line.endNode,
                length = line.length,
                trackReferences =
                    line.tracks.map { track ->
                        TopologyLocationTrackReference(track, trackOids[track.id as IntId<LocationTrack>])
                    },
                routingReference = line.routingReference,
            )
            endpointConnections
                .getOrPut(line.startNode, ::mutableListOf)
                .add(EndpointConnection(line.id, EdgeEndpoint.START, line.startSide))
            endpointConnections
                .getOrPut(line.endNode, ::mutableListOf)
                .add(EndpointConnection(line.id, EdgeEndpoint.END, line.endSide))
        }
        databaseNodes.values.forEach { occurrences ->
            val node = occurrences.first().node
            addNodeTransitions(node, endpointConnections[node.uuid].orEmpty())
        }
        synthesizedSwitchNodes.values
            .filter { node -> node.id in topologyNodeIds }
            .forEach { node ->
                addTransitions(node.id, createSwitchTransitions(endpointConnections[node.id].orEmpty()))
            }
    }
}

private fun Collection<LayoutSwitch>.switchJoints(structures: Map<IntId<SwitchStructure>, SwitchStructure>) =
    this.flatMap { switch ->
            val structure = structures.getValue(switch.switchStructureId)
            (structure.endJointNumbers + structure.presentationJointNumber).map { joint ->
                SwitchJointKey(switch.id as IntId, joint)
            }
        }
        .toSet()

private fun List<Pair<LocationTrack, DbLocationTrackGeometry>>.tracksByEdges() =
    this.flatMap { (track, geometry) -> geometry.edges.map { edge -> edge to track } }
        .groupBy { (edge, _) -> edge.id }
        .values
        .map { occurrences ->
            NanoEdgeData(
                edge = occurrences.first().first,
                tracks = occurrences.map { (_, track) -> track }.distinctBy { track -> track.id },
            )
        }

private fun TopologyBuilder.addNodeTransitions(node: DbLayoutNode, connections: List<EndpointConnection>) {
    when (node) {
        is DbTrackBoundaryNode -> {
            if (node.portB == null) {
                connections.forEach { connection ->
                    addUTurn(node.uuid, connection.edgeId, connection.incomingDirection)
                }
            } else {
                addTransitionsBetween(node.uuid, connections) { first, second ->
                    val firstBoundary = (first.side as? BoundarySide)?.boundary
                    val secondBoundary = (second.side as? BoundarySide)?.boundary
                    firstBoundary != null && secondBoundary != null && firstBoundary != secondBoundary
                }
            }
        }
        is DbSwitchNode -> addTransitions(node.uuid, createSwitchTransitions(connections))
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
            routingAlignments = transition.routingAlignments,
        )
    }

internal fun createSwitchTransitions(connections: List<EndpointConnection>): List<GeneratedTransition> =
    createTransitionsBetween(
        connections,
        connects = { first, second -> switchSidesConnect(first.side as? SwitchSide, second.side as? SwitchSide) },
        routingAlignments = { first, second ->
            switchTransitionRoutingAlignments(first.side as? SwitchSide, second.side as? SwitchSide)
        },
    )

private fun createTransitionsBetween(
    connections: List<EndpointConnection>,
    connects: (EndpointConnection, EndpointConnection) -> Boolean,
    routingAlignments: (EndpointConnection, EndpointConnection) -> Set<TopologySwitchAlignment> = { _, _ ->
        emptySet()
    },
): List<GeneratedTransition> = connections.flatMap { incoming ->
    connections.mapNotNull { outgoing ->
        if (incoming != outgoing && connects(incoming, outgoing)) {
            GeneratedTransition(
                incomingEdge = incoming.edgeId,
                incomingDirection = incoming.incomingDirection,
                outgoingEdge = outgoing.edgeId,
                outgoingDirection = outgoing.outgoingDirection,
                routingAlignments = routingAlignments(incoming, outgoing),
            )
        } else {
            null
        }
    }
}

private fun switchSidesConnect(first: SwitchSide?, second: SwitchSide?): Boolean =
    when {
        first == null || second == null -> false
        first.key == second.key ->
            when {
                first is SwitchSide.Internal && second is SwitchSide.Internal ->
                    first.routingAlignments.intersect(second.routingAlignments).isNotEmpty()
                else -> first::class != second::class
            }
        else ->
            first is SwitchSide.Internal && second is SwitchSide.Internal && first.key.switchId != second.key.switchId
    }

private fun switchTransitionRoutingAlignments(
    first: SwitchSide?,
    second: SwitchSide?,
): Set<TopologySwitchAlignment> =
    when {
        first is SwitchSide.Internal && second is SwitchSide.Internal && first.key == second.key ->
            first.routingAlignments.intersect(second.routingAlignments)
        first is SwitchSide.Internal && second is SwitchSide.External && first.key == second.key ->
            first.routingAlignments
        first is SwitchSide.External && second is SwitchSide.Internal && first.key == second.key ->
            second.routingAlignments
        else -> emptySet()
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

private data class NanoEdgeData(val edge: DbLayoutEdge, val tracks: List<LocationTrack>)

private data class NodeOccurrence(val node: DbLayoutNode, val edgeId: IntId<LayoutEdge>, val location: Point)

private val DbSwitchNode.switchLinks: List<SwitchLink>
    get() = listOfNotNull(portA, portB)

internal data class SwitchJointKey(val switchId: IntId<LayoutSwitch>, val joint: JointNumber)

private data class SwitchLineData(
    val id: UUID,
    val startNode: UUID,
    val endNode: UUID,
    val startSide: SwitchSide.Internal,
    val endSide: SwitchSide.Internal,
    val length: Double,
    val tracks: List<LocationTrack>,
    val routingReference: SwitchAlignmentRoutingReference,
)

private data class SynthesizedSwitchNode(
    val id: UUID,
    val location: Point,
    val switchReference: TopologySwitchReference,
)

internal sealed interface EndpointSide

private data class BoundarySide(val boundary: TrackBoundary) : EndpointSide

internal sealed interface SwitchSide : EndpointSide {
    val key: SwitchJointKey

    data class Internal(
        override val key: SwitchJointKey,
        val routingAlignments: Set<TopologySwitchAlignment> = emptySet(),
    ) : SwitchSide

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
    val routingAlignments: Set<TopologySwitchAlignment> = emptySet(),
)

private fun MutableMap<UUID, MutableList<EndpointConnection>>.add(
    connection: fi.fta.geoviite.infra.tracklayout.DbNodeConnection,
    edgeId: UUID,
    endpoint: EdgeEndpoint,
) {
    val nodeId = connection.node.uuid
    val side =
        when (connection.node) {
            is DbTrackBoundaryNode -> connection.trackBoundaryIn?.let(::BoundarySide)
            is DbSwitchNode ->
                connection.switchOut?.let { link -> SwitchSide.External(SwitchJointKey(link.id, link.jointNumber)) }
        }
    getOrPut(nodeId, ::mutableListOf).add(EndpointConnection(edgeId, endpoint, side))
}

private fun List<NanoEdgeData>.collectNodesBySwitchJoint(
    topologySwitchJoints: Set<SwitchJointKey>
): Map<SwitchJointKey, DbSwitchNode> =
    this.asSequence()
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
    uuidReference: SwitchRef,
    nodesBySwitchJoint: Map<SwitchJointKey, DbSwitchNode>,
    edgeGroups: List<NanoEdgeData>,
    synthesizedNodes: MutableMap<UUID, SynthesizedSwitchNode>,
): List<SwitchLineData> {
    val switchId = switch.id as IntId
    val mainJoint = structure.presentationJointNumber
    return if (mainJoint in structure.endJointNumbers) {
        structure.alignments.mapNotNull { alignment ->
            val startKey = SwitchJointKey(switchId, alignment.jointNumbers.first())
            val endKey = SwitchJointKey(switchId, alignment.jointNumbers.last())
            val startNode = nodesBySwitchJoint[startKey] ?: return@mapNotNull null
            val endNode = nodesBySwitchJoint[endKey] ?: return@mapNotNull null
            val tracks = alignmentTracks(edgeGroups, switchId, alignment)
            val routingAlignment = TopologySwitchAlignment(switchId, alignment)
            SwitchLineData(
                id = switchLineUuid(uuidReference, alignment.jointNumbers),
                startNode = startNode.uuid,
                endNode = endNode.uuid,
                startSide = SwitchSide.Internal(startKey, setOf(routingAlignment)),
                endSide = SwitchSide.Internal(endKey, setOf(routingAlignment)),
                length = alignment.length(),
                tracks = tracks,
                routingReference =
                    SwitchAlignmentRoutingReference(
                        switchId = switchId,
                        alternatives =
                            listOf(
                                SwitchAlignmentRoutingAlternative(
                                    alignment = alignment,
                                    forward = true,
                                    mRange = Range(LineM(0.0), LineM(alignment.length())),
                                )
                            ),
                    ),
            )
        }
    } else {
        createCenterMainSwitchLines(
            switch = switch,
            structure = structure,
            uuidReference = uuidReference,
            nodesBySwitchJoint = nodesBySwitchJoint,
            edgeGroups = edgeGroups,
            synthesizedNodes = synthesizedNodes,
        )
    }
}

private fun createCenterMainSwitchLines(
    switch: LayoutSwitch,
    structure: SwitchStructure,
    uuidReference: SwitchRef,
    nodesBySwitchJoint: Map<SwitchJointKey, DbSwitchNode>,
    edgeGroups: List<NanoEdgeData>,
    synthesizedNodes: MutableMap<UUID, SynthesizedSwitchNode>,
): List<SwitchLineData> {
    val switchId = switch.id as IntId
    val mainJoint = structure.presentationJointNumber
    val mainKey = SwitchJointKey(switchId, mainJoint)
    val linkedAlignments = linkedCenterMainAlignments(structure, switchId, nodesBySwitchJoint)
    if (linkedAlignments.isEmpty()) return emptyList()

    val mainNodeId =
        centerMainNodeId(switch, uuidReference, mainJoint, mainKey, nodesBySwitchJoint, synthesizedNodes)
            ?: return emptyList()
    return structure.endJointNumbers.mapNotNull { endJoint ->
        createCenterMainSwitchLine(
            switchId,
            structure,
            uuidReference,
            mainJoint,
            mainKey,
            mainNodeId,
            endJoint,
            linkedAlignments,
            nodesBySwitchJoint,
            edgeGroups,
        )
    }
}

private fun linkedCenterMainAlignments(
    structure: SwitchStructure,
    switchId: IntId<LayoutSwitch>,
    nodesBySwitchJoint: Map<SwitchJointKey, DbSwitchNode>,
): List<SwitchStructureAlignment> =
    structure.alignments.filter { alignment ->
        val startKey = SwitchJointKey(switchId, alignment.jointNumbers.first())
        val endKey = SwitchJointKey(switchId, alignment.jointNumbers.last())
        startKey in nodesBySwitchJoint && endKey in nodesBySwitchJoint
    }

private fun centerMainNodeId(
    switch: LayoutSwitch,
    uuidReference: SwitchRef,
    mainJoint: JointNumber,
    mainKey: SwitchJointKey,
    nodesBySwitchJoint: Map<SwitchJointKey, DbSwitchNode>,
    synthesizedNodes: MutableMap<UUID, SynthesizedSwitchNode>,
): UUID? {
    nodesBySwitchJoint[mainKey]?.let {
        return it.uuid
    }
    val mainLocation = switch.getJoint(mainJoint)?.location ?: return null
    val id = switchNodeUuid(uuidReference, mainJoint)
    synthesizedNodes.getOrPut(id) {
        SynthesizedSwitchNode(
            id = id,
            location = mainLocation,
            switchReference =
                TopologySwitchReference(
                    switch = SwitchLink(mainKey.switchId, SwitchJointRole.MAIN, mainJoint),
                    oid = (uuidReference as? OidSwitchRef)?.oid,
                ),
        )
    }
    return id
}

private fun createCenterMainSwitchLine(
    switchId: IntId<LayoutSwitch>,
    structure: SwitchStructure,
    uuidReference: SwitchRef,
    mainJoint: JointNumber,
    mainKey: SwitchJointKey,
    mainNodeId: UUID,
    endJoint: JointNumber,
    linkedAlignments: List<SwitchStructureAlignment>,
    nodesBySwitchJoint: Map<SwitchJointKey, DbSwitchNode>,
    edgeGroups: List<NanoEdgeData>,
): SwitchLineData? {
    val endKey = SwitchJointKey(switchId, endJoint)
    val endNode = nodesBySwitchJoint[endKey] ?: return null
    val jointAlignments = linkedAlignments.filter { alignment ->
        alignment.jointNumbers.first() == endJoint || alignment.jointNumbers.last() == endJoint
    }
    if (jointAlignments.isEmpty()) return null

    val routingAlternatives = jointAlignments.map { alignment ->
        createCenterMainRoutingAlternative(structure, endJoint, mainJoint, alignment)
    }
    val routingAlignments =
        routingAlternatives.mapTo(mutableSetOf()) { alternative ->
            TopologySwitchAlignment(switchId, alternative.alignment)
        }
    val legTracks = centerMainLegTracks(edgeGroups, switchId, endJoint, jointAlignments)
    val legLength = structure.distanceAlongAlignment(endJoint, mainJoint, jointAlignments.first())
    return SwitchLineData(
        id = switchLineUuid(uuidReference, listOf(mainJoint, endJoint)),
        startNode = mainNodeId,
        endNode = endNode.uuid,
        startSide = SwitchSide.Internal(mainKey, routingAlignments),
        endSide = SwitchSide.Internal(endKey, routingAlignments),
        length = legLength,
        tracks = legTracks,
        routingReference =
            SwitchAlignmentRoutingReference(
                switchId = switchId,
                alternatives = routingAlternatives,
            ),
    )
}

private fun createCenterMainRoutingAlternative(
    structure: SwitchStructure,
    endJoint: JointNumber,
    mainJoint: JointNumber,
    alignment: SwitchStructureAlignment,
): SwitchAlignmentRoutingAlternative {
    val isStartOfAlignment = alignment.jointNumbers.first() == endJoint
    val legLength = structure.distanceAlongAlignment(endJoint, mainJoint, alignment)
    val alignmentLength = alignment.length()
    val mRange: Range<LineM<SwitchStructureAlignmentM>> =
        if (isStartOfAlignment) {
            Range(LineM(0.0), LineM(legLength))
        } else {
            Range(LineM(alignmentLength - legLength), LineM(alignmentLength))
        }
    return SwitchAlignmentRoutingAlternative(
        alignment = alignment,
        forward = !isStartOfAlignment,
        mRange = mRange,
    )
}

private fun SwitchStructure.distanceAlongAlignment(
    from: JointNumber,
    to: JointNumber,
    alignment: SwitchStructureAlignment,
): Double = abs(mAtJoint(alignment, to) - mAtJoint(alignment, from))

private fun SwitchStructure.mAtJoint(alignment: SwitchStructureAlignment, joint: JointNumber): Double {
    val jointIndex = alignment.jointNumbers.indexOf(joint)
    require(jointIndex >= 0) { "Alignment must contain joint $joint: alignment=$alignment" }
    if (jointIndex == 0) return 0.0
    if (jointIndex == alignment.jointNumbers.lastIndex) return alignment.length()

    return if (alignment.elements.size == alignment.jointNumbers.size - 1) {
        alignment.elements.take(jointIndex).sumOf { element -> element.length }
    } else {
        val jointDistances =
            alignment.jointNumbers.zipWithNext().map { (start, end) ->
                lineLength(getJointLocation(start), getJointLocation(end))
            }
        val totalJointDistance = jointDistances.sum()
        alignment.length() * jointDistances.take(jointIndex).sum() / totalJointDistance
    }
}

private fun centerMainLegTracks(
    edgeGroups: List<NanoEdgeData>,
    switchId: IntId<LayoutSwitch>,
    endJoint: JointNumber,
    jointAlignments: List<SwitchStructureAlignment>,
): List<LocationTrack> =
    edgeGroups
        .filter { edgeData -> edgeData.isCenterMainLeg(switchId, endJoint, jointAlignments) }
        .flatMap(NanoEdgeData::tracks)
        .distinctBy { track -> track.id }

private fun NanoEdgeData.isCenterMainLeg(
    switchId: IntId<LayoutSwitch>,
    endJoint: JointNumber,
    jointAlignments: List<SwitchStructureAlignment>,
): Boolean {
    val startLink = edge.startNode.switchIn ?: return false
    val endLink = edge.endNode.switchIn ?: return false
    return startLink.id == switchId &&
        endLink.id == switchId &&
        (startLink.jointNumber == endJoint || endLink.jointNumber == endJoint) &&
        jointAlignments.any { alignment ->
            alignment.contains(startLink.jointNumber) && alignment.contains(endLink.jointNumber)
        }
}

private fun alignmentTracks(
    edgeGroups: List<NanoEdgeData>,
    switchId: IntId<LayoutSwitch>,
    alignment: SwitchStructureAlignment,
): List<LocationTrack> =
    edgeGroups
        .filter { (edge) ->
            edge.startNode.switchIn?.id == switchId &&
                edge.endNode.switchIn?.id == switchId &&
                edge.startNode.switchIn?.jointNumber?.let(alignment::contains) == true &&
                edge.endNode.switchIn?.jointNumber?.let(alignment::contains) == true
        }
        .flatMap(NanoEdgeData::tracks)
        .distinctBy { track -> track.id }

fun switchNodeUuid(switchReference: SwitchRef, jointNumber: JointNumber): UUID =
    TOPOLOGY_API_ARCS_UUID_GENERATOR.generate("node:${switchReference.uuidKey()}:${jointNumber.intValue}")

private fun SwitchRef.uuidKey(): String =
    when (this) {
        is IdSwitchRef -> "id:${id.intValue}"
        is OidSwitchRef -> oid.toString()
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

fun switchLineUuid(
    switchReference: SwitchRef,
    jointNumbers: List<JointNumber>,
): UUID {
    val reference =
        when (switchReference) {
            is IdSwitchRef -> "id:${switchReference.id.intValue}"
            is OidSwitchRef -> switchReference.oid.toString()
        }

    return TOPOLOGY_API_ARCS_UUID_GENERATOR.generate(
        "$reference:${jointNumbers.joinToString(",") { it.intValue.toString() }}"
    )
}

/**
 * The identifier of a micro level edge that combines several nano level edges: UUIDv5 of the combined nano edge UUIDs,
 * listed in the order they are traversed along the combined edge's own ascending m-direction.
 */
fun microEdgeUuid(nanoEdgeIds: List<UUID>): UUID =
    TOPOLOGY_API_ARCS_UUID_GENERATOR.generate(nanoEdgeIds.joinToString(",") { id -> id.toString() })

/**
 * Holds the topology of a single track layout version. The nano level topology is the full-detail base, from which the
 * coarser micro level topology is derived only if it is actually requested.
 */
internal data class CachedTopology(val nano: Topology) {
    val micro: Topology by lazy { simplifyToMicroLevel(nano) }
}

private sealed interface TopologyRequest {
    data class Snapshot(val branch: LayoutBranch, val moment: Instant) : TopologyRequest

    data class Layout(val context: LayoutContext, val changeTime: Instant) : TopologyRequest

    data class Validation(
        val context: LayoutContextTransition,
        val tracks: Set<LayoutRowVersion<LocationTrack>>,
        val switches: Set<LayoutRowVersion<LayoutSwitch>>,
    ) : TopologyRequest
}

private data class TopologyData(
    val branch: LayoutBranch,
    val tracks: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    val switches: List<LayoutSwitch>,
)

internal fun TopologyBuilder.addLayoutRouting(
    trackGeometries: List<DbLocationTrackGeometry>,
    switches: Map<IntId<LayoutSwitch>, LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
) {
    trackGeometries
        .flatMap { geometry ->
            geometry.edgesWithM.map { (edge, mRange) -> edge to TrackSection(geometry.trackId, mRange) }
        }
        .groupBy { (edge) -> edge.id }
        .forEach { (_, occurrences) ->
            val edge = occurrences.first().first
            addLayoutEdgeRouting(edge, occurrences.map { (_, track) -> track }.toSet())
            resolveTopologySwitchSegments(edge, switches, structures).forEach { (alignment, segment) ->
                addSwitchAlignmentSegment(alignment, segment)
            }
        }
}

internal fun resolveTopologySwitchSegments(
    edge: DbLayoutEdge,
    switches: Map<IntId<LayoutSwitch>, LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): Map<TopologySwitchAlignment, TopologySwitchSegment> {
    val switchId = edge.startNode.switchIn?.id?.takeIf { edge.endNode.switchIn?.id == it }
    val startJoint = edge.startNode.switchIn?.jointNumber
    val endJoint = edge.endNode.switchIn?.jointNumber
    val switch = switchId?.let(switches::get)
    return if (switch != null && startJoint != null && endJoint != null) {
        val structure = structures.getValue(switch.switchStructureId)
        structure.alignments
            .filter { alignment -> alignment.contains(startJoint) && alignment.contains(endJoint) }
            .mapNotNull { alignment ->
                val startJointSwitchM =
                    structure.distanceAlongAlignment(alignment.jointNumbers.first(), startJoint, alignment)
                val endJointSwitchM =
                    structure.distanceAlongAlignment(alignment.jointNumbers.first(), endJoint, alignment)
                val startSwitchM = minOf(startJointSwitchM, endJointSwitchM)
                val endSwitchM = maxOf(startJointSwitchM, endJointSwitchM)
                if (endSwitchM - startSwitchM >= LAYOUT_M_DELTA) {
                    TopologySwitchAlignment(switchId, alignment) to
                        TopologySwitchSegment(
                            edgeId = edge.id,
                            direction =
                                if (startJointSwitchM < endJointSwitchM) {
                                    TopologyDirection.ASCENDING
                                } else {
                                    TopologyDirection.DESCENDING
                                },
                            mRange = Range(LineM(startSwitchM), LineM(endSwitchM)),
                        )
                } else {
                    null
                }
            }
            .toMap()
    } else {
        emptyMap()
    }
}

sealed interface SwitchRef

data class IdSwitchRef(val id: IntId<LayoutSwitch>) : SwitchRef

data class OidSwitchRef(val oid: Oid<LayoutSwitch>) : SwitchRef
