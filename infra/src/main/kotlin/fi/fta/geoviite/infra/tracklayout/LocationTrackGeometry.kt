package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.linking.createGapIfNeeded
import fi.fta.geoviite.infra.linking.slice
import fi.fta.geoviite.infra.linking.splitSegments
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.SWITCH
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_BOUNDARY
import fi.fta.geoviite.infra.tracklayout.NodePortType.A
import fi.fta.geoviite.infra.tracklayout.NodePortType.B
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.END
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.START
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLinkType.INNER
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLinkType.OUTER
import java.util.*
import kotlin.math.PI
import kotlin.math.abs

enum class TrackSwitchLinkType {
    INNER,
    OUTER,
}

data class TrackSwitchLink(
    val link: SwitchLink,
    val location: AlignmentPoint<LocationTrackM>,
    val type: TrackSwitchLinkType,
) {
    val switchId: IntId<LayoutSwitch>
        get() = link.id

    val jointNumber: JointNumber
        get() = link.jointNumber

    val jointRole: SwitchJointRole
        get() = link.jointRole
}

sealed class LocationTrackGeometry : IAlignment<LocationTrackM> {
    abstract val trackId: IntId<LocationTrack>?
    @get:JsonIgnore abstract val edges: List<LayoutEdge>
    @get:JsonIgnore val edgeMs: List<Range<LineM<LocationTrackM>>> by lazy { calculateEdgeMValues(edges) }
    @get:JsonIgnore override val segments: List<LayoutSegment> by lazy { edges.flatMap(LayoutEdge::segments) }
    override val segmentMValues: List<Range<LineM<LocationTrackM>>> by lazy { calculateSegmentMValues(segments) }
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(edges.mapNotNull(LayoutEdge::boundingBox)) }

    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<LineM<LocationTrackM>>>>
        get() = segments.zip(segmentMValues)

    fun getSegmentWithM(index: Int) = segments[index] to segmentMValues[index]

    // All edges have segments, all segments have points
    @get:JsonIgnore
    val isEmpty: Boolean
        get() = edges.isEmpty()

    @get:JsonIgnore
    val isNotEmpty: Boolean
        get() = edges.isNotEmpty()

    @get:JsonIgnore
    open val edgesWithM: List<Pair<LayoutEdge, Range<LineM<LocationTrackM>>>>
        get() = edges.zip(edgeMs)

    @get:JsonIgnore
    open val nodes: List<LayoutNode> by lazy {
        // Init-block ensures that edges are connected: previous edge end is the next edge start
        edges.flatMapIndexed { i, e ->
            if (i == edges.lastIndex) listOf(e.startNode.node, e.endNode.node) else listOf(e.startNode.node)
        }
    }

    @get:JsonIgnore
    open val nodesWithLocation: List<Pair<LayoutNode, AlignmentPoint<LocationTrackM>>> by lazy {
        edgesWithM.flatMapIndexed { i, (e, m) ->
            // Init-block ensures that edges are connected: previous edge end is the next edge start
            if (i == edges.lastIndex) {
                listOf(
                    e.startNode.node to e.firstSegmentStart.toAlignmentPoint(m.min),
                    e.endNode.node to e.lastSegmentEnd.toAlignmentPoint(e.segmentMValues.last().min.toAlignmentM(m.min)),
                )
            } else {
                listOf(e.startNode.node to e.firstSegmentStart.toAlignmentPoint(m.min))
            }
        }
    }

    @get:JsonIgnore
    val switchIds: List<IntId<LayoutSwitch>>
        get() = trackSwitchLinks.map(TrackSwitchLink::switchId).distinct()

    fun getSwitchJoints(id: IntId<LayoutSwitch>, includeOuterLinks: Boolean = true): List<JointNumber> =
        trackSwitchLinks.filter { it.switchId == id && (includeOuterLinks || it.type == INNER) }.map { it.jointNumber }

    @get:JsonIgnore
    val trackSwitchLinks: List<TrackSwitchLink> by lazy {
        edgesWithM.flatMapIndexed { i, (e, m) ->
            // Init-block ensures that edges are connected: previous edge end node is the next edge
            // start node
            val start = e.firstSegmentStart.toAlignmentPoint(m.min)
            val startSwitches: List<TrackSwitchLink> =
                listOfNotNull(
                    e.startNode.switchOut?.let { TrackSwitchLink(it, start, if (i == 0) OUTER else INNER) },
                    e.startNode.switchIn?.let { TrackSwitchLink(it, start, INNER) },
                )
            val endSwitches: List<TrackSwitchLink> =
                if (i == edges.lastIndex) {
                    val end = e.lastSegmentEnd.toAlignmentPoint(e.segmentMValues.last().min.toAlignmentM(m.min))
                    listOfNotNull(
                        e.endNode.switchIn?.let { TrackSwitchLink(it, end, INNER) },
                        e.endNode.switchOut?.let { TrackSwitchLink(it, end, OUTER) },
                    )
                } else {
                    emptyList()
                }
            startSwitches + endSwitches
        }
    }

    @get:JsonIgnore
    val nodeConnections: List<NodeConnection>
        get() = edges.flatMap { listOf(it.startNode, it.endNode) }

    fun getSwitchLocation(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        trackSwitchLinks.firstOrNull { tsl -> tsl.link.matches(switchId, jointNumber) }?.location

    fun getSwitchLocations(switchId: IntId<LayoutSwitch>) =
        trackSwitchLinks.filter { tsl -> tsl.link.id == switchId }.map { tsl -> tsl.link to tsl.location }

    @get:JsonIgnore
    open val startNode: NodeConnection?
        get() = edges.firstOrNull()?.startNode

    @get:JsonIgnore
    open val endNode: NodeConnection?
        get() = edges.lastOrNull()?.endNode

    /**
     * The primary switch link at track start
     * - The inside-switch (whose geometry this track is) primarily
     * - The outside-switch (that continues after this track) secondarily
     */
    @get:JsonIgnore
    val startSwitchLink: SwitchLink?
        get() = startNode?.let(::pickPrimaryEndJoint)

    /**
     * The primary switch link at track end
     * - The inside-switch (whose geometry this track is) primarily
     * - The outside-switch (that continues after this track) secondarily
     */
    @get:JsonIgnore
    val endSwitchLink: SwitchLink?
        get() = endNode?.let(::pickPrimaryEndJoint)

    @get:JsonIgnore
    val outerStartSwitch: SwitchLink?
        get() = startNode?.switchOut

    @get:JsonIgnore
    val outerEndSwitch: SwitchLink?
        get() = endNode?.switchOut

    /**
     * This picks the to-display "track end joint" from various combinations of track inner switches (switch is part of
     * track geometry) and outer switches (track ends at the switch start). Normally, the inner one is the preferred
     * one, but in cases where there are two switches following each other, a presentation joint is preferred, as that's
     * the logical node of the topology.
     */
    private fun pickPrimaryEndJoint(nodeConnection: NodeConnection): SwitchLink? =
        nodeConnection.switchIn?.takeIf { j -> j.jointRole == SwitchJointRole.MAIN }
            ?: nodeConnection.switchOut?.takeIf { j -> j.jointRole == SwitchJointRole.MAIN }
            ?: nodeConnection.switchIn
            ?: nodeConnection.switchOut

    fun containsSwitch(switchId: IntId<LayoutSwitch>): Boolean = switchIds.contains(switchId)

    fun withDbComponents(
        dbNodes: Map<NodeHash, DbLayoutNode>,
        dbEdges: Map<EdgeHash, DbLayoutEdge>,
    ): LocationTrackGeometry =
        edges
            .map { edge -> (edge as? DbLayoutEdge) ?: dbEdges[edge.contentHash] ?: edge.withDbNodes(dbNodes) }
            .let { newEdges -> if (newEdges == edges) this else TmpLocationTrackGeometry.of(newEdges, trackId) }

    fun withLocationTrackId(id: IntId<LocationTrack>): LocationTrackGeometry =
        when {
            trackId == id -> this
            else -> TmpLocationTrackGeometry.of(edges.map { it.withLocationTrackId(id) }, id)
        }

    fun getEdgeStartAndEnd(
        edgeIndices: IntRange
    ): Pair<AlignmentPoint<LocationTrackM>, AlignmentPoint<LocationTrackM>> {
        require(edgeIndices.first >= 0 && edgeIndices.last <= edges.lastIndex) {
            "Edge indices out of bounds: first=${edgeIndices.first} last=${edgeIndices.last} edges=${edges.size}"
        }
        val start =
            edgesWithM[edgeIndices.first].let { (edge, edgeM) -> edge.firstSegmentStart.toAlignmentPoint(edgeM.min) }
        val end =
            edgesWithM[edgeIndices.last].let { (edge, edgeM) ->
                val (segment, segmentM) = edge.segmentsWithM.last()
                segment.segmentEnd.toAlignmentPoint(segmentM.min.toAlignmentM(edgeM.min))
            }
        return start to end
    }

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): LocationTrackGeometry =
        when {
            !containsSwitch(switchId) -> this
            else -> TmpLocationTrackGeometry.of(combineEdges(edges.map { e -> e.withoutSwitch(switchId) }), trackId)
        }

    fun withNodeReplacements(nodeSwaps: Map<NodeHash, LayoutNode>): LocationTrackGeometry =
        this.takeIf { nodes.none { nodeSwaps.containsKey(it.contentHash) } }
            ?: TmpLocationTrackGeometry.of(processNodeReplacements(nodeSwaps, edges), trackId)

    fun getEdgeAtMOrThrow(m: LineM<LocationTrackM>): Pair<LayoutEdge, Range<LineM<LocationTrackM>>> {
        return requireNotNull(getEdgeAtM(m)) { "Geometry does not contain edge at m $m" }
    }

    fun getEdgeAtM(
        m: LineM<LocationTrackM>,
        delta: Double = 0.000001,
    ): Pair<LayoutEdge, Range<LineM<LocationTrackM>>>? =
        edgeMs
            .binarySearch { mRange ->
                when {
                    m < mRange.min - delta -> 1
                    m > mRange.max + delta -> -1
                    else -> 0
                }
            }
            .takeIf { it >= 0 }
            ?.let(edgesWithM::getOrNull)
}

