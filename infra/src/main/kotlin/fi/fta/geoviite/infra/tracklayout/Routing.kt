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
import fi.fta.geoviite.infra.tracklayout.graph.LayoutEdgeRoutingReference
import fi.fta.geoviite.infra.tracklayout.graph.SwitchAlignmentRoutingReference
import fi.fta.geoviite.infra.tracklayout.graph.Topology
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDirection
import fi.fta.geoviite.infra.tracklayout.graph.TopologyEdge
import fi.fta.geoviite.infra.tracklayout.graph.TopologyEdgeTraversal
import fi.fta.geoviite.infra.tracklayout.graph.TopologyLayoutEdgeData
import fi.fta.geoviite.infra.tracklayout.graph.TopologySwitchAlignment
import fi.fta.geoviite.infra.tracklayout.graph.TopologySwitchSegment
import fi.fta.geoviite.infra.tracklayout.graph.TrackSection
import fi.fta.geoviite.infra.tracklayout.graph.resolveTopologySwitchSegments
import fi.fta.geoviite.infra.util.alsoIfNull
import fi.fta.geoviite.infra.util.produceIf
import java.util.*
import kotlin.math.abs
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

enum class TopologyEdgeEndpoint {
    START,
    END,
}

data class TopologyEndpointVertex(
    val edgeId: UUID,
    val endpoint: TopologyEdgeEndpoint,
    val direction: VertexDirection,
) : RoutingVertex() {
    override fun reverse(): TopologyEndpointVertex = copy(direction = direction.reverse())
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

data class TopologyTransitionEdge(
    val nodeId: UUID,
    val incomingEdgeId: UUID,
    val incomingDirection: TopologyDirection,
    val outgoingEdgeId: UUID,
    val outgoingDirection: TopologyDirection,
) : RoutingEdge() {
    override fun reverse(): TopologyTransitionEdge =
        copy(
            incomingEdgeId = outgoingEdgeId,
            incomingDirection = outgoingDirection.reverse(),
            outgoingEdgeId = incomingEdgeId,
            outgoingDirection = incomingDirection.reverse(),
        )
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

data class TopologySwitchEdge(
    val edge: TopologyEdge,
    val alignment: RoutingSwitchAlignment,
    val forward: Boolean,
) {
    fun direction(topologyDirection: TopologyDirection): EdgeDirection =
        when {
            forward && topologyDirection == TopologyDirection.ASCENDING -> UP
            forward -> DOWN
            topologyDirection == TopologyDirection.ASCENDING -> DOWN
            else -> UP
        }

    fun endpointAtSwitchM(m: Double): TopologyEdgeEndpoint {
        val alignmentEndpoint =
            when {
                abs(m) < LAYOUT_M_DELTA -> TopologyEdgeEndpoint.START
                abs(m - alignment.length) < LAYOUT_M_DELTA -> TopologyEdgeEndpoint.END
                else -> error("Switch topology endpoint is not at alignment end: alignment=$alignment m=$m")
            }
        return if (forward) alignmentEndpoint
        else
            when (alignmentEndpoint) {
                TopologyEdgeEndpoint.START -> TopologyEdgeEndpoint.END
                TopologyEdgeEndpoint.END -> TopologyEdgeEndpoint.START
            }
    }
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

data class RoutingGraph
internal constructor(
    private val jgraph: Graph<RoutingVertex, RoutingEdge>,
    private val edgeData: Map<IntId<LayoutEdge>, RouteEdgeData>,
    private val switchInternalEdges: Map<RoutingSwitchAlignment, List<SwitchEdge>>,
    private val topologyRouting: RoutingTopologyData? = null,
) {
    fun getVertices(): Set<RoutingVertex> = jgraph.vertexSet()

    fun getEdges(): Map<RoutingEdge, Pair<RoutingVertex, RoutingVertex>> =
        jgraph.edgeSet().associateWith { edge -> jgraph.getEdgeSource(edge) to jgraph.getEdgeTarget(edge) }

    fun findPath(start: PointNearTrack, end: PointNearTrack): Route? {
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
            is DirectConnectionEdge,
            is TopologyTransitionEdge -> emptyList()
        }

    private fun findSwitchRouteSections(
        alignment: RoutingSwitchAlignment,
        edgeDirection: EdgeDirection,
        favoredTrackIds: Set<IntId<LocationTrack>>,
        mRangeLimit: Range<LineM<SwitchStructureAlignmentM>>? = null,
    ) =
        switchInternalEdges[alignment]?.mapNotNull { switchEdge ->
            val edgeDirection = if (edgeDirection == UP) switchEdge.direction else switchEdge.direction.reverse()
            val edgeRangeLimit = mRangeLimit?.let { switchM ->
                val edgeMin = switchEdge.toEdgeM(switchM.min)
                val edgeMax = switchEdge.toEdgeM(switchM.max)
                Range(minOf(edgeMin, edgeMax), maxOf(edgeMin, edgeMax))
            }
            if (edgeRangeLimit == null || edgeRangeLimit.length().distance >= LAYOUT_M_DELTA) {
                findRouteSection(switchEdge.id, edgeDirection, favoredTrackIds, edgeRangeLimit)
            } else null
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

    fun findPathEdges(start: PointNearTrack, end: PointNearTrack): List<RoutingEdge>? {
        val (startEdge, startMRange) = start.getEdge()
        val (endEdge, endMRange) = end.getEdge()
        val startEdgeM = start.closestPoint.m.toEdgeM(startMRange.min)
        val endEdgeM = end.closestPoint.m.toEdgeM(endMRange.min)

        val startEdgeData = edgeData.getValue(startEdge.id)
        val endEdgeData = edgeData.getValue(endEdge.id)
        val sharedSwitches = startEdgeData.switchConnections.keys.intersect(endEdgeData.switchConnections.keys)

        return when {
            // Special case: no distance between points -> no need for path
            startEdge.id == endEdge.id && abs(startEdgeM - endEdgeM).distance < LAYOUT_M_DELTA -> emptyList()

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
                // Note: switch inner links produce "edge ends" at switch alignment ends instead.
                // Due to how some structures are modeled, there might be multiple endpoints (if the
                // edge is a part of multiple structure alignments). Those endpoints might also be
                // outside the edge (if the edge is only a part of the alignment).
                // This logic works with all those scenarios.
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

        protected fun <T : AnyM<T>> calculateEdgeDirection(sourceM: LineM<T>, targetM: LineM<T>): EdgeDirection =
            when {
                targetM > sourceM -> UP
                sourceM > targetM -> DOWN
                // Special cases for the same location: assume we're coming from the center direction
                // If the target is at the start of the edge (0.0) we're heading DOWN, otherwise UP
                targetM.distance == 0.0 -> DOWN
                else -> UP
            }
    }

    private data class TmpTrackVertexData(override val vertex: RoutingVertex, val vertexM: LineM<EdgeM>) :
        TmpVertexData() {

        override fun edgeDirection(midPointM: LineM<EdgeM>, vertexDirection: VertexDirection): EdgeDirection =
            when (vertexDirection) {
                IN -> calculateEdgeDirection(vertexM, midPointM)
                OUT -> calculateEdgeDirection(midPointM, vertexM)
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
                    IN -> calculateEdgeDirection(vertexM, switchM)
                    OUT -> calculateEdgeDirection(switchM, vertexM)
                }
            }
    }

    private fun createEdgeStartVertices(edge: DbLayoutEdge, vertexDirection: VertexDirection): List<TmpVertexData> =
        if (topologyRouting != null) {
            createTopologyEdgeVertices(edge, vertexDirection, TopologyEdgeEndpoint.START)
        } else {
            createLegacyEdgeStartVertices(edge, vertexDirection)
        }

    private fun createLegacyEdgeStartVertices(
        edge: DbLayoutEdge,
        vertexDirection: VertexDirection,
    ): List<TmpVertexData> =
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
            listOfNotNull(
                createIncomingTrackConnectionVertex(edge.startNode)
                    .alsoIfNull {
                        // Can happen when the edge is inside a broken switch-linking -> won't yield a routable path
                        logger.warn(
                            "Failed to resolve incoming vertex from edge start: edge=${edge.id} startNode=${edge.startNode} endNode=${edge.endNode}"
                        )
                    }
                    ?.let { TmpTrackVertexData(if (vertexDirection == IN) it else it.reverse(), LineM(0.0)) }
            )

    private fun createEdgeEndVertices(edge: DbLayoutEdge, vertexDirection: VertexDirection): List<TmpVertexData> =
        if (topologyRouting != null) {
            createTopologyEdgeVertices(edge, vertexDirection, TopologyEdgeEndpoint.END)
        } else {
            createLegacyEdgeEndVertices(edge, vertexDirection)
        }

    private fun createLegacyEdgeEndVertices(
        edge: DbLayoutEdge,
        vertexDirection: VertexDirection,
    ): List<TmpVertexData> =
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
            listOfNotNull(
                createIncomingTrackConnectionVertex(edge.endNode)
                    .alsoIfNull {
                        // Can happen when the edge is inside a broken switch-linking -> won't yield a routable path
                        logger.warn(
                            "Failed to resolve incoming vertex from edge end: edge=${edge.id} startNode=${edge.startNode} endNode=${edge.endNode}"
                        )
                    }
                    ?.let { TmpTrackVertexData(if (vertexDirection == IN) it else it.reverse(), edge.length) }
            )

    private fun createTopologyEdgeVertices(
        edge: DbLayoutEdge,
        vertexDirection: VertexDirection,
        dbEndpoint: TopologyEdgeEndpoint,
    ): List<TmpVertexData> {
        val topologyRouting = requireNotNull(topologyRouting)
        val topologyVertexDirection = vertexDirection.reverse()
        return if (edge.isSwitchInnerLink()) {
            edgeData
                .getValue(edge.id)
                .switchConnections
                .flatMap { (alignment, switchEdge) ->
                    val switchM =
                        when (dbEndpoint) {
                            TopologyEdgeEndpoint.START -> if (switchEdge.direction == UP) 0.0 else alignment.length
                            TopologyEdgeEndpoint.END -> if (switchEdge.direction == UP) alignment.length else 0.0
                        }
                    topologyRouting.switchEdgesByAlignment[alignment]?.let { topologySwitchEdge ->
                        listOf(
                            TmpSwitchVertexData(
                                vertex =
                                    TopologyEndpointVertex(
                                        topologySwitchEdge.edge.id,
                                        topologySwitchEdge.endpointAtSwitchM(switchM),
                                        topologyVertexDirection,
                                    ),
                                vertexM = LineM(switchM),
                                alignment = alignment,
                                switchEdge = switchEdge,
                            )
                        )
                    }
                        ?: createPartialSwitchEndpointVertices(
                            topologyRouting,
                            alignment,
                            switchEdge,
                            switchM,
                            vertexDirection,
                        )
                }
                .distinctBy(TmpVertexData::vertex)
        } else {
            val topologyEdge =
                requireNotNull(topologyRouting.edgesByDbEdgeId[edge.id]) {
                    "Topology is missing a routing reference for database edge: edge=${edge.id}"
                }
            val vertexM =
                when (dbEndpoint) {
                    TopologyEdgeEndpoint.START -> LineM(0.0)
                    TopologyEdgeEndpoint.END -> edge.length
                }
            listOf(
                TmpTrackVertexData(
                    TopologyEndpointVertex(topologyEdge.id, dbEndpoint, topologyVertexDirection),
                    vertexM,
                )
            )
        }
    }

    private fun createPartialSwitchEndpointVertices(
        topologyRouting: RoutingTopologyData,
        alignment: RoutingSwitchAlignment,
        switchEdge: SwitchEdge,
        switchM: Double,
        vertexDirection: VertexDirection,
    ): List<TmpVertexData> {
        val jointNumber =
            when {
                abs(switchM) < LAYOUT_M_DELTA -> alignment.jointNumbers.first()
                abs(switchM - alignment.length) < LAYOUT_M_DELTA -> alignment.jointNumbers.last()
                else -> return emptyList()
            }
        return topologyRouting.externalEndpointsBySwitchJoint[RoutingSwitchJoint(alignment.id, jointNumber)]
            .orEmpty()
            .map { endpoint ->
                TmpSwitchVertexData(
                    vertex = TopologyEndpointVertex(endpoint.edgeId, endpoint.endpoint, vertexDirection),
                    vertexM = LineM(switchM),
                    alignment = alignment,
                    switchEdge = switchEdge,
                )
            }
    }
}

internal data class RoutingTopologyData(
    val edgesByDbEdgeId: Map<IntId<LayoutEdge>, TopologyEdge>,
    val switchEdgesByAlignment: Map<RoutingSwitchAlignment, TopologySwitchEdge>,
    val externalEndpointsBySwitchJoint: Map<RoutingSwitchJoint, List<TopologyEdgeReference>>,
)

internal data class RoutingSwitchJoint(val switchId: IntId<LayoutSwitch>, val jointNumber: JointNumber)

internal data class TopologyEdgeReference(val edgeId: UUID, val endpoint: TopologyEdgeEndpoint)

fun buildGraph(topology: Topology): RoutingGraph {
    val (edgeData, topologyEdgeByDbEdgeId, topologySwitchEdgeList) = topology.verifyBeforeBuildingGraph()
    val topologySwitchEdges = topologySwitchEdgeList.associateBy(TopologySwitchEdge::alignment)
    val topologySwitchEdgesById = topologySwitchEdges.values.associateBy { switchEdge -> switchEdge.edge.id }
    val layoutEdgeEndpointsByNode = topology.layoutEdgeEndpointsByNode()
    val externalEndpointsBySwitchJoint = topology.externalEndpointsBySwitchJoint(layoutEdgeEndpointsByNode)
    val switchInternalEdges =
        edgeData.entries.flatMap { (_, data) -> data.switchConnections.entries }.groupBy({ it.key }, { it.value })
    val jgraph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)

    topology.edges.forEach { edge ->
        TopologyEdgeEndpoint.entries.forEach { endpoint ->
            jgraph.addVertex(TopologyEndpointVertex(edge.id, endpoint, IN))
            jgraph.addVertex(TopologyEndpointVertex(edge.id, endpoint, OUT))
        }
        val forwardRoutingEdge =
            when (val reference = edge.routingReference) {
                is LayoutEdgeRoutingReference -> TrackEdge(reference.edgeId, UP)
                is SwitchAlignmentRoutingReference ->
                    topologySwitchEdgesById.getValue(edge.id).let { switchEdge ->
                        SwitchInternalEdge(
                            switchEdge.alignment,
                            switchEdge.direction(TopologyDirection.ASCENDING),
                        )
                    }
                null -> error("Nano topology edge has no routing reference: edge=${edge.id}")
            }
        jgraph.addWeightedEdge(
            TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.START, OUT),
            TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.END, IN),
            forwardRoutingEdge,
            edge.length,
        )
        jgraph.addWeightedEdge(
            TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.END, OUT),
            TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.START, IN),
            forwardRoutingEdge.reverse(),
            edge.length,
        )
    }
    topology.nodes
        .flatMap { node -> node.transitions.map { transition -> node to transition } }
        .forEach { (node, transition) ->
            jgraph.addWeightedEdge(
                transition.incomingEdge.endVertex(IN),
                transition.outgoingEdge.startVertex(OUT),
                TopologyTransitionEdge(
                    nodeId = node.id,
                    incomingEdgeId = transition.incomingEdge.edge.id,
                    incomingDirection = transition.incomingEdge.direction,
                    outgoingEdgeId = transition.outgoingEdge.edge.id,
                    outgoingDirection = transition.outgoingEdge.direction,
                ),
                0.0,
            )
        }
    return RoutingGraph(
        jgraph = jgraph,
        edgeData = edgeData,
        switchInternalEdges = switchInternalEdges,
        topologyRouting =
            RoutingTopologyData(
                edgesByDbEdgeId = topologyEdgeByDbEdgeId,
                switchEdgesByAlignment = topologySwitchEdges,
                externalEndpointsBySwitchJoint = externalEndpointsBySwitchJoint,
            ),
    )
}

