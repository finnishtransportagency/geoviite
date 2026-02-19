package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.length
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import org.jgrapht.Graph
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.AsGraphUnion
import org.jgrapht.graph.DirectedWeightedMultigraph

data class ClosestTrackPoint(
    val locationTrackId: IntId<LocationTrack>,
    val requestedLocation: Point,
    val trackLocation: AlignmentPoint<LocationTrackM>,
    val distance: Double,
)

data class RouteResult(
    val startConnection: ClosestTrackPoint,
    val endConnection: ClosestTrackPoint,
    val route: Route?,
) {
    val totalLength: Double?
        get() = route?.totalLength?.let { it + startConnection.distance + endConnection.distance }
}

data class Route(val sections: List<TrackSection>) {
    val totalLength: Double
        get() = sections.sumOf { it.length }
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
data class SwitchInternalEdge(val switchId: IntId<LayoutSwitch>, val alignment: List<JointNumber>, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): SwitchInternalEdge = copy(direction = direction.reverse())
}

data class TrackBoundaryVertex(val trackId: IntId<LocationTrack>, val type: TrackBoundaryType, val direction: VertexDirection): RoutingVertex() {
    override fun reverse(): TrackBoundaryVertex = copy(direction = direction.reverse())
}

data class TrackMidPointVertex(val trackId: IntId<LocationTrack>, val m: LineM<LocationTrackM>, val direction: VertexDirection): RoutingVertex() {
    override fun reverse(): TrackMidPointVertex = error("Midpoints are not directional")
}

data class TrackEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): TrackEdge = copy(direction = direction.reverse())
}

data class PartialTrackEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection, val mRange: Range<LineM<EdgeM>>): RoutingEdge() {
    override fun reverse(): PartialTrackEdge = copy(direction = direction.reverse())
}

data class DirectConnectionEdge(val nodeId: IntId<LayoutNode>, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): DirectConnectionEdge = copy(direction = direction.reverse())
}


data class RoutingGraph2(
    private val jgraph: Graph<RoutingVertex, RoutingEdge>,
    private val edgeData: Map<IntId<LayoutEdge>, DbEdgeData>,
    private val structures: Map<IntId<SwitchStructure>, SwitchStructure>,
    private val switchInternalEdges: Map<IntId<LayoutSwitch>, List<IntId<LayoutEdge>>>,
) {
    fun getVertices(): Set<RoutingVertex> = jgraph.vertexSet()

    fun getEdges(): Map<RoutingEdge, Pair<RoutingVertex, RoutingVertex>> = jgraph.edgeSet().associateWith { edge ->
        jgraph.getEdgeSource(edge) to jgraph.getEdgeTarget(edge)
    }

    fun findPath(start: LocationTrackCacheHit, end: LocationTrackCacheHit): List<TrackSection>? {
        val edges = findPathEdges(start, end)
        return edges?.flatMap { edge ->
            toTrackSections(edge, setOf(start.track.id as IntId, end.track.id as IntId))
        }.also { println("Routed: edges=${edges?.size} trackSections=${it?.size}") }
    }

    // TODO: We could easily add the direction in here as well
    private fun toTrackSections(edge: RoutingEdge, favoredTrackIds: Set<IntId<LocationTrack>>): List<TrackSection> =
        when (edge) {
            is TrackEdge -> listOfNotNull(findTrack(edge.edgeId, favoredTrackIds))
            is PartialTrackEdge -> listOfNotNull(
                findTrack(edge.edgeId, favoredTrackIds)?.let { full ->
                    TrackSection(full.id, edge.mRange.map { it.toLocationTrackM(full.mRange.min) } )
                }
            )
            is SwitchInternalEdge -> switchInternalEdges[edge.switchId]
                ?.mapNotNull { edgeId -> findTrack(edgeId, favoredTrackIds) }
                ?: emptyList()
            is DirectConnectionEdge -> emptyList()
        }

    private fun findTrack(edgeId: IntId<LayoutEdge>, favoredTrackIds: Set<IntId<LocationTrack>>): TrackSection? =
        edgeData[edgeId]
            ?.tracks
            ?.let { tracks -> tracks.firstOrNull { favoredTrackIds.contains(it.id) } ?: tracks.firstOrNull() }

    fun findPathEdges(start: LocationTrackCacheHit, end: LocationTrackCacheHit): List<RoutingEdge>? {
        val (startEdge, startMRange) = start.getEdge()
        val (endEdge, endMRange) = end.getEdge()
        val startVertex = TrackMidPointVertex(start.track.id as IntId, start.closestPoint.m, VertexDirection.IN)
        val endVertex = TrackMidPointVertex(end.track.id as IntId, end.closestPoint.m, VertexDirection.OUT)

        return when {
            // Special case: no distance between points -> no need for path
            abs(start.closestPoint.m - end.closestPoint.m).distance < LAYOUT_M_DELTA -> emptyList()

            // Special case: on the same edge -> a direct single-step path along the edge is the shortest
            startEdge.id == endEdge.id -> listOf(
                when {
                    (start.closestPoint.m < end.closestPoint.m) -> PartialTrackEdge(
                        startEdge.id,
                        EdgeDirection.UP,
                        Range(start.closestPoint.m, end.closestPoint.m).map { it.toEdgeM(startMRange.min) }
                    )
                    else -> PartialTrackEdge(
                        startEdge.id,
                        EdgeDirection.DOWN,
                        Range(end.closestPoint.m, start.closestPoint.m).map { it.toEdgeM(endMRange.min) }
                    )
                }
            )

            // The actual pathfinding case
            else -> {
                // Create a second temp graph for the things in this routing
                val tmpGraph = buildTempRoutingGraph(
                    startVertex,
                    endVertex,
                    startEdge to startMRange,
                    endEdge to endMRange,
                    structures
                )
                // Route the start->end in a union graph with the main graph + the temp additions
                val dijkstra = DijkstraShortestPath(AsGraphUnion(jgraph, tmpGraph))
                dijkstra.getPath(startVertex, endVertex)?.edgeList?.filterNotNull()
            }
        }
    }
}