fun calculateEdgeMValues(edges: List<LayoutEdge>): List<Range<LineM<LocationTrackM>>> {
    var previousEnd = LineM<LocationTrackM>(0.0)
    return edges.map { edge ->
        Range(previousEnd, previousEnd + edge.length.distance).also { previousEnd += edge.length.distance }
    }
}

fun verifyTrackGeometry(trackId: IntId<LocationTrack>?, edges: List<LayoutEdge>) {
    edges.zipWithNext().forEach { (prev, next) ->
        require(prev.endNode.node.contentHash == next.startNode.node.contentHash) {
            "Edges should be connected: prev=${prev.endNode} next=${next.startNode}"
        }
        require(prev.endNode.type == SWITCH) {
            "Only switch nodes are allowed in the middle of the track: node=${prev.endNode}"
        }
        require(next.startNode.type == SWITCH) {
            "Only switch nodes are allowed in the middle of the track: node=${next.endNode}"
        }
        require(prev.endNode.node.portB == null || prev.endNode.portConnection != next.startNode.portConnection) {
            "Outgoing edge cannot connect to the same node port as the incoming one: prev=${prev.endNode} next=${next.startNode}"
        }

        require(prev.lastSegmentEnd.isSame(next.firstSegmentStart, 0.001)) {
            "Track edges should begin where the previous one ends: trackId=$trackId connectingNode=${prev.endNode} prevPoint=${prev.lastSegmentEnd} next=${next.firstSegmentStart}"
        }
    }
    edges.firstOrNull()?.startNode?.trackBoundaryIn?.let { boundary ->
        require(boundary.id == trackId) {
            "Track geometry start node must have the correct track ID: trackId=$trackId trackBoundary=$boundary"
        }
    }
    edges.lastOrNull()?.endNode?.trackBoundaryIn?.let { boundary ->
        require(boundary.id == trackId) {
            "Track geometry end node must have the correct track ID: trackId=$trackId trackBoundary=$boundary"
        }
    }
    val nodes = mutableSetOf<NodeHash>()
    edges
        .asSequence()
        .flatMapIndexed { i, edge -> listOfNotNull(edge.startNode.node.takeIf { i == 0 }, edge.endNode.node) }
        .filter { node -> node !is PlaceholderNode }
        .forEach { node ->
            require(!nodes.contains(node.contentHash)) {
                "Track geometry cannot contain the same node twice: trackId=$trackId node=$node"
            }
            nodes.add(node.contentHash)
        }
}