private fun Topology.verifyBeforeBuildingGraph(): VerifiedTopologyData {
    val edgeData = routingData.edgesByLayoutEdgeId.mapValues { (_, data) -> data.toRouteEdgeData() }
    val topologyEdgeByDbEdgeId =
        edges
            .mapNotNull { edge ->
                (edge.routingReference as? LayoutEdgeRoutingReference)?.let { reference -> reference.edgeId to edge }
            }
            .toMap()
    requireEmpty(edgeData.values.missingDbEdgeReferences(topologyEdgeByDbEdgeId)) { missing ->
        "Topology is missing routing references for database edges: edges=$missing"
    }
    requireEmpty(topologyEdgeByDbEdgeId.keys.filterNot(edgeData::containsKey)) { missing ->
        "Topology is missing routing data for referenced database edges: edges=$missing"
    }
    val topologySwitchEdgeList = edges.mapNotNull { edge ->
        (edge.routingReference as? SwitchAlignmentRoutingReference)?.let { reference ->
            TopologySwitchEdge(
                edge = edge,
                alignment = RoutingSwitchAlignment(reference.switchId, reference.alignment),
                forward = reference.forward,
            )
        }
    }
    requireEmpty(topologySwitchEdgeList.duplicateAlignments()) { values ->
        "Topology contains duplicate routing references for switch alignments: alignments=$values"
    }
    return VerifiedTopologyData(edgeData, topologyEdgeByDbEdgeId, topologySwitchEdgeList)
}

