package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.combine
import fi.fta.geoviite.infra.math.length
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
import fi.fta.geoviite.infra.tracklayout.graph.TopologyTransition
import fi.fta.geoviite.infra.tracklayout.graph.TrackSection
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
    val routingAlignment: RoutingSwitchAlignment? = null,
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
    val routingAlignment: RoutingSwitchAlignment? = null,
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
    val mRange: Range<LineM<SwitchStructureAlignmentM>> = Range(LineM(0.0), LineM(alignment.length)),
) {
    val isFullAlignment: Boolean
        get() =
            abs(mRange.min.distance) < LAYOUT_M_DELTA && abs(mRange.max.distance - alignment.length) < LAYOUT_M_DELTA

    fun direction(topologyDirection: TopologyDirection): EdgeDirection =
        when {
            forward && topologyDirection == TopologyDirection.ASCENDING -> UP
            forward -> DOWN
            topologyDirection == TopologyDirection.ASCENDING -> DOWN
            else -> UP
        }

    fun endpointAtSwitchM(m: Double): TopologyEdgeEndpoint? {
        val alignmentEndpoint =
            when {
                abs(m - mRange.min.distance) < LAYOUT_M_DELTA -> TopologyEdgeEndpoint.START
                abs(m - mRange.max.distance) < LAYOUT_M_DELTA -> TopologyEdgeEndpoint.END
                else -> return null
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
    val edgeLength: Double,
) {
    fun toSwitchM(edgeM: LineM<EdgeM>): LineM<SwitchStructureAlignmentM> =
        LineM(
            when (direction) {
                UP -> mRange.min.distance + edgeM.distance * mRange.length().distance / edgeLength
                DOWN -> mRange.max.distance - edgeM.distance * mRange.length().distance / edgeLength
            }
        )

    fun toEdgeM(switchM: LineM<SwitchStructureAlignmentM>): LineM<EdgeM> =
        LineM(
            when (direction) {
                UP -> (switchM.distance - mRange.min.distance) * edgeLength / mRange.length().distance
                DOWN -> (mRange.max.distance - switchM.distance) * edgeLength / mRange.length().distance
            }
        )

    fun toEdgeM(switchMRange: Range<LineM<SwitchStructureAlignmentM>>): Range<LineM<EdgeM>> {
        val first = toEdgeM(switchMRange.min)
        val second = toEdgeM(switchMRange.max)
        return Range(minOf(first, second), maxOf(first, second))
    }
}

private data class SwitchRouteCandidate(
    val switchEdge: SwitchEdge,
    val switchMRange: Range<LineM<SwitchStructureAlignmentM>>,
    val routeSection: RouteSection,
)

private fun Range<LineM<SwitchStructureAlignmentM>>.overlap(
    other: Range<LineM<SwitchStructureAlignmentM>>
): Range<LineM<SwitchStructureAlignmentM>>? {
    val min = if (this.min.distance >= other.min.distance) this.min else other.min
    val max = if (this.max.distance <= other.max.distance) this.max else other.max
    return if (max.distance - min.distance >= LAYOUT_M_DELTA) Range(min, max) else null
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
    ): List<RouteSection> {
        val switchEdges = switchInternalEdges[alignment].orEmpty()
        if (
            mRangeLimit == null ||
                (abs(mRangeLimit.min.distance) < LAYOUT_M_DELTA &&
                    abs(mRangeLimit.max.distance - alignment.length) < LAYOUT_M_DELTA)
        ) {
            return switchEdges.mapNotNull { switchEdge ->
                val direction = if (edgeDirection == UP) switchEdge.direction else switchEdge.direction.reverse()
                findRouteSection(switchEdge.id, direction, favoredTrackIds)
            }
        }

        val requestedMRange: Range<LineM<SwitchStructureAlignmentM>> = mRangeLimit
        val candidates = switchRouteCandidates(alignment, requestedMRange, edgeDirection, favoredTrackIds)
        val routeSections = mutableListOf<RouteSection>()
        var currentM = requestedMRange.min
        while ((requestedMRange.max - currentM).distance >= LAYOUT_M_DELTA) {
            val candidate = bestSwitchRouteCandidate(candidates, currentM, favoredTrackIds)
            if (candidate == null) {
                currentM = nextSwitchRouteCandidateStart(candidates, currentM) ?: requestedMRange.max
            } else {
                val switchMRange = candidate.switchMRangeFrom(currentM, requestedMRange.max)
                routeSectionFor(candidate.switchEdge, switchMRange, edgeDirection, favoredTrackIds)
                    ?.let(routeSections::add)
                currentM = switchMRange.max
            }
        }
        return routeSections
    }

    private fun switchRouteCandidates(
        alignment: RoutingSwitchAlignment,
        requestedMRange: Range<LineM<SwitchStructureAlignmentM>>,
        edgeDirection: EdgeDirection,
        favoredTrackIds: Set<IntId<LocationTrack>>,
    ): List<SwitchRouteCandidate> =
        switchInternalEdges[alignment].orEmpty().mapNotNull { switchEdge ->
            val switchMRange = switchEdge.mRange.overlap(requestedMRange) ?: return@mapNotNull null
            val routeSection =
                routeSectionFor(switchEdge, switchMRange, edgeDirection, favoredTrackIds) ?: return@mapNotNull null
            SwitchRouteCandidate(switchEdge, switchMRange, routeSection)
        }

    private fun bestSwitchRouteCandidate(
        candidates: List<SwitchRouteCandidate>,
        currentM: LineM<SwitchStructureAlignmentM>,
        favoredTrackIds: Set<IntId<LocationTrack>>,
    ): SwitchRouteCandidate? =
        candidates
            .filter { candidate ->
                candidate.switchMRange.min <= currentM + LAYOUT_M_DELTA &&
                    candidate.switchMRange.max > currentM + LAYOUT_M_DELTA
            }
            .maxWithOrNull(
                // Prefer full-alignment source geometry over a shared joint fragment when coverage ties.
                compareBy<SwitchRouteCandidate> { it.switchMRange.max.distance }
                    .thenBy { (it.switchEdge.mRange.max - it.switchEdge.mRange.min).distance }
                    .thenBy { favoredTrackIds.contains(it.routeSection.trackId) }
                    .thenBy { -it.routeSection.trackId.intValue }
            )

    private fun nextSwitchRouteCandidateStart(
        candidates: List<SwitchRouteCandidate>,
        currentM: LineM<SwitchStructureAlignmentM>,
    ): LineM<SwitchStructureAlignmentM>? =
        candidates
            .map(SwitchRouteCandidate::switchMRange)
            .filter { range -> range.min.distance > currentM.distance }
            .minByOrNull { range -> range.min.distance }
            ?.min

    private fun routeSectionFor(
        switchEdge: SwitchEdge,
        switchMRange: Range<LineM<SwitchStructureAlignmentM>>,
        edgeDirection: EdgeDirection,
        favoredTrackIds: Set<IntId<LocationTrack>>,
    ): RouteSection? {
        if (switchMRange.length().distance < LAYOUT_M_DELTA) return null
        val direction = if (edgeDirection == UP) switchEdge.direction else switchEdge.direction.reverse()
        val edgeMRange = switchEdge.toEdgeM(switchMRange)
        val routeSection = findRouteSection(switchEdge.id, direction, favoredTrackIds) ?: return null
        val edgeLength = edgeData.getValue(switchEdge.id).edge.length.distance
        val trackLength = routeSection.mRange.length().distance
        val trackMRange =
            Range(
                LineM<LocationTrackM>(
                    routeSection.mRange.min.distance + edgeMRange.min.distance / edgeLength * trackLength
                ),
                LineM<LocationTrackM>(
                    routeSection.mRange.min.distance + edgeMRange.max.distance / edgeLength * trackLength
                ),
            )
        return routeSection.copy(mRange = trackMRange)
    }

    private fun SwitchRouteCandidate.switchMRangeFrom(
        currentM: LineM<SwitchStructureAlignmentM>,
        requestedMax: LineM<SwitchStructureAlignmentM>,
    ): Range<LineM<SwitchStructureAlignmentM>> {
        val start = if (currentM.distance >= switchMRange.min.distance) currentM else switchMRange.min
        val end = if (requestedMax.distance <= switchMRange.max.distance) requestedMax else switchMRange.max
        return Range(start, end)
    }

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
        createTopologyEdgeVertices(edge, vertexDirection, TopologyEdgeEndpoint.START)

    private fun createEdgeEndVertices(edge: DbLayoutEdge, vertexDirection: VertexDirection): List<TmpVertexData> =
        createTopologyEdgeVertices(edge, vertexDirection, TopologyEdgeEndpoint.END)

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
                    val matchingTopologyEdges =
                        topologyRouting.switchEdgesByAlignment[alignment].orEmpty().mapNotNull { topologySwitchEdge ->
                            topologySwitchEdge.endpointAtSwitchM(switchM)?.let { endpoint ->
                                TmpSwitchVertexData(
                                    vertex =
                                        TopologyEndpointVertex(
                                            topologySwitchEdge.edge.id,
                                            endpoint,
                                            topologyVertexDirection,
                                            topologySwitchEdge.alignment,
                                        ),
                                    vertexM = LineM(switchM),
                                    alignment = alignment,
                                    switchEdge = switchEdge,
                                )
                            }
                        }
                    matchingTopologyEdges.ifEmpty {
                        createPartialSwitchEndpointVertices(
                            topologyRouting,
                            alignment,
                            switchEdge,
                            switchM,
                            vertexDirection,
                        )
                    }
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
    ): List<TmpVertexData> =
        when {
                abs(switchM) < LAYOUT_M_DELTA -> alignment.jointNumbers.first()
                abs(switchM - alignment.length) < LAYOUT_M_DELTA -> alignment.jointNumbers.last()
                else -> null
            }
            ?.let { topologyRouting.externalEndpointsBySwitchJoint[RoutingSwitchJoint(alignment.id, it)] }
            ?.map { endpoint ->
                TmpSwitchVertexData(
                    vertex = TopologyEndpointVertex(endpoint.edgeId, endpoint.endpoint, vertexDirection),
                    vertexM = LineM(switchM),
                    alignment = alignment,
                    switchEdge = switchEdge,
                )
            } ?: emptyList()
}