data class TmpLocationTrackGeometry
private constructor(override val edges: List<LayoutEdge>, override val trackId: IntId<LocationTrack>?) :
    LocationTrackGeometry() {

    companion object {
        val empty = of(emptyList(), null)

        /**
         * Creates a new geometry from the given edges and track ID. Any track boundary nodes are automatically
         * connected to the given trackId (or placeholder if not given).
         */
        fun of(edges: List<LayoutEdge>, trackId: IntId<LocationTrack>?): TmpLocationTrackGeometry =
            TmpLocationTrackGeometry(edges.map { e -> e.withLocationTrackId(trackId) }, trackId)

        /**
         * Creates a new geometry from the given segments as a single edge, ending with track boundary (with given track
         * ID, or placeholder if not given) on both sides.
         */
        fun ofSegments(segments: List<LayoutSegment>, trackId: IntId<LocationTrack>?): TmpLocationTrackGeometry =
            TmpLocationTrackGeometry(listOf(TmpLayoutEdge.of(segments, trackId)), trackId)
    }

    @get:JsonIgnore
    override val startNode: NodeConnection?
        get() = edges.firstOrNull()?.startNode

    @get:JsonIgnore
    override val endNode: NodeConnection?
        get() = edges.lastOrNull()?.endNode

    init {
        verifyTrackGeometry(trackId, edges)
    }
}

data class DbLocationTrackGeometry(
    @get:JsonIgnore val trackRowVersion: LayoutRowVersion<LocationTrack>,
    @get:JsonIgnore override val edges: List<DbLayoutEdge>,
) : LocationTrackGeometry() {
    override val trackId: IntId<LocationTrack>
        get() = trackRowVersion.id

    init {
        verifyTrackGeometry(trackId, edges)
    }

    @Suppress("UNCHECKED_CAST")
    override val edgesWithM: List<Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>>
        get() = super.edgesWithM as List<Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>>>

    @Suppress("UNCHECKED_CAST")
    override val nodes: List<DbLayoutNode>
        get() = super.nodes as List<DbLayoutNode>

    @Suppress("UNCHECKED_CAST")
    override val nodesWithLocation: List<Pair<DbLayoutNode, AlignmentPoint<LocationTrackM>>>
        get() = super.nodesWithLocation as List<Pair<DbLayoutNode, AlignmentPoint<LocationTrackM>>>

    override val startNode: DbNodeConnection?
        get() = edges.firstOrNull()?.startNode

    override val endNode: DbNodeConnection?
        get() = edges.lastOrNull()?.endNode
}

data class EdgeHash private constructor(val value: Int) {
    companion object {
        fun of(start: NodeConnection, end: NodeConnection, segments: List<LayoutSegment>): EdgeHash =
            EdgeHash(Objects.hash(nodeConnectionHash(start), nodeConnectionHash(end), segmentsHash(segments)))

        private fun nodeConnectionHash(nodeConnection: NodeConnection): Int =
            Objects.hash(nodeConnection.portConnection, nodeConnection.node.contentHash)

        private fun segmentsHash(segments: List<LayoutSegment>): Int = Objects.hash(segments.map(::segmentHash))

        // Note: the segment hash isn't similarly stable by content as node hash is:
        // This can change after save & read. However, the db-function for inserting segments does
        // normalize the hash and result in re-using any identical edge, so even at worst it's just
        // an extra round-trip to the db.
        private fun segmentHash(segment: LayoutSegment): Int = segment.hashCode()
    }
}

sealed class LayoutEdge : IAlignment<EdgeM> {
    abstract val startNode: NodeConnection
    abstract val endNode: NodeConnection
    override val segmentMValues: List<Range<LineM<EdgeM>>> by lazy { calculateSegmentMValues(segments) }

    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<LineM<EdgeM>>>>
        get() = segments.zip(segmentMValues)

    @get:JsonIgnore
    override val firstSegmentStart: SegmentPoint
        get() = segments.first().segmentStart

    @get:JsonIgnore
    override val lastSegmentEnd: SegmentPoint
        get() = segments.last().segmentEnd

    @get:JsonIgnore
    override val start: AlignmentPoint<EdgeM>
        get() = firstSegmentStart.toAlignmentPoint(LineM(0.0)) // alignmentStart