private data class VerifiedTopologyData(
    val edgeData: Map<IntId<LayoutEdge>, RouteEdgeData>,
    val topologyEdgeByDbEdgeId: Map<IntId<LayoutEdge>, TopologyEdge>,
    val topologySwitchEdgeList: List<TopologySwitchEdge>,
)

private fun Topology.layoutEdgeEndpointsByNode() =
    edges
        .filter { edge -> edge.routingReference is LayoutEdgeRoutingReference }
        .flatMap { edge ->
            listOf(
                edge.startNode.id to TopologyEdgeReference(edge.id, TopologyEdgeEndpoint.START),
                edge.endNode.id to TopologyEdgeReference(edge.id, TopologyEdgeEndpoint.END),
            )
        }
        .groupBy({ (nodeId) -> nodeId }, { (_, endpoint) -> endpoint })

private fun Topology.externalEndpointsBySwitchJoint(layoutEdgeEndpointsByNode: Map<UUID, List<TopologyEdgeReference>>) =
    nodes
        .flatMap { node ->
            node.switchReferences.flatMap { reference ->
                val link = reference.switch
                layoutEdgeEndpointsByNode[node.id].orEmpty().map { endpoint ->
                    RoutingSwitchJoint(link.id, link.jointNumber) to endpoint
                }
            }
        }
        .groupBy({ (joint) -> joint }, { (_, endpoint) -> endpoint })

