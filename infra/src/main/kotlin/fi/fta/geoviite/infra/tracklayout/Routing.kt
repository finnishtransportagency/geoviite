package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.combine
import fi.fta.geoviite.infra.math.length
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import org.jgrapht.Graph
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.AsGraphUnion
import org.jgrapht.graph.DirectedWeightedMultigraph
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(RoutingGraph::class.java)

data class ClosestTrackPoint(
    val locationTrackId: IntId<LocationTrack>,
    val requestedLocation: Point,
    val trackLocation: AlignmentPoint<LocationTrackM>,
    val distance: Double,
)

data class RouteResult(
    val startConnection: ClosestTrackPoint,
    val endConnection: ClosestTrackPoint,
    val route: Route,
) {
    val totalLength: Double
        get() = route.totalLength.let { it + startConnection.distance + endConnection.distance }
}

data class Route(val sections: List<RouteSection>) {
    val totalLength: Double
        get() = sections.sumOf { it.length }
}

data class RouteSection(
    val trackId: IntId<LocationTrack>,
    // Jackson bungles up names with a single lowercase letter in the beginning
    @get:JsonProperty("mRange") val mRange: Range<LineM<LocationTrackM>>,
    val direction: EdgeDirection
) {
    val length: Double
        get() = mRange.length().distance

    fun reverse(): RouteSection = copy(direction = direction.reverse())
}

data class RouteEdgeData(
    val edge: DbLayoutEdge,
    val tracks: Set<TrackSection>,
    val switchConnections: Map<RoutingSwitchAlignment, EdgeDirection>
) {
    val startNode: DbNodeConnection
        get() = edge.startNode

    val endNode: DbNodeConnection
        get() = edge.endNode

    val length: Double
        get() = edge.length.distance

    val start: Point by lazy { edge.start.toPoint() }
    val end: Point by lazy { edge.end.toPoint() }
}

enum class VertexDirection {
    IN,
    OUT;

    fun reverse(): VertexDirection = when(this) {
        IN -> OUT
        OUT -> IN
    }
}

enum class EdgeDirection {
    UP,
    DOWN;

    fun reverse(): EdgeDirection = when(this) {
        UP -> DOWN
        DOWN -> UP
    }
}

sealed class RoutingVertex {
    abstract fun reverse(): RoutingVertex
}

sealed class RoutingEdge {
    abstract fun reverse(): RoutingEdge
}

data class RoutingConnection(val from: RoutingVertex, val to: RoutingVertex, val length: Double) {
    fun reverse(): RoutingConnection = copy(from = to.reverse(), to = from.reverse())
}

data class SwitchJointVertex(val switchId: IntId<LayoutSwitch>, val jointNumber: JointNumber, val direction: VertexDirection): RoutingVertex() {
    override fun reverse(): SwitchJointVertex = copy(direction = direction.reverse())
}
data class SwitchInternalEdge(val alignment: RoutingSwitchAlignment, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): SwitchInternalEdge = copy(direction = direction.reverse())
}

data class TrackBoundaryVertex(val trackId: IntId<LocationTrack>, val type: TrackBoundaryType, val direction: VertexDirection): RoutingVertex() {
    override fun reverse(): TrackBoundaryVertex = copy(direction = direction.reverse())
}

data class TrackMidPointVertex(val trackId: IntId<LocationTrack>, val m: LineM<LocationTrackM>, val direction: VertexDirection): RoutingVertex() {
    override fun reverse(): TrackMidPointVertex = copy(direction = direction.reverse())
}

data class TrackEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): TrackEdge = copy(direction = direction.reverse())
}

data class PartialTrackEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection, val mRange: Range<LineM<EdgeM>>, val index: Int): RoutingEdge() {
    override fun reverse(): PartialTrackEdge = copy(direction = direction.reverse())
}

data class DirectConnectionEdge(val nodeId: IntId<LayoutNode>, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): DirectConnectionEdge = copy(direction = direction.reverse())
}

data class RoutingSwitchAlignment(
    val id: IntId<LayoutSwitch>,
    val jointNumbers: List<JointNumber>,
)