    @get:JsonIgnore
    override val end: AlignmentPoint<EdgeM>
        get() = lastSegmentEnd.toAlignmentPoint(segmentMValues.last().min)

    abstract override val segments: List<LayoutSegment>

    override val boundingBox: BoundingBox by lazy {
        requireNotNull(boundingBoxCombining(segments.mapNotNull(ISegment::boundingBox))) {
            "An edge must have segments, so it must have a bounding box"
        }
    }

    @get:JsonIgnore val contentHash: EdgeHash by lazy { EdgeHash.of(startNode, endNode, segments) }

    fun withSegments(newSegments: List<LayoutSegment>) = TmpLayoutEdge(startNode, endNode, newSegments)

    fun withStartNode(newStartNode: NodeConnection) = TmpLayoutEdge(newStartNode, endNode, segments)

    fun withStartNode(newStartNode: LayoutNode) = withStartNode(reconnectNode(startNode, newStartNode))

    fun withEndNode(newEndNode: NodeConnection) = TmpLayoutEdge(startNode, newEndNode, segments)

    fun withNodes(newStartNode: LayoutNode, newEndNode: LayoutNode) =
        TmpLayoutEdge(
            startNode = reconnectNode(startNode, newStartNode),
            endNode = reconnectNode(endNode, newEndNode),
            segments = segments,
        )

    fun withDbNodes(dbNodes: Map<NodeHash, DbLayoutNode>): LayoutEdge {
        val newStart =
            startNode.let { it as? TmpNodeConnection }?.let { dbNodes[it.node.contentHash]?.let(it::withDbNode) }
                ?: startNode
        val newEnd =
            endNode.let { it as? TmpNodeConnection }?.let { dbNodes[it.node.contentHash]?.let(it::withDbNode) }
                ?: endNode
        return if (newStart == startNode && newEnd == endNode) {
            this
        } else {
            TmpLayoutEdge(newStart, newEnd, segments)
        }
    }

    fun withEndNode(newEndNode: LayoutNode) = withEndNode(reconnectNode(endNode, newEndNode))

    fun containsSwitch(id: IntId<LayoutSwitch>) = startNode.containsSwitch(id) || endNode.containsSwitch(id)

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): LayoutEdge {
        val start = startNode.withoutSwitch(switchId)
        val end = endNode.withoutSwitch(switchId)
        return this.takeIf { startNode == start && endNode == end } ?: TmpLayoutEdge(start, end, segments)
    }

    fun withLocationTrackId(id: IntId<LocationTrack>?): LayoutEdge {
        val newStart = startNode.takeIf { n -> n.type != TRACK_BOUNDARY } ?: startNode.withInnerBoundary(id, START)
        val newEnd = endNode.takeIf { n -> n.type != TRACK_BOUNDARY } ?: endNode.withInnerBoundary(id, END)
        return if (newStart == startNode && newEnd == endNode) this else TmpLayoutEdge(newStart, newEnd, segments)
    }

    private fun findStartConnectionM(preceding: LayoutEdge, seekDistance: Double, tolerance: Double): LineM<EdgeM>? {
        if (firstSegmentStart.isSame(preceding.lastSegmentEnd, tolerance)) return LineM(0.0)
        val prevEnd = preceding.lastSegmentEnd
        val prevEndDirection = preceding.segments.last().endDirection
        return allAlignmentPoints
            .takeWhile { p -> lineLength(prevEnd, p) <= seekDistance }
            .firstOrNull { p -> angleDiffRads(prevEndDirection, directionBetweenPoints(prevEnd, p)) < PI / 2 }
            ?.m
    }

    private fun findEndConnectionM(following: LayoutEdge, seekDistance: Double, tolerance: Double): LineM<EdgeM>? {
        if (lastSegmentEnd.isSame(following.firstSegmentStart, tolerance)) return length
        val nextStart = following.firstSegmentStart
        val nextStartDirection = following.segments.first().startDirection
        return allAlignmentPointsDownward
            .takeWhile { p -> lineLength(p, nextStart) <= seekDistance }
            .firstOrNull { p -> angleDiffRads(nextStartDirection, directionBetweenPoints(p, nextStart)) < PI / 2 }
            ?.m
    }

    fun connectStartFrom(
        previousEdge: LayoutEdge,
        maxAdjustDistance: Double,
        tolerance: Double = LAYOUT_M_DELTA,
    ): LayoutEdge? =
        findStartConnectionM(previousEdge, maxAdjustDistance, tolerance)?.let { startConnectionM ->
            val slicedSegments =
                segments.takeIf { isSame(startConnectionM.distance, 0.0, tolerance) }
                    ?: slice(segmentsWithM, Range(startConnectionM, length), tolerance)
            val connectSegment = createGapIfNeeded(previousEdge.segments, slicedSegments)
            withSegments(listOfNotNull(connectSegment) + slicedSegments)
        }

    fun connectEndTo(nextEdge: LayoutEdge, maxAdjustDistance: Double, tolerance: Double = LAYOUT_M_DELTA): LayoutEdge? =
        findEndConnectionM(nextEdge, maxAdjustDistance, tolerance)?.let { endConnectionM ->
            val slicedSegments =
                segments.takeIf { isSame(endConnectionM.distance, length.distance, tolerance) }
                    ?: slice(segmentsWithM, Range(LineM(0.0), endConnectionM), tolerance)
            val connectSegment = createGapIfNeeded(slicedSegments, nextEdge.segments)
            withSegments(slicedSegments + listOfNotNull(connectSegment))
        }
}

