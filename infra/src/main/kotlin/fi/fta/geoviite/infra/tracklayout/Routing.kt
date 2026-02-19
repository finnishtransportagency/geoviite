package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import org.jgrapht.Graph
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.AsGraphUnion
import org.jgrapht.graph.DirectedWeightedMultigraph
import java.util.*

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
//    val totalLength: Double?
//        get() = route?.totalLength?.let { it + startConnection.distance + endConnection.distance }
}

data class Route(
    val path: List<RoutingEdge>,
//val sections: List<TrackSection>
) {
//    val totalLength: Double
//        get() = sections.sumOf { it.length }
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

//data class RoutingVertex(val nodeId: IntId<LayoutNode>, val port: NodePort, val direction: VertexDirection)

//data class RoutingEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection)


//data class RoutingGraph(val graph: LayoutGraph) {
//    private val jgraph: Graph<RoutingVertex, RoutingEdge> = buildJGraph(graph)
//    private val dijkstra = DijkstraShortestPath(jgraph)
//
//    fun findPath(startNode: IntId<LayoutNode>, endNode: IntId<LayoutNode>): List<LayoutGraphEdge>? =
//        dijkstra.getPath(startNode, endNode)?.edgeList?.filterNotNull()
//}

//private fun toVertices(node: LayoutNode): List<RoutingVertex> =
//    when(node) {
//        is DbSwitchNode ->  listOfNotNull(
//            RoutingVertex(node.id, node.portA, VertexDirection.IN),
//            RoutingVertex(node.id, node.portA, VertexDirection.OUT),
//            node.portB?.let { RoutingVertex(node.id, it, VertexDirection.IN) },
//            node.portB?.let { RoutingVertex(node.id, it, VertexDirection.OUT) },
//        )
//        is DbTrackBoundaryNode -> listOf(
//            RoutingVertex(node.id, node.portA, VertexDirection.IN),
//            RoutingVertex(node.id, node.portA, VertexDirection.OUT),
//        )
//        else -> error { "Routing node must be either db-switch or db-track-boundary: node=$node" }
//    }

data class RoutingGraph2(
    private val jgraph: Graph<RoutingVertex, RoutingEdge>,
) {
    fun getVertices(): Set<RoutingVertex> = jgraph.vertexSet()

    fun getEdges(): Map<RoutingEdge, Pair<RoutingVertex, RoutingVertex>> = jgraph.edgeSet().associateWith { edge ->
        jgraph.getEdgeSource(edge) to jgraph.getEdgeTarget(edge)
    }

    fun findPath(start: LocationTrackCacheHit, end: LocationTrackCacheHit): List<RoutingEdge>? {
        // Create a second temp graph for the things in this routing
        val tmpGraph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)
        val (startEdge, startMRange) = start.getEdge()
        val (endEdge, endMRange) = end.getEdge()
        val startVertex = TrackMidPointVertex(start.track.id as IntId)
        val endVertex = TrackMidPointVertex(end.track.id as IntId)
        val outgoingPreStartVertex = toIncomingEndVertex(startEdge.startNode)?.reverse() ?: return null
        val outgoingPostStartVertex = toIncomingEndVertex(startEdge.endNode)?.reverse() ?: return null
        val incomingPreEndVertex = toIncomingEndVertex(startEdge.startNode) ?: return null
        val incomingPostEndVertex = toIncomingEndVertex(startEdge.endNode) ?: return null
        sequenceOf(startVertex, endVertex, outgoingPreStartVertex, outgoingPostStartVertex, incomingPreEndVertex, incomingPostEndVertex)
            .forEach { v -> tmpGraph.addVertex(v) }

        TrackHalfEdge(startEdge.id, EdgeDirection.DOWN).also { edge ->
            try {
                println("Adding tmp edge: edge=$edge start=$startVertex end=$outgoingPreStartVertex")
                tmpGraph.addEdge(startVertex, outgoingPreStartVertex, edge)
                tmpGraph.setEdgeWeight(edge, start.closestPoint.m.distance - startMRange.min.distance)
            } catch (e: Exception) {
                println("Error adding tmp edge: edge=$edge error=${e.message}")
            }
        }
        TrackHalfEdge(startEdge.id, EdgeDirection.UP).also { edge ->
            try {
                println("Adding tmp edge: edge=$edge start=$startVertex end=$outgoingPostStartVertex")
                tmpGraph.addEdge(startVertex, outgoingPostStartVertex, edge)
                tmpGraph.setEdgeWeight(edge, startMRange.max.distance - start.closestPoint.m.distance)
            }
            catch (e: Exception) {
                println("Error adding tmp edge: edge=$edge error=${e.message}")
            }
        }
        TrackHalfEdge(endEdge.id, EdgeDirection.UP).also { edge ->
            try {
                println("Adding tmp edge: edge=$edge start=$incomingPreEndVertex end=$endVertex")
                tmpGraph.addEdge(incomingPreEndVertex, endVertex, edge)
                tmpGraph.setEdgeWeight(edge, end.closestPoint.m.distance - endMRange.min.distance)
            } catch (e: Exception) {
                println("Error adding tmp edge: edge=$edge error=${e.message}")
            }
        }
        TrackHalfEdge(endEdge.id, EdgeDirection.DOWN).also { edge ->
            try {
                println("Adding tmp edge: edge=$edge start=$incomingPostEndVertex end=$endVertex")
                tmpGraph.addEdge(incomingPostEndVertex, endVertex, edge)
                tmpGraph.setEdgeWeight(edge, endMRange.max.distance - end.closestPoint.m.distance)
            }
            catch (e: Exception) {
                println("Error adding tmp edge: edge=$edge error=${e.message}")
            }
        }

        println("Trying to route: startVertex=$startVertex endVertex=$endVertex")
        val dijkstra = DijkstraShortestPath(AsGraphUnion(jgraph, tmpGraph))
        return dijkstra.getPath(startVertex, endVertex)?.edgeList?.filterNotNull()
            .also { println("Result path: $it") }
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
    )
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

data class TrackMidPointVertex(val trackId: IntId<LocationTrack>, val uuid: String = UUID.randomUUID().toString()): RoutingVertex() {
    override fun reverse(): TrackMidPointVertex = error("Midpoints are not directional")
}

data class TrackEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): TrackEdge = copy(direction = direction.reverse())
}

data class TrackHalfEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection, val randomId: String = UUID.randomUUID().toString()): RoutingEdge() {
    override fun reverse(): TrackHalfEdge = copy(direction = direction.reverse())
}

data class DirectConnectionEdge(val nodeId: IntId<LayoutNode>, val direction: EdgeDirection): RoutingEdge() {
    override fun reverse(): DirectConnectionEdge = copy(direction = direction.reverse())
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
        val incomingStartVertex = toIncomingEndVertex(e.startNode)
        val outgoingEndVertex = toIncomingEndVertex(e.endNode)?.reverse()
        if (incomingStartVertex != null && outgoingEndVertex != null) {
            val connection = RoutingConnection(incomingStartVertex, outgoingEndVertex, e.length.distance)
            val edge = TrackEdge(e.id, EdgeDirection.UP)
            listOf(connection to edge, connection.reverse() to edge.reverse())
        } else {
            null
        }
    } ?: emptyList()

private fun toIncomingEndVertex(nodeConnection: DbNodeConnection) =
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

// For immutable edit patterns, see: https://jgrapht.org/guide/UserOverview#graph-wrappers