data class RoutingGraph(
    private val jgraph: Graph<RoutingVertex, RoutingEdge>,
    private val edgeData: Map<IntId<LayoutEdge>, RouteEdgeData>,
    private val switchInternalEdges: Map<RoutingSwitchAlignment, List<Pair<IntId<LayoutEdge>, EdgeDirection>>>,
) {
    fun getVertices(): Set<RoutingVertex> = jgraph.vertexSet()

    fun getEdges(): Map<RoutingEdge, Pair<RoutingVertex, RoutingVertex>> = jgraph.edgeSet().associateWith { edge ->
        jgraph.getEdgeSource(edge) to jgraph.getEdgeTarget(edge)
    }

    fun findPath(start: LocationTrackCacheHit, end: LocationTrackCacheHit): Route? {
        val edges = findPathEdges(start, end)
        return edges
            ?.flatMap { edge -> toRouteSections(edge, setOf(start.track.id as IntId, end.track.id as IntId)) }
            ?.let(::compress)
            ?.let(::Route)
    }

    private fun compress(sections: List<RouteSection>): List<RouteSection> =
        sections.fold(emptyList()) { acc, section ->
            if (acc.isEmpty()) listOf(section)
            else {
                val last = acc.last()
                if (last.trackId == section.trackId && last.direction == section.direction) {
                    acc.dropLast(1) + last.copy(mRange = combine(last.mRange, section.mRange))
                } else {
                    acc + section
                }
            }
        }

    private fun toRouteSections(edge: RoutingEdge, favoredTrackIds: Set<IntId<LocationTrack>>): List<RouteSection> =
        when (edge) {
            is TrackEdge -> listOfNotNull(findRouteSection(edge.edgeId, edge.direction, favoredTrackIds))
            is PartialTrackEdge -> listOfNotNull(
                findRouteSection(edge.edgeId, edge.direction, favoredTrackIds)?.let { full ->
                    full.copy(mRange = edge.mRange.map { it.toLocationTrackM(full.mRange.min) })
                }
            )
            is SwitchInternalEdge -> switchInternalEdges[edge.alignment]
                ?.mapNotNull { (edgeId, switchDirection) -> findRouteSection(
                    edgeId,
                    if (edge.direction == EdgeDirection.UP) switchDirection else switchDirection.reverse(),
                    favoredTrackIds
                ) }
                ?: emptyList()
            is DirectConnectionEdge -> emptyList()
        }

    private fun findRouteSection(edgeId: IntId<LayoutEdge>, direction: EdgeDirection, favoredTrackIds: Set<IntId<LocationTrack>>): RouteSection? =
        edgeData.getValue(edgeId).tracks
            .let { tracks -> tracks.firstOrNull { favoredTrackIds.contains(it.id) } ?: tracks.firstOrNull() }
            ?.let { (id, mRange) -> RouteSection(id, mRange, direction) }

    fun findPathEdges(start: LocationTrackCacheHit, end: LocationTrackCacheHit): List<RoutingEdge>? {
        val (startEdge, startMRange) = start.getEdge()
        val (endEdge, endMRange) = end.getEdge()
        val startEdgeM = start.closestPoint.m.toEdgeM(startMRange.min)
        val endEdgeM = end.closestPoint.m.toEdgeM(endMRange.min)

        return when {
            // Special case: no distance between points -> no need for path
            abs(startEdgeM - endEdgeM).distance < LAYOUT_M_DELTA -> emptyList()

            // Special case: on the same edge -> a direct single-step path along the edge is the shortest
            startEdge.id == endEdge.id -> listOf(
                when {
                    (startEdgeM <= endEdgeM) -> PartialTrackEdge(
                        startEdge.id,
                        EdgeDirection.UP,
                        Range(startEdgeM, endEdgeM),
                        0,
                    )
                    else -> PartialTrackEdge(
                        startEdge.id,
                        EdgeDirection.DOWN,
                        Range(endEdgeM, startEdgeM),
                        0,
                    )
                }
            )

            // The actual pathfinding case
            else -> {
                // Create a second temp graph for the things in this routing
                val startVertex = TrackMidPointVertex(start.track.id as IntId, start.closestPoint.m, VertexDirection.IN)
                val endVertex = TrackMidPointVertex(end.track.id as IntId, end.closestPoint.m, VertexDirection.OUT)
                val tmpGraph = buildTempRoutingGraph(
                    startVertex,
                    endVertex,
                    startEdge to startMRange,
                    endEdge to endMRange,
                )
                // Route the start->end in a union graph of the main graph + temp additions
                val dijkstra = DijkstraShortestPath(AsGraphUnion(jgraph, tmpGraph))
                dijkstra.getPath(startVertex, endVertex)?.edgeList?.filterNotNull()
                    .also { println("**** Found: path=$it") }
            }
        }
    }

    private fun buildTempRoutingGraph(
        startVertex: TrackMidPointVertex,
        endVertex: TrackMidPointVertex,
        startEdgeWithM: Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>,
        endEdgeWithM: Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>,
    ): DirectedWeightedMultigraph<RoutingVertex, RoutingEdge> {
        val (startEdge, startMRange) = startEdgeWithM
        val (endEdge, endMRange) = endEdgeWithM

        val graph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)
        graph.addVertex(startVertex)
        graph.addVertex(endVertex)

        // Note: This isn't strictly correct for switch inner links (routing start/end inside a switch) if the edge
        // doesn't span the entire alignment. For example, it could be the edge 1-5 from alignment 1-5-2, in which case
        // the postStartM should continue beyond the edge end. The proper information is a bit tricky to get here, so
        // we accept this minor inaccuracy.
        val (preStartM, postStartM) = startMRange.split(startVertex.m)
            .map { r -> r.map { m -> m.toEdgeM(startMRange.min) } }
        val (preEndM, postEndM) = endMRange.split(endVertex.m)
            .map { r -> r.map { m -> m.toEdgeM(endMRange.min) } }

        // Add edges outwards from the start
        // The pre/post vertexes should already exist in the main graph, but we also need them here to add the edges
        createEdgeEndVertices(startEdge, VertexDirection.OUT).forEachIndexed { index, (preStartVertex, postStartVertex) ->
            graph.addVertex(preStartVertex)
            graph.addVertex(postStartVertex)
            PartialTrackEdge(startEdge.id, EdgeDirection.DOWN, preStartM, index)
                .also { edge ->
                    println("Adding tmp edge: type=preStart edge=$edge startVertex=$startVertex preStartVertex=$preStartVertex")
                    graph.addEdge(startVertex, preStartVertex, edge)
                    graph.setEdgeWeight(edge, preStartM.length().distance)
                }
            PartialTrackEdge(startEdge.id, EdgeDirection.UP, postStartM, index)
                .also { edge ->
                    println("Adding tmp edge: type=postStart edge=$edge startVertex=$startVertex postStartVertex=$postStartVertex")
                    graph.addEdge(startVertex, postStartVertex, edge)
                    graph.setEdgeWeight(edge, postStartM.length().distance)
                }
        }

        // Add edges inwards to the end
        createEdgeEndVertices(endEdge, VertexDirection.IN).forEachIndexed { index, (preEndVertex, postEndVertex) ->
            graph.addVertex(preEndVertex)
            graph.addVertex(postEndVertex)

            PartialTrackEdge(endEdge.id, EdgeDirection.UP, preEndM, index)
                .also { edge ->
                    println("Adding tmp edge: type=preEnd edge=$edge endVertex=$endVertex preEndVertex=$preEndVertex")
                    graph.addEdge(preEndVertex, endVertex, edge)
                    graph.setEdgeWeight(edge, preEndM.length().distance)
                }
            PartialTrackEdge(endEdge.id, EdgeDirection.DOWN, postEndM, index)
                .also { edge ->
                    println("Adding tmp edge: type=postEnd edge=$edge endVertex=$endVertex postEndVertex=$postEndVertex")
                    graph.addEdge(postEndVertex, endVertex, edge)
                    graph.setEdgeWeight(edge, postEndM.length().distance)
                }
        }

        return graph
    }

    private fun createEdgeEndVertices(
        edge: DbLayoutEdge,
        vertexDirection: VertexDirection,
    ): List<Pair<RoutingVertex, RoutingVertex>> =
        if (edge.isSwitchInnerLink()) {
            val data = edgeData.getValue(edge.id)
            println("createEdgeEndVertices: switchLinks=${data.switchConnections}")
            data.switchConnections.entries.map { (alignment, edgeDirection) ->
                val alignmentStart = SwitchJointVertex(alignment.id, alignment.jointNumbers.first(), vertexDirection)
                val alignmentEnd = SwitchJointVertex(alignment.id, alignment.jointNumbers.last(), vertexDirection)
                if (edgeDirection == EdgeDirection.UP) {
                    alignmentStart to alignmentEnd
                } else {
                    alignmentEnd to alignmentStart
                }
            }
        } else {
            val incomingPreVertex = requireNotNull(createIncomingTrackConnectionVertex(edge.startNode)) {
                "Failed to resolve incoming vertex from edge start: edge=${edge.id} startNode=${edge.startNode}"
            }
            val incomingPostVertex = requireNotNull(createIncomingTrackConnectionVertex(edge.endNode)) {
                "Failed to resolve incoming vertex from edge end: edge=${edge.id} endNode=${edge.endNode}"
            }
            listOf(
                if (vertexDirection == VertexDirection.IN) {
                    incomingPreVertex to incomingPostVertex
                } else {
                    incomingPreVertex.reverse() to incomingPostVertex.reverse()
                }
            )
        }

}