data class TmpLayoutEdge(
    override val startNode: NodeConnection,
    override val endNode: NodeConnection,
    @get:JsonIgnore override val segments: List<LayoutSegment>,
) : LayoutEdge() {
    companion object {
        fun of(segments: List<LayoutSegment>, trackId: IntId<LocationTrack>?): TmpLayoutEdge =
            when {
                trackId != null ->
                    TmpLayoutEdge(
                        NodeConnection.trackBoundary(trackId, START),
                        NodeConnection.trackBoundary(trackId, END),
                        segments,
                    )
                else -> TmpLayoutEdge(PlaceHolderNodeConnection, PlaceHolderNodeConnection, segments)
            }
    }

    init {
        verifyEdgeContent(this)
    }
}

data class DbLayoutEdge(
    val id: IntId<LayoutEdge>,
    override val startNode: DbNodeConnection,
    override val endNode: DbNodeConnection,
    @get:JsonIgnore override val segments: List<LayoutSegment>,
) : LayoutEdge() {
    init {
        verifyEdgeContent(this)
    }
}

sealed class NodeConnection {
    companion object {
        fun trackBoundary(id: IntId<LocationTrack>, type: TrackBoundaryType): TmpNodeConnection =
            trackBoundary(TrackBoundary(id, type))

        fun trackBoundary(inner: TrackBoundary?, outer: TrackBoundary? = null): TmpNodeConnection {
            val (trackA, trackB) = inNodeOrder(inner, outer)
            val portConnection = if (inner == trackA) A else B
            return TmpNodeConnection(portConnection, TmpTrackBoundaryNode(trackA, trackB))
        }

        fun switch(inner: SwitchLink?, outer: SwitchLink?): TmpNodeConnection {
            val (switchA, switchB) = inNodeOrder(inner, outer)
            val portConnection = if (inner == switchA) A else B
            return TmpNodeConnection(portConnection, TmpSwitchNode(switchA, switchB))
        }
    }

    @get:JsonIgnore abstract val portConnection: NodePortType

    @get:JsonIgnore abstract val node: LayoutNode

    val innerPort
        get() = node.get(portConnection)

    val outerPort
        get() = node.get(portConnection.opposite)

    val type: LayoutNodeType
        get() = node.type

    val switchIn: SwitchLink?
        get() = innerPort?.let { it as? SwitchLink }

    val switchOut: SwitchLink?
        get() = outerPort?.let { it as? SwitchLink }

    val switches: List<SwitchLink>
        get() = listOfNotNull(switchIn, switchOut)

    val trackBoundaryIn: TrackBoundary?
        get() = innerPort?.let { it as? TrackBoundary }

    val trackBoundaryOut: TrackBoundary?
        get() = outerPort?.let { it as? TrackBoundary }

    val detailLevel: DetailLevel
        get() =
            when (type) {
                TRACK_BOUNDARY -> DetailLevel.MICRO
                SWITCH -> {
                    if (switches.any { s -> s.jointRole == SwitchJointRole.MAIN }) DetailLevel.MICRO
                    else DetailLevel.NANO
                }
            }

    fun containsSwitch(switchId: IntId<LayoutSwitch>) = node.containsSwitch(switchId)

    fun containsJoint(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        node.containsJoint(switchId, jointNumber)

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): NodeConnection =
        if (containsSwitch(switchId)) {
            val remainingSwitch = switches.singleOrNull { it.id != switchId }
            remainingSwitch?.let { switch ->
                val newConnection = if (switch == node?.portA) portConnection else portConnection.opposite
                TmpNodeConnection(newConnection, TmpSwitchNode(switch, null))
            } ?: PlaceHolderNodeConnection
        } else {
            this
        }

    fun withInnerBoundary(id: IntId<LocationTrack>?, type: TrackBoundaryType): NodeConnection =
        when {
            trackBoundaryIn?.id == id && trackBoundaryIn?.type == type -> this
            id != null -> trackBoundary(TrackBoundary(id, type), trackBoundaryOut)
            trackBoundaryOut != null -> trackBoundary(null, trackBoundaryOut)
            else -> PlaceHolderNodeConnection
        }

    fun flipPort(): TmpNodeConnection = TmpNodeConnection(portConnection.opposite, node)
}

data class DbNodeConnection(override val portConnection: NodePortType, override val node: DbLayoutNode) :
    NodeConnection() {
    val id: IntId<LayoutNode>
        get() = node.id
}

data class TmpNodeConnection(override val portConnection: NodePortType, override val node: LayoutNode) :
    NodeConnection() {
    fun withDbNode(dbNode: DbLayoutNode) = DbNodeConnection(portConnection, dbNode.also { require(node.isSame(it)) })
}

data object PlaceHolderNodeConnection : NodeConnection() {
    override val portConnection: NodePortType = A
    override val node: PlaceholderNode = PlaceholderNode
}

enum class LayoutNodeType {
    SWITCH,
    TRACK_BOUNDARY,
}

enum class TrackBoundaryType {
    START,
    END,
}

enum class NodePortType {
    A,
    B;

    val opposite
        get() = if (this == A) B else A
}

data class NodeHash private constructor(val value: Int) {
    companion object {
        fun of(portA: NodePort, portB: NodePort?): NodeHash = NodeHash(Objects.hash(portA, portB))
    }
}

sealed class LayoutNode {
    abstract val portA: NodePort
    abstract val portB: NodePort?

    val ports: List<NodePort>
        get() = listOfNotNull(portA, portB)

    fun get(port: NodePortType) = if (port == A) portA else portB

    fun containsSwitch(switchId: IntId<LayoutSwitch>): Boolean =
        ports.any { port -> (port as? SwitchLink)?.id == switchId }