internal data class RoutingTopologyData(
    val edgesByDbEdgeId: Map<IntId<LayoutEdge>, TopologyEdge>,
    val switchEdgesByAlignment: Map<RoutingSwitchAlignment, List<TopologySwitchEdge>>,
    val externalEndpointsBySwitchJoint: Map<RoutingSwitchJoint, List<TopologyEdgeReference>>,
)

internal data class RoutingSwitchJoint(val switchId: IntId<LayoutSwitch>, val jointNumber: JointNumber)

internal data class TopologyEdgeReference(val edgeId: UUID, val endpoint: TopologyEdgeEndpoint)

fun buildGraph(topology: Topology): RoutingGraph {
    val (edgeData, topologyEdgeByDbEdgeId, topologySwitchEdgeList) = topology.verifyBeforeBuildingGraph()
    val topologySwitchEdges = topologySwitchEdgeList.groupBy(TopologySwitchEdge::alignment)
    val topologySwitchEdgesById = topologySwitchEdgeList.groupBy { switchEdge -> switchEdge.edge.id }
    val layoutEdgeEndpointsByNode = topology.layoutEdgeEndpointsByNode()
    val externalEndpointsBySwitchJoint = topology.externalEndpointsBySwitchJoint(layoutEdgeEndpointsByNode)
    val switchInternalEdges =
        edgeData.entries.flatMap { (_, data) -> data.switchConnections.entries }.groupBy({ it.key }, { it.value })
    val jgraph = DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>(RoutingEdge::class.java)

    topology.edges.forEach { edge -> addTopologyEdgeToRoutingGraph(jgraph, edge, topologySwitchEdgesById) }
    topology.nodes.forEach { node ->
        node.transitions.forEach { transition -> addTopologyTransitionToRoutingGraph(jgraph, node.id, transition) }
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

private fun addTopologyEdgeToRoutingGraph(
    jgraph: DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>,
    edge: TopologyEdge,
    topologySwitchEdgesById: Map<UUID, List<TopologySwitchEdge>>,
) {
    when (val reference = edge.routingReference) {
        is LayoutEdgeRoutingReference -> addLayoutEdgeToRoutingGraph(jgraph, edge, reference)
        is SwitchAlignmentRoutingReference ->
            topologySwitchEdgesById.getValue(edge.id).forEach { switchEdge ->
                addSwitchAlignmentEdgeToRoutingGraph(jgraph, edge, switchEdge)
            }
        null -> error("Nano topology edge has no routing reference: edge=${edge.id}")
    }
}

private fun addLayoutEdgeToRoutingGraph(
    jgraph: DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>,
    edge: TopologyEdge,
    reference: LayoutEdgeRoutingReference,
) {
    TopologyEdgeEndpoint.entries.forEach { endpoint ->
        jgraph.addVertex(TopologyEndpointVertex(edge.id, endpoint, IN))
        jgraph.addVertex(TopologyEndpointVertex(edge.id, endpoint, OUT))
    }
    val forwardRoutingEdge = TrackEdge(reference.edgeId, UP)
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

private fun addSwitchAlignmentEdgeToRoutingGraph(
    jgraph: DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>,
    edge: TopologyEdge,
    switchEdge: TopologySwitchEdge,
) {
    TopologyEdgeEndpoint.entries.forEach { endpoint ->
        VertexDirection.entries.forEach { direction ->
            jgraph.addVertex(TopologyEndpointVertex(edge.id, endpoint, direction, switchEdge.alignment))
        }
    }
    val direction = switchEdge.direction(TopologyDirection.ASCENDING)
    val forwardRoutingEdge =
        if (switchEdge.isFullAlignment) {
            SwitchInternalEdge(switchEdge.alignment, direction)
        } else {
            PartialSwitchInternalEdge(switchEdge.alignment, direction, switchEdge.mRange)
        }
    jgraph.addWeightedEdge(
        TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.START, OUT, switchEdge.alignment),
        TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.END, IN, switchEdge.alignment),
        forwardRoutingEdge,
        edge.length,
    )
    jgraph.addWeightedEdge(
        TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.END, OUT, switchEdge.alignment),
        TopologyEndpointVertex(edge.id, TopologyEdgeEndpoint.START, IN, switchEdge.alignment),
        forwardRoutingEdge.reverse(),
        edge.length,
    )
}