private fun List<TopologySwitchEdge>.duplicateAlignments() =
    this.groupBy(TopologySwitchEdge::alignment).filterValues { edges -> edges.size > 1 }.keys

private inline fun <T> requireEmpty(values: Collection<T>, lazyMessage: (values: Collection<T>) -> Any) {
    require(values.isEmpty()) { lazyMessage(values) }
}

private fun Collection<RouteEdgeData>.missingDbEdgeReferences(
    topologyEdgeByDbEdgeId: Map<IntId<LayoutEdge>, TopologyEdge>
) =
    this.filterNot { data -> data.edge.isSwitchInnerLink() }
        .map { data -> data.edge.id }
        .filterNot { id -> topologyEdgeByDbEdgeId.containsKey(id) }

private fun TopologyEdgeTraversal.startVertex(direction: VertexDirection): TopologyEndpointVertex =
    TopologyEndpointVertex(
        edgeId = edge.id,
        endpoint =
            when (this.direction) {
                TopologyDirection.ASCENDING -> TopologyEdgeEndpoint.START
                TopologyDirection.DESCENDING -> TopologyEdgeEndpoint.END
            },
        direction = direction,
    )

private fun TopologyEdgeTraversal.endVertex(direction: VertexDirection): TopologyEndpointVertex =
    TopologyEndpointVertex(
        edgeId = edge.id,
        endpoint =
            when (this.direction) {
                TopologyDirection.ASCENDING -> TopologyEdgeEndpoint.END
                TopologyDirection.DESCENDING -> TopologyEdgeEndpoint.START
            },
        direction = direction,
    )