    fun containsJoint(switchId: IntId<LayoutSwitch>, joint: JointNumber): Boolean =
        ports.any { port -> (port as? SwitchLink)?.matches(switchId, joint) ?: false }

    fun containsBoundary(boundary: TrackBoundary): Boolean = ports.any { port -> port == boundary }

    fun forEachPort(onPort: (port: NodePort, type: NodePortType) -> Unit) {
        onPort(portA, A)
        if (type == SWITCH && portB == null) {
            onPort(EmptyPort, B)
        } else {
            portB?.let { b -> onPort(b, B) }
        }
    }

    abstract val type: LayoutNodeType

    companion object {
        fun of(link1: SwitchLink, link2: SwitchLink? = null): LayoutNode =
            inNodeOrder(link1, link2).let { (portA, portB) -> TmpSwitchNode(portA, portB) }

        fun of(link1: TrackBoundary, link2: TrackBoundary? = null) =
            inNodeOrder(link1, link2).let { (portA, portB) -> TmpTrackBoundaryNode(portA, portB) }
    }

    @get:JsonIgnore val contentHash: NodeHash by lazy { NodeHash.of(portA, portB) }

    fun isSame(other: LayoutNode): Boolean = other.portA == portA && other.portB == portB
}

sealed class DbLayoutNode : LayoutNode() {
    abstract val id: IntId<LayoutNode>
}

sealed class TmpLayoutNode : LayoutNode()

data class DbSwitchNode(
    override val id: IntId<LayoutNode>,
    override val portA: SwitchLink,
    override val portB: SwitchLink?,
) : DbLayoutNode() {
    override val type: LayoutNodeType = SWITCH

    init {
        verifySwitchNode(portA, portB)
    }
}

data class TmpSwitchNode(override val portA: SwitchLink, override val portB: SwitchLink? = null) : TmpLayoutNode() {
    override val type: LayoutNodeType = SWITCH

    init {
        verifySwitchNode(portA, portB)
    }
}

data class DbTrackBoundaryNode(
    override val id: IntId<LayoutNode>,
    override val portA: TrackBoundary,
    override val portB: TrackBoundary? = null,
) : DbLayoutNode() {
    override val type: LayoutNodeType = TRACK_BOUNDARY

    init {
        verifyTrackBoundaryNode(portA, portB)
    }
}

data class TmpTrackBoundaryNode(override val portA: TrackBoundary, override val portB: TrackBoundary? = null) :
    TmpLayoutNode() {
    constructor(id: IntId<LocationTrack>, type: TrackBoundaryType) : this(TrackBoundary(id, type), null)

    override val type: LayoutNodeType = TRACK_BOUNDARY

    init {
        verifyTrackBoundaryNode(portA, portB)
    }
}

data object PlaceholderNode : TmpLayoutNode() {
    override val type: LayoutNodeType = TRACK_BOUNDARY
    override val portA: EmptyPort = EmptyPort
    override val portB: EmptyPort? = null
}

sealed class NodePort {
    abstract fun isBefore(other: NodePort): Boolean

    abstract fun isSame(other: NodePort): Boolean
}

data object EmptyPort : NodePort() {
    override fun isBefore(other: NodePort): Boolean {
        require(other is EmptyPort) { "Cannot compare ${EmptyPort::class.simpleName} with $other" }
        return true
    }

    override fun isSame(other: NodePort): Boolean = (other == this)
}

data class TrackBoundary(val id: IntId<LocationTrack>, val type: TrackBoundaryType) : NodePort() {
    override fun isSame(other: NodePort): Boolean = (other is TrackBoundary && other.id == id && other.type == type)

    override fun isBefore(other: NodePort): Boolean {
        require(other is TrackBoundary) { "Cannot compare ${TrackBoundary::class.simpleName} with $other" }
        return id.intValue < other.id.intValue ||
            (id.intValue == other.id.intValue && type.ordinal <= other.type.ordinal)
    }
}

data class SwitchLink(val id: IntId<LayoutSwitch>, val jointRole: SwitchJointRole, val jointNumber: JointNumber) :
    NodePort() {
    constructor(
        id: IntId<LayoutSwitch>,
        jointNumber: JointNumber,
        structure: SwitchStructure,
    ) : this(id, SwitchJointRole.of(structure, jointNumber), jointNumber)

    fun matches(switchId: IntId<LayoutSwitch>, switchJointNumber: JointNumber) =
        id == switchId && jointNumber == switchJointNumber

    override fun isSame(other: NodePort): Boolean =
        (other is SwitchLink && other.id == id && other.jointNumber == jointNumber)

    override fun isBefore(other: NodePort): Boolean {
        require(other is SwitchLink) { "Cannot compare ${SwitchLink::class.simpleName} with $other" }
        return id.intValue < other.id.intValue ||
            (id.intValue == other.id.intValue && jointNumber.intValue <= other.jointNumber.intValue)
    }
}

/**
 * Combine edges from different sources into a single geometry:
 * - Edges without a switch between them are combined into a single edge
 * - If either of the edges points to a switch between them, both edges are linked to it
 * - If edges point to having different switches between them, a new combined node is placed there
 */