fun buildGraph(
    trackGeoms: List<DbLocationTrackGeometry>,
    switches: List<LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): RoutingGraph {
    val edgeData = createEdgeData(trackGeoms, switches, structures)
    val edges = edgeData.values.map { edgeData -> edgeData.edge }.toSet()
    val nodes = edges.flatMap { edge-> listOf(edge.startNode.node, edge.endNode.node) }.toSet()
    val switchInternalEdges = edgeData.entries
        .flatMap { (edgeId, data) ->
            data.switchConnections.entries.map { (alignment, direction) -> alignment to (edgeId to direction) }
        }
        .groupBy({ it.first }, {it.second })

    switchInternalEdges
        .get(RoutingSwitchAlignment(IntId(8851), listOf(4,5,2).map(::JointNumber)))
//        .also { edges -> println("Switch internal edges for switch INT_8851: $edges") }

    // Graph types: https://jgrapht.org/guide/UserOverview#graph-structures
    val jgraph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)

    val switchVertices = switches.flatMap { s -> createSwitchVertices(s, structures) }
    val trackVertices = trackGeoms.flatMap(::createTrackEndVertices)
    (switchVertices.asSequence() + trackVertices.asSequence()).forEach { v ->
        jgraph.addVertex(v)
    }

    val switchConnections = switches.flatMap { s -> createThroughSwitchConnections(s, structures) }
    val directConnections = nodes.flatMap(::createDirectConnections)
    val trackConnections = edges.flatMap(::createTrackConnections)
    (switchConnections.asSequence() + directConnections.asSequence() + trackConnections.asSequence()).forEach { (connection, edge) ->
        try {
            if (jgraph.addEdge(connection.from, connection.to, edge)) {
                jgraph.setEdgeWeight(edge, connection.length)
            }
        }
        catch (e: Exception) {
            logger.error("Error adding edge: edge=$edge connection=$connection error=${e.message}")
        }
    }
    return RoutingGraph(
        jgraph = jgraph,
        edgeData = edgeData,
        switchInternalEdges = switchInternalEdges,
    )
}