fun buildGraph(
    trackGeoms: List<DbLocationTrackGeometry>,
    switches: List<LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): RoutingGraph2 {
    val edgeData =
        trackGeoms
            .flatMap { geom -> geom.edgesWithM.map { (e, m) -> e to TrackSection(geom.trackId, m) } }
            .groupBy { it.first.id }
            .mapValues { (_, edgesAndTrackIds) ->
                DbEdgeData(edgesAndTrackIds[0].first, edgesAndTrackIds.map { it.second }.toSet())
            }

    val edges = edgeData.values.map { edgeData -> edgeData.edge }.toSet()
    val nodes = edges.flatMap { edge-> listOf(edge.startNode.node, edge.endNode.node) }.toSet()

    // Graph types: https://jgrapht.org/guide/UserOverview#graph-structures
    val jgraph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)

    val switchVertices = switches.flatMap { s -> createSwitchVertices(s, structures) }
    val trackVertices = trackGeoms.flatMap(::createTrackEndVertices)
    (switchVertices.asSequence() + trackVertices.asSequence()).forEach { v ->
//        if (v is SwitchJointVertex && v.switchId == IntId<LayoutSwitch>(6432)) println("Adding vertex: vertex=$v")
        jgraph.addVertex(v)
    }

    val switchConnections = switches.flatMap { s -> createThroughSwitchConnections(s, structures) }
    println(nodes)
    val directConnections = nodes.flatMap(::createDirectConnections)
    val trackConnections = edges.flatMap(::createTrackConnections)
    (switchConnections.asSequence() + directConnections.asSequence() + trackConnections.asSequence()).forEach { (connection, edge) ->
//        println("Adding edge: edge=$edge connection=$connection")
        try {
            if (jgraph.addEdge(connection.from, connection.to, edge)) {
                jgraph.setEdgeWeight(edge, connection.length)
            } else {
                println("Dropping duplicate edge: edge=$edge connection=$connection")
            }
        }
        catch (e: Exception) {
            println("Error adding edge: edge=$edge connection=$connection error=${e.message}")
        }
    }

    return RoutingGraph2(
        jgraph = jgraph,
        edgeData = edgeData,
        structures = structures,
        switchInternalEdges = edges.filter { it.isSwitchInnerLink() }
            .groupBy({ requireNotNull(it.startNode.switchIn).id}, {it.id})
    )
}