fun combineEdges(edges: List<LayoutEdge>): List<LayoutEdge> {
    if (edges.isEmpty()) return edges
    val combined = mutableListOf<LayoutEdge>()
    var previous: LayoutEdge? = null
    for (next in edges) {
        if (previous == null) {
            previous = next
        }
        // Both edges agree there's a switch node between them
        else if (previous.endNode.type == SWITCH && next.startNode.type == SWITCH) {
            // Both edges agree on the switch content -> move on
            if (next.startNode.node.contentHash == previous.endNode.node.contentHash) {
                combined.add(previous)
                previous = next
            }
            // Edges disagree on the switch content -> create a new combined node of their connected
            // ports
            else {
                val endingNode =
                    NodeConnection.switch(inner = previous.endNode.switchIn, outer = next.startNode.switchIn)
                val startingNode =
                    NodeConnection.switch(inner = next.startNode.switchIn, outer = previous.endNode.switchIn)
                require(endingNode.node.contentHash == startingNode.node.contentHash) {
                    "Failed to resolve dual-switch node: previous=$endingNode next=$startingNode"
                }
                combined.add(previous.withEndNode(endingNode))
                previous = next.withStartNode(startingNode)
            }
        }
        // Previous has a switch node -> mark the next one to start from that node as well
        else if (previous.endNode.type == SWITCH) {
            combined.add(previous)
            previous = next.withStartNode(previous.endNode.flipPort())
        }
        // Next has a switch node -> mark the previous one to end at that node as well
        else if (next.startNode.type == SWITCH) {
            combined.add(previous.withEndNode(next.startNode.flipPort()))
            previous = next
        }
        // Neither has a switch node -> combine them into a single edge
        else {
            previous =
                TmpLayoutEdge(
                    startNode = previous.startNode,
                    endNode = next.endNode,
                    segments = previous.segments + next.segments,
                )
        }
    }
    previous?.let(combined::add)
    return combined.toList()
}

private fun verifyEdgeContent(edge: LayoutEdge) {
    val startNode = edge.startNode.node
    val endNode = edge.endNode.node
    require(startNode is PlaceholderNode || startNode.contentHash != endNode.contentHash) {
        "Start and end node must be different (edge cannot loop back on itself): start=$startNode end=$endNode"
    }
    require(edge.segments.isNotEmpty()) { "LayoutEdge must have at least one segment" }
    edge.segmentMValues.forEach { range ->
        require(range.min.isFinite() && range.min.distance >= 0.0) { "Invalid start m: ${range.min}" }
        require(range.max.isFinite() && range.max >= range.min) { "Invalid end m: ${range.max}" }
    }
    edge.segmentMValues.zipWithNext().mapIndexed { i, (prev, next) ->
        require(abs(prev.max.distance - next.min.distance) < 0.001) {
            "Edge segment m-values should be continuous: index=$i prev=$prev next=$next"
        }
    }
    edge.segments.zipWithNext().mapIndexed { i, (prev, next) ->
        require(prev.segmentEnd.isSame(next.segmentStart, 0.001)) {
            "Edge segments should begin where the previous one ends: index=$i prev=${prev.segmentEnd} next=${next.segmentStart}"
        }
    }
    edge.startNode.trackBoundaryIn?.let { innerBoundary ->
        require(innerBoundary.type == START) {
            "Edge start node must not be a a track end: $edge start(inner)=$innerBoundary"
        }
    }
    edge.endNode.trackBoundaryIn?.let { innerBoundary ->
        require(innerBoundary.type == END) {
            "Edge end node must not be a a track start: $edge end(inner)=$innerBoundary"
        }
    }
}

private fun verifySwitchNode(portA: SwitchLink, portB: SwitchLink?) {
    require(portA.id != portB?.id) {
        "A node cannot have two ports for the same switch (2 joints in one location): portA=$portA portB=$portB"
    }
    require(portA.id != portB?.id || portA.jointNumber != portB.jointNumber) {
        "Switch node cannot have two identical ports (they should be the same single port): portA=$portA portB=$portB"
    }
}

private fun verifyTrackBoundaryNode(portA: TrackBoundary, portB: TrackBoundary?) {
    require(portA.id != portB?.id) {
        "A node cannot have two ports for the same track boundary (2 ends in one location): portA=$portA portB=$portB"
    }
}

private fun <T : NodePort> inNodeOrder(linkIn: T?, linkOut: T?): Pair<T, T?> {
    require(linkIn != null || linkOut != null) { "A node must have at least one port" }
    return when {
        linkIn == null -> requireNotNull(linkOut) to null
        linkOut == null -> linkIn to null
        linkIn.isSame(linkOut) -> linkIn to null
        linkIn.isBefore(linkOut) -> linkIn to linkOut
        else -> linkOut to linkIn
    }
}

private fun processNodeReplacements(
    replacements: Map<NodeHash, LayoutNode>,
    edges: List<LayoutEdge>,
): List<LayoutEdge> = buildList {
    // This is notably stateful as each change is simpler to handle as an operation, but multiple
    // may end up affecting the same edge due to replacements sometimes merging/splitting them.
    // We maintain an edit window of 3 edges, where the middle one is the one being processed.
    var previous: LayoutEdge? = null
    var current: LayoutEdge? = edges.getOrNull(0)
    var next: LayoutEdge? = edges.getOrNull(1)
    var index = 0
    while (current != null) {
        // The replacement may result in the current edge getting merged with the next or previous
        // one
        val (editedPrevious, editedCurrent, editedNext) = replaceNodes(previous, current, next, replacements)

        // Move the edit-window forward, maintaining partially edited edges
        index++
        previous = editedCurrent ?: editedPrevious
        current = editedNext
        next = edges.getOrNull(index + 1)

        // Store the edges that are done into the result list
        if (current != null) {
            // More yet to process -> add edges as they move out of the processing window
            editedPrevious?.takeIf { it != previous }?.let(::add)
        } else {
            // We're done -> add result edges
            editedPrevious?.let(::add)
            editedCurrent?.let(::add)
        }
    }
}