fun createEdgeData(
    trackGeoms: List<DbLocationTrackGeometry>,
    switches: List<LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): Map<IntId<LayoutEdge>, RouteEdgeData> {
    val switchesById = switches.associateBy { it.id as IntId }
    return trackGeoms
        .flatMap { geom -> geom.edgesWithM.map { (e, m) -> e to TrackSection(geom.trackId, m) } }
        .groupBy { it.first.id }
        .mapValues { (_, edgesAndTrackIds) ->
            val switchConnections = resolveSwitchAlignments(edgesAndTrackIds[0].first, switchesById, structures)
            RouteEdgeData(edgesAndTrackIds[0].first, edgesAndTrackIds.map { it.second }.toSet(), switchConnections)
        }
}

fun resolveSwitchAlignments(
    edge: DbLayoutEdge,
    switches: Map<IntId<LayoutSwitch>, LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): Map<RoutingSwitchAlignment, EdgeDirection> {
    val switchId = edge.startNode.switchIn?.id?.takeIf { edge.endNode.switchIn?.id == it }
    val startJoint = edge.startNode.switchIn?.jointNumber
    val endJoint = edge.endNode.switchIn?.jointNumber
//    if (switchId?.intValue == 8851) println("Resolving switch alignment for edge ${edge.id} with switchId=$switchId startJoint=$startJoint endJoint=$endJoint")
    return if (switchId != null && startJoint != null && endJoint != null) {
        val structure = structures.getValue(switches.getValue(switchId).switchStructureId)
        val alignments = structure.alignments
            .map { it.jointNumbers }
            .filter { it.contains(startJoint) && it.contains(endJoint) }
//        if (switchId.intValue == 8851) println(" - Alignment: $alignment")
        alignments.associate { alignment ->
            val direction =
                if (alignment.indexOf(startJoint) < alignment.indexOf(endJoint)) EdgeDirection.UP else EdgeDirection.DOWN
            RoutingSwitchAlignment(switchId, alignment) to direction
        }
//        .also { if (switchId.intValue == 8851) println(" - result: $it") }
    } else emptyMap()
}