fun resolveSwitchAlignments(
    edge: DbLayoutEdge,
    switches: Map<IntId<LayoutSwitch>, LayoutSwitch>,
    structures: Map<IntId<SwitchStructure>, SwitchStructure>,
): Map<RoutingSwitchAlignment, SwitchEdge> =
    resolveTopologySwitchSegments(edge, switches, structures)
        .map { (alignment, segment) -> alignment.toRoutingSwitchAlignment() to segment.toSwitchEdge() }
        .toMap()

private fun TopologyLayoutEdgeData.toRouteEdgeData(): RouteEdgeData =
    RouteEdgeData(
        edge = edge,
        tracks = tracks,
        switchConnections =
            switchConnections
                .map { (alignment, segment) -> alignment.toRoutingSwitchAlignment() to segment.toSwitchEdge() }
                .toMap(),
    )

private fun TopologySwitchAlignment.toRoutingSwitchAlignment(): RoutingSwitchAlignment =
    RoutingSwitchAlignment(switchId, alignment)

private fun TopologySwitchSegment.toSwitchEdge(): SwitchEdge =
    SwitchEdge(
        id = edgeId,
        direction =
            when (direction) {
                TopologyDirection.ASCENDING -> UP
                TopologyDirection.DESCENDING -> DOWN
            },
        mRange = mRange,
    )

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
        // These can happen when incorrectly built switch linked result in edges whose vertices are not in the graph
        logger.warn("Failed to add edge: edge=$edge from=$from to=$to error=${e.message}")
    }
}