private fun addTopologyTransitionToRoutingGraph(
    jgraph: DirectedWeightedMultigraph<RoutingVertex, RoutingEdge>,
    nodeId: UUID,
    transition: TopologyTransition,
) {
    transition.routingAlignmentPairs().forEach { (incomingAlignment, outgoingAlignment) ->
        jgraph.addWeightedEdge(
            transition.incomingEdge.endVertex(IN, incomingAlignment),
            transition.outgoingEdge.startVertex(OUT, outgoingAlignment),
            TopologyTransitionEdge(
                nodeId = nodeId,
                incomingEdgeId = transition.incomingEdge.edge.id,
                incomingDirection = transition.incomingEdge.direction,
                outgoingEdgeId = transition.outgoingEdge.edge.id,
                outgoingDirection = transition.outgoingEdge.direction,
                routingAlignment = incomingAlignment ?: outgoingAlignment,
            ),
            0.0,
        )
    }
}

private fun TopologyTransition.routingAlignmentPairs(): List<Pair<RoutingSwitchAlignment?, RoutingSwitchAlignment?>> {
    val incomingAlternatives = topologyRouteAlternatives(incomingEdge.edge)
    val outgoingAlternatives = topologyRouteAlternatives(outgoingEdge.edge)
    if (routingAlignments.isEmpty()) {
        return incomingAlternatives.flatMap { incoming ->
            outgoingAlternatives.map { outgoing -> incoming to outgoing }
        }
    }
    return routingAlignments.mapNotNull { topologyAlignment ->
        val alignment = topologyAlignment.toRoutingSwitchAlignment()
        val incoming =
            when {
                alignment in incomingAlternatives -> alignment
                null in incomingAlternatives -> null
                else -> return@mapNotNull null
            }
        val outgoing =
            when {
                alignment in outgoingAlternatives -> alignment
                null in outgoingAlternatives -> null
                else -> return@mapNotNull null
            }
        incoming to outgoing
    }
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
    val topologySwitchEdgeList = edges.flatMap { edge ->
        (edge.routingReference as? SwitchAlignmentRoutingReference)?.alternatives.orEmpty().map { alternative ->
            TopologySwitchEdge(
                edge = edge,
                alignment =
                    RoutingSwitchAlignment(
                        (edge.routingReference as SwitchAlignmentRoutingReference).switchId,
                        alternative.alignment,
                    ),
                forward = alternative.forward,
                mRange = alternative.mRange,
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
    this.groupBy { edge -> edge.alignment to edge.mRange }
        .filterValues { edges -> edges.size > 1 }
        .keys
        .map { it.first }

private inline fun <T> requireEmpty(values: Collection<T>, lazyMessage: (values: Collection<T>) -> Any) {
    require(values.isEmpty()) { lazyMessage(values) }
}

private fun Collection<RouteEdgeData>.missingDbEdgeReferences(
    topologyEdgeByDbEdgeId: Map<IntId<LayoutEdge>, TopologyEdge>
) =
    this.filterNot { data -> data.edge.isSwitchInnerLink() }
        .map { data -> data.edge.id }
        .filterNot { id -> topologyEdgeByDbEdgeId.containsKey(id) }

private fun TopologyEdgeTraversal.startVertex(
    direction: VertexDirection,
    routingAlignment: RoutingSwitchAlignment? = null,
): TopologyEndpointVertex =
    TopologyEndpointVertex(
        edgeId = edge.id,
        endpoint =
            when (this.direction) {
                TopologyDirection.ASCENDING -> TopologyEdgeEndpoint.START
                TopologyDirection.DESCENDING -> TopologyEdgeEndpoint.END
            },
        direction = direction,
        routingAlignment = routingAlignment,
    )

private fun TopologyEdgeTraversal.endVertex(
    direction: VertexDirection,
    routingAlignment: RoutingSwitchAlignment? = null,
): TopologyEndpointVertex =
    TopologyEndpointVertex(
        edgeId = edge.id,
        endpoint =
            when (this.direction) {
                TopologyDirection.ASCENDING -> TopologyEdgeEndpoint.END
                TopologyDirection.DESCENDING -> TopologyEdgeEndpoint.START
            },
        direction = direction,
        routingAlignment = routingAlignment,
    )

private fun topologyRouteAlternatives(edge: TopologyEdge): List<RoutingSwitchAlignment?> =
    when (val reference = edge.routingReference) {
        is LayoutEdgeRoutingReference -> listOf(null)
        is SwitchAlignmentRoutingReference ->
            reference.alternatives.map { alternative ->
                RoutingSwitchAlignment(reference.switchId, alternative.alignment)
            }
        null -> error("Nano topology edge has no routing reference: edge=${edge.id}")
    }

private fun TopologyLayoutEdgeData.toRouteEdgeData(): RouteEdgeData =
    RouteEdgeData(
        edge = edge,
        tracks = tracks,
        switchConnections =
            switchConnections
                .map { (alignment, segment) ->
                    alignment.toRoutingSwitchAlignment() to segment.toSwitchEdge(edge.length.distance)
                }
                .toMap(),
    )

private fun TopologySwitchAlignment.toRoutingSwitchAlignment(): RoutingSwitchAlignment =
    RoutingSwitchAlignment(switchId, alignment)

private fun TopologySwitchSegment.toSwitchEdge(edgeLength: Double): SwitchEdge =
    SwitchEdge(
        id = edgeId,
        direction =
            when (direction) {
                TopologyDirection.ASCENDING -> UP
                TopologyDirection.DESCENDING -> DOWN
            },
        mRange = mRange,
        edgeLength = edgeLength,
    )

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