fun <A, B> Pair<A,A>.map(transform: (A) -> B): Pair<B,B> = transform(first) to transform(second)

fun createSwitchVertices(switch: LayoutSwitch, structures: Map<IntId<SwitchStructure>, SwitchStructure>): List<SwitchJointVertex> {
    val structure = structures.getValue(switch.switchStructureId)
    val id = switch.id as? IntId ?: error { "Switch must be stored in DB and hence have a db ID: switch=$switch" }
    return structure.endJointNumbers.flatMap { jointNumber ->
        listOf(
            SwitchJointVertex(id, jointNumber, VertexDirection.IN),
            SwitchJointVertex(id, jointNumber, VertexDirection.OUT),
        )
    }
}

fun createThroughSwitchConnections(switch: LayoutSwitch, structures: Map<IntId<SwitchStructure>, SwitchStructure>): List<Pair<RoutingConnection, SwitchInternalEdge>> {
    val structure = structures.getValue(switch.switchStructureId)
    val id = switch.id as? IntId ?: error { "Switch must be stored in DB and hence have a db ID: switch=$switch" }
    return structure.alignments.flatMap { alignment ->
        val connectionStraight = RoutingConnection(
            from = SwitchJointVertex(id, alignment.jointNumbers.first(), VertexDirection.IN),
            to = SwitchJointVertex(id, alignment.jointNumbers.last(), VertexDirection.OUT),
            length = alignment.length(),
        )
        val connectionReverse = RoutingConnection(
            from = SwitchJointVertex(id, alignment.jointNumbers.last(), VertexDirection.IN),
            to = SwitchJointVertex(id, alignment.jointNumbers.first(), VertexDirection.OUT),
            length = alignment.length(),
        )
        val edgeStraight = SwitchInternalEdge(RoutingSwitchAlignment(id, alignment.jointNumbers), EdgeDirection.UP)
        val edgeReverse = edgeStraight.reverse()
        listOf(connectionStraight to edgeStraight, connectionReverse to edgeReverse)
    }
}

fun createTrackEndVertices(geometry: DbLocationTrackGeometry): List<TrackBoundaryVertex> =
    listOf(geometry.startNode, geometry.endNode).flatMap { connection ->
        connection?.innerPort?.let { port ->
            if (port is TrackBoundary) listOf(
                TrackBoundaryVertex(port.id, port.type, VertexDirection.IN),
                TrackBoundaryVertex(port.id, port.type, VertexDirection.OUT),
            ) else null
        } ?: emptyList()
    }

