package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.combine
import fi.fta.geoviite.infra.math.length
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.tracklayout.EdgeDirection.DOWN
import fi.fta.geoviite.infra.tracklayout.EdgeDirection.UP
import fi.fta.geoviite.infra.tracklayout.VertexDirection.IN
import fi.fta.geoviite.infra.tracklayout.VertexDirection.OUT
import fi.fta.geoviite.infra.util.produceIf
import java.util.*
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

data class RouteResult(val startConnection: ClosestTrackPoint, val endConnection: ClosestTrackPoint, val route: Route) {
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
    val direction: EdgeDirection,
) {
    val length: Double
        get() = mRange.length().distance

    fun reverse(): RouteSection = copy(direction = direction.reverse())
}

data class RouteEdgeData(
    val edge: DbLayoutEdge,
    val tracks: Set<TrackSection>,
    val switchConnections: Map<RoutingSwitchAlignment, SwitchEdge>,
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

    fun reverse(): VertexDirection =
        when (this) {
            IN -> OUT
            OUT -> IN
        }
}

enum class EdgeDirection {
    UP,
    DOWN;

    fun reverse(): EdgeDirection =
        when (this) {
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

@Suppress("EqualsOrHashCode")
data class SwitchJointVertex(
    val switchId: IntId<LayoutSwitch>,
    val jointNumber: JointNumber,
    val direction: VertexDirection,
) : RoutingVertex() {
    override fun reverse(): SwitchJointVertex = copy(direction = direction.reverse())

    private val hash = Objects.hash(switchId.intValue, jointNumber.intValue, direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class TrackBoundaryVertex(
    val trackId: IntId<LocationTrack>,
    val type: TrackBoundaryType,
    val direction: VertexDirection,
) : RoutingVertex() {
    override fun reverse(): TrackBoundaryVertex = copy(direction = direction.reverse())

    private val hash = Objects.hash(trackId.intValue, type, direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class TrackMidPointVertex(
    val trackId: IntId<LocationTrack>,
    val m: LineM<LocationTrackM>,
    val direction: VertexDirection,
) : RoutingVertex() {
    override fun reverse(): TrackMidPointVertex = copy(direction = direction.reverse())

    private val hash = Objects.hash(trackId.intValue, m.distance, direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class TrackEdge(val edgeId: IntId<LayoutEdge>, val direction: EdgeDirection) : RoutingEdge() {
    override fun reverse(): TrackEdge = copy(direction = direction.reverse())

    private val hash = Objects.hash(edgeId.intValue, direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class PartialTrackEdge(
    val edgeId: IntId<LayoutEdge>,
    val direction: EdgeDirection,
    val mRange: Range<LineM<EdgeM>>,
) : RoutingEdge() {
    override fun reverse(): PartialTrackEdge = copy(direction = direction.reverse())

    private val hash = Objects.hash(edgeId.intValue, mRange.min.distance, mRange.max.distance, direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class DirectConnectionEdge(val nodeId: IntId<LayoutNode>, val direction: EdgeDirection) : RoutingEdge() {
    override fun reverse(): DirectConnectionEdge = copy(direction = direction.reverse())

    private val hash = Objects.hash(nodeId.intValue, direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class SwitchInternalEdge(val alignment: RoutingSwitchAlignment, val direction: EdgeDirection) : RoutingEdge() {
    override fun reverse(): SwitchInternalEdge = copy(direction = direction.reverse())

    private val hash = Objects.hash(alignment.hashCode(), direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class PartialSwitchInternalEdge(
    val alignment: RoutingSwitchAlignment,
    val direction: EdgeDirection,
    val mRange: Range<LineM<SwitchStructureAlignmentM>>,
) : RoutingEdge() {
    override fun reverse(): PartialSwitchInternalEdge = copy(direction = direction.reverse())

    private val hash = Objects.hash(alignment.hashCode(), mRange.min.distance, mRange.max.distance, direction)

    override fun hashCode(): Int = hash
}

@Suppress("EqualsOrHashCode")
data class RoutingSwitchAlignment(val id: IntId<LayoutSwitch>, val alignment: SwitchStructureAlignment) {

    val jointNumbers: List<JointNumber>
        get() = alignment.jointNumbers

    val length: Double
        get() = alignment.length()

    private val hash = Objects.hash(id.intValue, alignment.jointNumbers)

    override fun hashCode(): Int = hash
}

data class SwitchEdge(
    val id: IntId<LayoutEdge>,
    val direction: EdgeDirection,
    val mRange: Range<LineM<SwitchStructureAlignmentM>>,
) {
    fun toSwitchM(edgeM: LineM<EdgeM>): LineM<SwitchStructureAlignmentM> =
        LineM(
            when (direction) {
                UP -> mRange.min.distance + edgeM.distance
                DOWN -> mRange.max.distance - edgeM.distance
            }
        )

    fun toEdgeM(switchM: LineM<SwitchStructureAlignmentM>): LineM<EdgeM> =
        LineM(
            when (direction) {
                UP -> switchM.distance - mRange.min.distance
                DOWN -> mRange.max.distance - switchM.distance
            }
        )
}

data class RoutingGraph(
    private val jgraph: Graph<RoutingVertex, RoutingEdge>,
    private val edgeData: Map<IntId<LayoutEdge>, RouteEdgeData>,
    private val switchInternalEdges: Map<RoutingSwitchAlignment, List<SwitchEdge>>,
) {
    fun getVertices(): Set<RoutingVertex> = jgraph.vertexSet()

    fun getEdges(): Map<RoutingEdge, Pair<RoutingVertex, RoutingVertex>> =
        jgraph.edgeSet().associateWith { edge -> jgraph.getEdgeSource(edge) to jgraph.getEdgeTarget(edge) }

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
            is PartialTrackEdge ->
                listOfNotNull(findRouteSection(edge.edgeId, edge.direction, favoredTrackIds, edge.mRange))
            is PartialSwitchInternalEdge ->
                findSwitchRouteSections(edge.alignment, edge.direction, favoredTrackIds, edge.mRange)
            is SwitchInternalEdge -> findSwitchRouteSections(edge.alignment, edge.direction, favoredTrackIds)
            is DirectConnectionEdge -> emptyList()
        }

    private fun findSwitchRouteSections(
        alignment: RoutingSwitchAlignment,
        edgeDirection: EdgeDirection,
        favoredTrackIds: Set<IntId<LocationTrack>>,
        mRangeLimit: Range<LineM<SwitchStructureAlignmentM>>? = null,
    ) =
        switchInternalEdges[alignment]?.mapNotNull { switchEdge ->
            val edgeDirection = if (edgeDirection == UP) switchEdge.direction else switchEdge.direction.reverse()
            val edgeRangeLimit =
                mRangeLimit?.let { switchM ->
                    val edgeMin = switchEdge.toEdgeM(switchM.min)
                    val edgeMax = switchEdge.toEdgeM(switchM.max)
                    Range(minOf(edgeMin, edgeMax), maxOf(edgeMin, edgeMax)).takeIf {
                        it.length().distance >= LAYOUT_M_DELTA
                    }
                }
            findRouteSection(switchEdge.id, edgeDirection, favoredTrackIds, edgeRangeLimit)
        } ?: emptyList()

    private fun findRouteSection(
        edgeId: IntId<LayoutEdge>,
        direction: EdgeDirection,
        favoredTrackIds: Set<IntId<LocationTrack>>,
        mRangeLimit: Range<LineM<EdgeM>>? = null,
    ): RouteSection? =
        edgeData
            .getValue(edgeId)
            .let { data ->
                data.tracks.mapNotNull { (id, mRange) ->
                    val limitedRange =
                        if (mRangeLimit == null) {
                            mRange
                        } else {
                            val edgeMin = maxOf(mRangeLimit.min, LineM(0.0)).toLocationTrackM(mRange.min)
                            val edgeMax = minOf(mRangeLimit.max, data.edge.length).toLocationTrackM(mRange.min)
                            produceIf((edgeMax - edgeMin).distance >= LAYOUT_M_DELTA) { Range(edgeMin, edgeMax) }
                        }
                    limitedRange?.let { RouteSection(id, it, direction) }
                }
            }
            .let { sections ->
                sections.firstOrNull { favoredTrackIds.contains(it.trackId) }
                    ?: sections.minByOrNull { it.trackId.intValue }
            }

    fun findPathEdges(start: LocationTrackCacheHit, end: LocationTrackCacheHit): List<RoutingEdge>? {
        val (startEdge, startMRange) = start.getEdge()
        val (endEdge, endMRange) = end.getEdge()
        val startEdgeM = start.closestPoint.m.toEdgeM(startMRange.min)
        val endEdgeM = end.closestPoint.m.toEdgeM(endMRange.min)

        val startEdgeData = edgeData.getValue(startEdge.id)
        val endEdgeData = edgeData.getValue(endEdge.id)
        val sharedSwitches = startEdgeData.switchConnections.keys.intersect(endEdgeData.switchConnections.keys)

        return when {
            // Special case: no distance between points -> no need for path
            abs(startEdgeM - endEdgeM).distance < LAYOUT_M_DELTA -> emptyList()

            // Special case: on the same edge -> a direct single-step path along the edge is the shortest
            startEdge.id == endEdge.id -> {
                val mRange = Range(minOf(startEdgeM, endEdgeM), maxOf(startEdgeM, endEdgeM))
                val direction = if (startEdgeM <= endEdgeM) UP else DOWN
                listOf(PartialTrackEdge(startEdge.id, direction, mRange))
            }

            // Special case: on the same switch alignment -> a direct path along switch-alignment edges is the shortest
            sharedSwitches.isNotEmpty() -> {
                val connection = sharedSwitches.first()
                val startSwitchEdge = startEdgeData.switchConnections.getValue(connection)
                val endSwitchEdge = endEdgeData.switchConnections.getValue(connection)
                val startSwitchM = startSwitchEdge.toSwitchM(startEdgeM)
                val endSwitchM = endSwitchEdge.toSwitchM(endEdgeM)
                val switchMRange = Range(minOf(startSwitchM, endSwitchM), maxOf(startSwitchM, endSwitchM))
                val switchDirection = if (startSwitchM <= endSwitchM) UP else DOWN
                listOf(PartialSwitchInternalEdge(connection, switchDirection, switchMRange))
            }

            // The actual pathfinding case
            else -> {
                // Create a second temp graph for the things in this routing
                val startVertex = TrackMidPointVertex(start.track.id as IntId, start.closestPoint.m, IN)
                val endVertex = TrackMidPointVertex(end.track.id as IntId, end.closestPoint.m, OUT)
                val tmpGraph =
                    buildTempRoutingGraph(startVertex, endVertex, startEdge to startMRange, endEdge to endMRange)
                // Route the start->end in a union graph of the main graph + temp additions
                val dijkstra = DijkstraShortestPath(AsGraphUnion(jgraph, tmpGraph))
                dijkstra.getPath(startVertex, endVertex)?.edgeList?.filterNotNull()?.takeIf { it.isNotEmpty() }
            }
        }
    }

    private fun buildTempRoutingGraph(
        fromVertex: TrackMidPointVertex,
        toVertex: TrackMidPointVertex,
        fromEdgeWithM: Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>,
        toEdgeWithM: Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>,
    ): DirectedWeightedMultigraph<RoutingVertex, RoutingEdge> {
        val graph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)

        val connect =
            {
                midPoint: TrackMidPointVertex,
                vertexDirection: VertexDirection,
                edgeWithM: Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>> ->
                val (edge, edgeMRange) = edgeWithM
                val midPointM = midPoint.m.toEdgeM(edgeMRange.min)
                graph.addVertex(midPoint)
                // Note: switch inner links produce "edge ends" at switch alignment ends instead. Due to how some
                // strucutres
                // are modeled, there might be multiple endpoints (if the edge is a part of multiple structure
                // alignments).
                // Those endpoints might also be outside the edge (if the edge is only a part of the alignment). This
                // logic
                // works with all those scenarios.
                (createEdgeStartVertices(edge, vertexDirection) + createEdgeEndVertices(edge, vertexDirection))
                    .forEach { data ->
                        graph.addVertex(data.vertex)
                        val edgeDirection = data.edgeDirection(midPointM, vertexDirection)
                        val (edge, length) =
                            when (data) {
                                is TmpTrackVertexData -> {
                                    val mRange = Range(minOf(midPointM, data.vertexM), maxOf(midPointM, data.vertexM))
                                    PartialTrackEdge(edge.id, edgeDirection, mRange) to mRange.length().distance
                                }
                                is TmpSwitchVertexData -> {
                                    val switchMRange =
                                        data.switchEdge.toSwitchM(midPointM).let { midPointSwitchM ->
                                            Range(
                                                minOf(midPointSwitchM, data.vertexM),
                                                maxOf(midPointSwitchM, data.vertexM),
                                            )
                                        }
                                    PartialSwitchInternalEdge(data.alignment, edgeDirection, switchMRange) to
                                        switchMRange.length().distance
                                }
                            }
                        val (start, end) =
                            when (vertexDirection) {
                                OUT -> (midPoint to data.vertex)
                                IN -> (data.vertex to midPoint)
                            }
                        graph.addWeightedEdge(start, end, edge, length)
                    }
            }

        connect(fromVertex, OUT, fromEdgeWithM)
        connect(toVertex, IN, toEdgeWithM)

        return graph
    }

    sealed class TmpVertexData {
        abstract val vertex: RoutingVertex

        abstract fun edgeDirection(midPointM: LineM<EdgeM>, vertexDirection: VertexDirection): EdgeDirection
    }

    private data class TmpTrackVertexData(override val vertex: RoutingVertex, val vertexM: LineM<EdgeM>) :
        TmpVertexData() {
        override fun edgeDirection(midPointM: LineM<EdgeM>, vertexDirection: VertexDirection): EdgeDirection =
            when (vertexDirection) {
                IN -> if (midPointM >= vertexM) UP else DOWN
                OUT -> if (midPointM <= vertexM) UP else DOWN
            }
    }

    private data class TmpSwitchVertexData(
        override val vertex: RoutingVertex,
        val vertexM: LineM<SwitchStructureAlignmentM>,
        val alignment: RoutingSwitchAlignment,
        val switchEdge: SwitchEdge,
    ) : TmpVertexData() {

        override fun edgeDirection(midPointM: LineM<EdgeM>, vertexDirection: VertexDirection): EdgeDirection =
            switchEdge.toSwitchM(midPointM).let { switchM ->
                when (vertexDirection) {
                    IN -> if (switchM >= vertexM) UP else DOWN
                    OUT -> if (switchM <= vertexM) UP else DOWN
                }
            }
    }

    private fun createEdgeStartVertices(edge: DbLayoutEdge, vertexDirection: VertexDirection): List<TmpVertexData> =
        if (edge.isSwitchInnerLink()) {
            val data = edgeData.getValue(edge.id)
            data.switchConnections.entries
                .map { (alignment, switchEdge) ->
                    val jointNumber =
                        alignment.jointNumbers.let { if (switchEdge.direction == UP) it.first() else it.last() }
                    val vertexM =
                        LineM<SwitchStructureAlignmentM>(if (switchEdge.direction == UP) 0.0 else alignment.length)
                    val vertex = SwitchJointVertex(alignment.id, jointNumber, vertexDirection)
                    TmpSwitchVertexData(vertex, vertexM, alignment, switchEdge)
                }
                .distinctBy { it.vertex }
        } else
            listOf(
                requireNotNull(createIncomingTrackConnectionVertex(edge.startNode)) {
                        "Failed to resolve incoming vertex from edge start: edge=${edge.id} startNode=${edge.startNode}"
                    }
                    .let { TmpTrackVertexData(if (vertexDirection == IN) it else it.reverse(), LineM(0.0)) }
            )

    private fun createEdgeEndVertices(edge: DbLayoutEdge, vertexDirection: VertexDirection): List<TmpVertexData> =
        if (edge.isSwitchInnerLink()) {
            val data = edgeData.getValue(edge.id)
            data.switchConnections.entries
                .map { (alignment, switchEdge) ->
                    val jointNumber =
                        alignment.jointNumbers.let { if (switchEdge.direction == UP) it.last() else it.first() }
                    val vertexM =
                        LineM<SwitchStructureAlignmentM>(if (switchEdge.direction == UP) alignment.length else 0.0)
                    val vertex = SwitchJointVertex(alignment.id, jointNumber, vertexDirection)
                    TmpSwitchVertexData(vertex, vertexM, alignment, switchEdge)
                }
                .distinctBy { it.vertex }
        } else
            listOf(
                requireNotNull(createIncomingTrackConnectionVertex(edge.endNode)) {
                        "Failed to resolve incoming vertex from edge start: edge=${edge.id} startNode=${edge.endNode}"
                    }
                    .let { TmpTrackVertexData(if (vertexDirection == IN) it else it.reverse(), edge.length) }
            )
}

fun buildGraph(
    trackGeoms: List<DbLocationTrackGeometry>,
    switches: List<LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): RoutingGraph {
    val edgeData = createEdgeData(trackGeoms, switches, structures)
    val edges = edgeData.values.map { edgeData -> edgeData.edge }.toSet()
    val nodes = edges.flatMap { edge -> listOf(edge.startNode.node, edge.endNode.node) }.toSet()
    val switchInternalEdges =
        edgeData.entries.flatMap { (_, data) -> data.switchConnections.entries }.groupBy({ it.key }, { it.value })

    // Graph types: https://jgrapht.org/guide/UserOverview#graph-structures
    val jgraph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)

    val switchVertices = switches.flatMap { s -> createSwitchVertices(s, structures) }
    val trackVertices = trackGeoms.flatMap(::createTrackEndVertices)
    (switchVertices.asSequence() + trackVertices.asSequence()).forEach { v -> jgraph.addVertex(v) }

    val switchConnections = switches.flatMap { s -> createThroughSwitchConnections(s, structures) }
    val directConnections = nodes.flatMap(::createDirectConnections)
    val trackConnections = edges.flatMap(::createTrackConnections)
    (switchConnections.asSequence() + directConnections.asSequence() + trackConnections.asSequence()).forEach {
        (connection, edge) ->
        jgraph.addWeightedEdge(connection.from, connection.to, edge, connection.length)
    }
    return RoutingGraph(jgraph = jgraph, edgeData = edgeData, switchInternalEdges = switchInternalEdges)
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
): Map<RoutingSwitchAlignment, SwitchEdge> {
    val switchId = edge.startNode.switchIn?.id?.takeIf { edge.endNode.switchIn?.id == it }
    val startJoint = edge.startNode.switchIn?.jointNumber
    val endJoint = edge.endNode.switchIn?.jointNumber
    return if (switchId != null && startJoint != null && endJoint != null) {
        val structure = structures.getValue(switches.getValue(switchId).switchStructureId)
        val alignments = structure.alignments.filter { it.contains(startJoint) && it.contains(endJoint) }
        alignments
            .mapNotNull { alignment ->
                val startJointSwitchM = structure.distance(alignment.jointNumbers.first(), startJoint, alignment)
                val endJointSwitchM = structure.distance(alignment.jointNumbers.first(), endJoint, alignment)
                val startSwitchM = minOf(startJointSwitchM, endJointSwitchM)
                val endSwitchM = maxOf(startJointSwitchM, endJointSwitchM)
                produceIf(endSwitchM - startSwitchM >= LAYOUT_M_DELTA) {
                    val direction = if (startJointSwitchM < endJointSwitchM) UP else DOWN
                    val switchEdge = SwitchEdge(edge.id, direction, Range(LineM(startSwitchM), LineM(endSwitchM)))
                    RoutingSwitchAlignment(switchId, alignment) to switchEdge
                }
            }
            .associate { it }
    } else emptyMap()
}

fun createSwitchVertices(
    switch: LayoutSwitch,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): List<SwitchJointVertex> {
    val structure = structures.getValue(switch.switchStructureId)
    val id = switch.id as? IntId ?: error { "Switch must be stored in DB and hence have a db ID: switch=$switch" }
    return structure.endJointNumbers.flatMap { jointNumber ->
        listOf(SwitchJointVertex(id, jointNumber, IN), SwitchJointVertex(id, jointNumber, OUT))
    }
}

fun createThroughSwitchConnections(
    switch: LayoutSwitch,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): List<Pair<RoutingConnection, SwitchInternalEdge>> {
    val structure = structures.getValue(switch.switchStructureId)
    val id = switch.id as? IntId ?: error { "Switch must be stored in DB and hence have a db ID: switch=$switch" }
    return structure.alignments.flatMap { alignment ->
        val connectionStraight =
            RoutingConnection(
                from = SwitchJointVertex(id, alignment.jointNumbers.first(), IN),
                to = SwitchJointVertex(id, alignment.jointNumbers.last(), OUT),
                length = alignment.length(),
            )
        val connectionReverse =
            RoutingConnection(
                from = SwitchJointVertex(id, alignment.jointNumbers.last(), IN),
                to = SwitchJointVertex(id, alignment.jointNumbers.first(), OUT),
                length = alignment.length(),
            )
        val edgeStraight = SwitchInternalEdge(RoutingSwitchAlignment(id, alignment), UP)
        val edgeReverse = edgeStraight.reverse()
        listOf(connectionStraight to edgeStraight, connectionReverse to edgeReverse)
    }
}

fun createTrackEndVertices(geometry: DbLocationTrackGeometry): List<TrackBoundaryVertex> =
    listOf(geometry.startNode, geometry.endNode).flatMap { connection ->
        connection?.innerPort?.let { port ->
            if (port is TrackBoundary)
                listOf(TrackBoundaryVertex(port.id, port.type, IN), TrackBoundaryVertex(port.id, port.type, OUT))
            else null
        } ?: emptyList()
    }

fun createDirectConnections(node: DbLayoutNode): List<Pair<RoutingConnection, DirectConnectionEdge>> {
    return when (node) {
        is DbSwitchNode ->
            if (node.portA.id != node.portB?.id && node.portB != null) {
                // Dual switch nodes: when coming out of one switch, you can move straight in to the next one
                val connection =
                    RoutingConnection(
                        from = SwitchJointVertex(node.portA.id, node.portA.jointNumber, OUT),
                        to = SwitchJointVertex(node.portB.id, node.portB.jointNumber, IN),
                        length = 0.0,
                    )
                val edge = DirectConnectionEdge(node.id, UP)
                listOf(connection to edge, connection.reverse() to edge.reverse())
            } else {
                // Single switch node internal navigation is handled by switch-internal connections
                emptyList()
            }
        is DbTrackBoundaryNode ->
            if (node.portB != null) {
                // Dual-track boundaries: when coming out of one track, you can move straight in to the next one
                val forward =
                    RoutingConnection(
                        from = TrackBoundaryVertex(node.portA.id, node.portA.type, OUT),
                        to = TrackBoundaryVertex(node.portB.id, node.portB.type, IN),
                        length = 0.0,
                    )
                val backward =
                    RoutingConnection(
                        from = TrackBoundaryVertex(node.portB.id, node.portB.type, OUT),
                        to = TrackBoundaryVertex(node.portA.id, node.portA.type, IN),
                        length = 0.0,
                    )
                val edge = DirectConnectionEdge(node.id, UP)
                listOf(forward to edge, backward to edge.reverse())
            } else {
                // Single track boundary is a dead end, but allow turning around here (only out->in, not in->out)
                listOf(
                    RoutingConnection(
                        from = TrackBoundaryVertex(node.portA.id, node.portA.type, OUT),
                        to = TrackBoundaryVertex(node.portA.id, node.portA.type, IN),
                        length = 0.0,
                    ) to DirectConnectionEdge(node.id, UP)
                )
            }
    }
}

fun createTrackConnections(edge: DbLayoutEdge): List<Pair<RoutingConnection, TrackEdge>> =
    edge
        // Switch inner links are handled separately: ignore them here
        .takeIf { !it.isSwitchInnerLink() }
        ?.let { e ->
            val incomingStartVertex = createIncomingTrackConnectionVertex(e.startNode)
            val outgoingEndVertex = createIncomingTrackConnectionVertex(e.endNode)?.reverse()
            if (incomingStartVertex != null && outgoingEndVertex != null) {
                val connection = RoutingConnection(incomingStartVertex, outgoingEndVertex, e.length.distance)
                val edge = TrackEdge(e.id, UP)
                listOf(connection to edge, connection.reverse() to edge.reverse())
            } else {
                logger.warn(
                    "Cannot route via edge with invalid switch linking: edgeId=${e.id} startNode=${e.startNode} endNode=${e.endNode}"
                )
                null
            }
        } ?: emptyList()

private fun createIncomingTrackConnectionVertex(nodeConnection: DbNodeConnection) =
    when (nodeConnection.node) {
        is DbSwitchNode -> {
            // Inner switch connections are handled elsewhere and multi-switch nodes are already connected via
            // direct connections. Hence, only pure outer connections need processing here.
            nodeConnection.switchOut
                ?.takeIf { nodeConnection.switchIn == null }
                ?.let { link -> SwitchJointVertex(link.id, link.jointNumber, OUT) }
        }
        is DbTrackBoundaryNode -> {
            // Multi-track boundaries are already connected via direct connections. Hence, we only need to
            // connect from the inner track boundary.
            nodeConnection.trackBoundaryIn?.let { boundary -> TrackBoundaryVertex(boundary.id, boundary.type, IN) }
        }
    }

private fun DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>.addWeightedEdge(
    from: RoutingVertex,
    to: RoutingVertex,
    edge: RoutingEdge,
    weight: Double,
) {
    try {
        if (addEdge(from, to, edge)) {
            setEdgeWeight(edge, weight)
        } else {
            logger.warn("Did not add duplicate edge: edge=$edge from=$from to=$to")
        }
    } catch (e: IllegalArgumentException) {
        logger.error("Failed to add edge: edge=$edge from=$from to=$to error=${e.message}")
    }
}