fun <A, B> Pair<A,A>.map(transform: (A) -> B): Pair<B,B> = transform(first) to transform(second)

fun buildTempRoutingGraph(
    startVertex: TrackMidPointVertex,
    endVertex: TrackMidPointVertex,
    startEdgeWithM: Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>,
    endEdgeWithM: Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>? {
    val (startEdge, startMRange) = startEdgeWithM
    val (endEdge, endMRange) = endEdgeWithM

    val (preStartVertex, postStartVertex) = createEdgeEndVertices(startEdge, VertexDirection.OUT, structures)
    val (preEndVertex, postEndVertex) = createEdgeEndVertices(endEdge, VertexDirection.IN, structures)
    val (preStartM, postStartM) = startMRange.split(startVertex.m)
        .map { r -> r.map { m -> m.toEdgeM(startMRange.min) } }
    val (preEndM, postEndM) = endMRange.split(endVertex.m)
        .map { r -> r.map { m -> m.toEdgeM(endMRange.min) } }

    val graph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)

    // Add all the vertices. Note: only the start/end are actually new -- the others exist in the main graph
    // Nonetheless, we need them all in the temp graph to add in the temp edges
    listOfNotNull(startVertex, endVertex, preStartVertex, postStartVertex, preEndVertex, postEndVertex)
        .also { println("Adding tmp vertices: $it") }
        .forEach { v -> graph.addVertex(v) }

    // Add edges outwards from the start
    if (preStartVertex != null) PartialTrackEdge(startEdge.id, EdgeDirection.DOWN, preStartM)
        .also { edge ->
            println("Adding temp edge: edge=$edge from=$startVertex to=$preStartVertex")
            graph.addEdge(startVertex, preStartVertex, edge)
            graph.setEdgeWeight(edge, preStartM.length().distance)
        }
    if (postStartVertex != null) PartialTrackEdge(startEdge.id, EdgeDirection.UP, postStartM)
        .also { edge ->
            println("Adding temp edge: edge=$edge from=$startVertex to=$postStartVertex")
            graph.addEdge(startVertex, postStartVertex, edge)
            graph.setEdgeWeight(edge, postStartM.length().distance)
        }

    // Add edges inwards to the end
    if (preEndVertex != null) PartialTrackEdge(endEdge.id, EdgeDirection.UP, preEndM)
        .also { edge ->
            println("Adding temp edge: edge=$edge from=$preEndVertex to=$endVertex")
            graph.addEdge(preEndVertex, endVertex, edge)
            graph.setEdgeWeight(edge, preEndM.length().distance)
        }
    if (postEndVertex != null) PartialTrackEdge(endEdge.id, EdgeDirection.DOWN, postEndM)
        .also { edge ->
            println("Adding temp edge: edge=$edge from=$postEndVertex to=$endVertex")
            graph.addEdge(postEndVertex, endVertex, edge)
            graph.setEdgeWeight(edge, postEndM.length().distance)
        }

    return graph
}

private fun createEdgeEndVertices(
    edge: DbLayoutEdge,
    direction: VertexDirection,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): Pair<RoutingVertex?, RoutingVertex?> =
    if (edge.isSwitchInnerLink()) {
        requireNotNull(edge.startNode.switchIn?.id)
        // TODO: Handle routing to/from inner switch connections
        null to null
    } else {
        val incomingPreVertex = createIncomingTrackConnectionVertex(edge.startNode)
        val incomingPostVertex = createIncomingTrackConnectionVertex(edge.endNode)
        if (direction == VertexDirection.IN) {
            incomingPreVertex to incomingPostVertex
        } else {
            incomingPreVertex?.reverse() to incomingPostVertex?.reverse()
        }
    }

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
        val edgeStraight = SwitchInternalEdge(id, alignment.jointNumbers, EdgeDirection.UP)
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
            // TODO: Log error: this edge won't work for routing. It's possible when the track is poorly linked (old data).
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