fun createDirectConnections(node: DbLayoutNode): List<Pair<RoutingConnection, DirectConnectionEdge>> {
    return when (node) {
        is DbSwitchNode -> if (node.portA.id != node.portB?.id && node.portB != null) {
            // Dual switch nodes: when coming out of one switch, you can move straight in to the next one
            val connection = RoutingConnection(
                from = SwitchJointVertex(node.portA.id, node.portA.jointNumber, VertexDirection.OUT),
                to = SwitchJointVertex(node.portB.id, node.portB.jointNumber, VertexDirection.IN),
                length = 0.0,
            )
            val edge = DirectConnectionEdge(node.id, EdgeDirection.UP)
            listOf(connection to edge, connection.reverse() to edge.reverse())
        } else {
            // Single switch node internal navigation is handled by switch-internal connections
            emptyList()
        }
        is DbTrackBoundaryNode -> if (node.portB != null) {
            // Dual-track boundaries: when coming out of one track, you can move straight in to the next one
            val forward = RoutingConnection(
                from = TrackBoundaryVertex(node.portA.id, node.portA.type, VertexDirection.OUT),
                to = TrackBoundaryVertex(node.portB.id, node.portB.type, VertexDirection.IN),
                length = 0.0,
            )
            val backward = RoutingConnection(
                from = TrackBoundaryVertex(node.portB.id, node.portB.type, VertexDirection.OUT),
                to = TrackBoundaryVertex(node.portA.id, node.portA.type, VertexDirection.IN),
                length = 0.0,
            )
            val edge = DirectConnectionEdge(node.id, EdgeDirection.UP)
            listOf(forward to edge, backward to edge.reverse())
        } else {
            // Single track boundary is a dead end, but allow turning around here (only out->in, not in->out)
            listOf(
                RoutingConnection(
                    from = TrackBoundaryVertex(node.portA.id, node.portA.type, VertexDirection.OUT),
                    to = TrackBoundaryVertex(node.portA.id, node.portA.type, VertexDirection.IN),
                    length = 0.0,
                ) to DirectConnectionEdge(node.id, EdgeDirection.UP),
            )
        }
    }
}

fun createTrackConnections(edge: DbLayoutEdge): List<Pair<RoutingConnection, TrackEdge>> = edge
    // Switch inner links are handled separately: ignore them here
    .takeIf { !it.isSwitchInnerLink() }
    ?.let { e ->
        val incomingStartVertex = createIncomingTrackConnectionVertex(e.startNode)
        val outgoingEndVertex = createIncomingTrackConnectionVertex(e.endNode)?.reverse()
        if (incomingStartVertex != null && outgoingEndVertex != null) {
            val connection = RoutingConnection(incomingStartVertex, outgoingEndVertex, e.length.distance)
            val edge = TrackEdge(e.id, EdgeDirection.UP)
            listOf(connection to edge, connection.reverse() to edge.reverse())
        } else {
            logger.warn("Cannot route via edge with invalid switch linking: edgeId=${e.id} startNode=${e.startNode} endNode=${e.endNode}")
            null
        }
    } ?: emptyList()

private fun createIncomingTrackConnectionVertex(nodeConnection: DbNodeConnection) =
     when (nodeConnection.node) {
        is DbSwitchNode -> {
            // Inner switch connections are handled elsewhere and multi-switch nodes are already connected via
            // direct connections. Hence, only pure outer connections need processing here.
            nodeConnection.switchOut?.takeIf { nodeConnection.switchIn == null }?.let { link ->
                SwitchJointVertex(link.id, link.jointNumber, VertexDirection.OUT)
            }
        }
        is DbTrackBoundaryNode -> {
            // Multi-track boundaries are already connected via direct connections. Hence, we only need to
            // connect from the inner track boundary.
            nodeConnection.trackBoundaryIn?.let { boundary ->
                TrackBoundaryVertex(boundary.id, boundary.type, VertexDirection.IN)
            }
        }
    }