private fun replaceNodes(
    previous: LayoutEdge?,
    current: LayoutEdge,
    next: LayoutEdge?,
    replacements: Map<NodeHash, LayoutNode>,
): Triple<LayoutEdge?, LayoutEdge?, LayoutEdge?> {
    val newStartNode = replacements[current.startNode.node.contentHash]
    val newEndNode = replacements[current.endNode.node.contentHash]
    val newStartHash = newStartNode?.contentHash ?: current.startNode.node.contentHash
    val newEndHash = newEndNode?.contentHash ?: current.endNode.node.contentHash
    return when {
        newStartNode == null && newEndNode == null -> Triple(previous, current, next)
        // Special case where both ends would connect to the same node -> one of them needs to go
        newStartHash == newEndHash ->
            mergeEdgeWithPeers(previous, current, next, requireNotNull(newStartNode ?: newEndNode))
        newStartNode == null -> Triple(previous, current.withEndNode(requireNotNull(newEndNode)), next)
        newEndNode == null -> Triple(previous, current.withStartNode(newStartNode), next)
        else -> Triple(previous, current.withNodes(newStartNode, newEndNode), next)
    }
}

private fun mergeEdgeWithPeers(
    previous: LayoutEdge?,
    target: LayoutEdge,
    next: LayoutEdge?,
    replacementNode: LayoutNode,
    snapDistance: Double = 0.1,
): Triple<LayoutEdge?, LayoutEdge?, LayoutEdge?> =
    when {
        // Can't do it: the replacements would connect track ends to each other
        // This can happen in intermediate states if the track is short, so we can't throw
        previous == null && next == null -> Triple(null, target, null)
        // This is the first edge: merge it with the next one & the node becomes the start
        previous == null -> {
            requireNotNull(next)
            Triple(
                null,
                null,
                TmpLayoutEdge(
                    startNode = reconnectNode(next.startNode, replacementNode),
                    endNode = next.endNode,
                    segments = target.segments + next.segments,
                ),
            )
        }
        // This is the last edge: merge it with the previous one & the node becomes the end
        next == null ->
            Triple(
                TmpLayoutEdge(
                    startNode = previous.startNode,
                    endNode = reconnectNode(previous.endNode, replacementNode),
                    segments = previous.segments + target.segments,
                ),
                null,
                null,
            )
        // Between nodes: split the edge, dividing segments between previous/next
        // The node comes in the middle, connecting the two edges
        else -> {
            val (preSegments, postSegments) = splitSegments(target.segmentsWithM, target.length * 0.5, snapDistance)
            Triple(
                TmpLayoutEdge(
                    startNode = previous.startNode,
                    endNode = reconnectNode(previous.endNode, replacementNode),
                    segments = previous.segments + preSegments,
                ),
                null,
                TmpLayoutEdge(
                    startNode = reconnectNode(next.startNode, replacementNode),
                    endNode = next.endNode,
                    segments = postSegments + next.segments,
                ),
            )
        }
    }

private fun reconnectNode(currentNodeConnection: NodeConnection, newNode: LayoutNode): TmpNodeConnection =
    when {
        currentNodeConnection.type == TRACK_BOUNDARY && newNode.type == SWITCH ->
            TmpNodeConnection(B, newNode).also { require(newNode.portB == null) }

        // Normally, the inner port should already be defined and reconnecting should only produce a topology link
        newNode.portA == currentNodeConnection.innerPort -> TmpNodeConnection(A, newNode)
        newNode.portB == currentNodeConnection.innerPort -> TmpNodeConnection(B, newNode)

        // However, in partial linking situations (new track connecting to existing switch-combination), the
        // intermediate state requires connecting to a combination node based on the outer port and replacing the inner
        // one. This can create an edge with conflicting inner ports (the opposite side is not the same switch), but
        // it should be corrected when the second switch is linked to the track. Publication validation must ensure
        // that the broken state does not go into official layout.

        // For such a connection, we need to have an outer port to correctly resolve the orientation
        currentNodeConnection.outerPort == null ->
            error(
                "Unable to replace inner node since there is no outer port to resolve the connection with: " +
                    "current=$currentNodeConnection new=$newNode"
            )
        newNode.portA == currentNodeConnection.outerPort -> TmpNodeConnection(B, newNode)
        newNode.portB == currentNodeConnection.outerPort -> TmpNodeConnection(A, newNode)

        // Insufficient information to orient the new node
        else ->
            error(
                "Unable to replace edge node since it doesn't seem related to the current one: " +
                    "current=$currentNodeConnection new=$newNode"
            )
    }

fun replaceEdges(
    geometry: LocationTrackGeometry,
    edgesToReplace: List<LayoutEdge>,
    newEdges: List<LayoutEdge>,
): LocationTrackGeometry {
    return TmpLocationTrackGeometry.of(
        replaceEdges(originalEdges = geometry.edges, edgesToReplace, newEdges),
        geometry.trackId,
    )
}

private fun replaceEdges(
    originalEdges: List<LayoutEdge>,
    edgesToReplace: List<LayoutEdge>,
    newEdges: List<LayoutEdge>,
): List<LayoutEdge> {
    val replaceStartIndex =
        originalEdges.indexOfFirst { originalEdge ->
            originalEdge.startNode.node == edgesToReplace.first().startNode.node
        }
    val replaceEndIndex =
        originalEdges.indexOfLast { originalEdge -> originalEdge.endNode.node == edgesToReplace.last().endNode.node }
    require(replaceStartIndex != -1 && replaceEndIndex != -1) { "Cannot replace non existing edges" }
    val newAllEdges =
        originalEdges.subList(0, replaceStartIndex) +
            newEdges +
            originalEdges.subList(replaceEndIndex + 1, originalEdges.lastIndex + 1)
    return combineEdges(newAllEdges)
}
