package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.SWITCH
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_BOUNDARY
import fi.fta.geoviite.infra.tracklayout.NodePortType.A
import fi.fta.geoviite.infra.tracklayout.NodePortType.B
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.END
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.START
import java.util.*
import kotlin.math.abs

data class TrackSwitchLink(
    val switchId: IntId<LayoutSwitch>,
    val jointNumber: JointNumber,
    val location: AlignmentPoint,
)

sealed class LocationTrackGeometry : IAlignment {
    companion object {
        val empty = TmpLocationTrackGeometry(emptyList())
    }

    @get:JsonIgnore abstract val edges: List<LayoutEdge>
    @get:JsonIgnore val edgeMs: List<Range<Double>> by lazy { calculateEdgeMs(edges) }
    @get:JsonIgnore override val segments: List<LayoutSegment> by lazy { edges.flatMap(LayoutEdge::segments) }
    override val segmentMValues: List<Range<Double>> by lazy { calculateSegmentMValues(segments) }
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(edges.mapNotNull(LayoutEdge::boundingBox)) }

    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<Double>>>
        get() = segments.zip(segmentMValues)

    // All edges have segments, all segments have points
    @get:JsonIgnore
    val isEmpty: Boolean
        get() = edges.isEmpty()

    @get:JsonIgnore
    val isNotEmpty: Boolean
        get() = edges.isNotEmpty()

    @get:JsonIgnore
    open val edgesWithM: List<Pair<LayoutEdge, Range<Double>>>
        get() = edges.zip(edgeMs)

    // TODO: GVT-1727 Use streams instead of lists here?

    @get:JsonIgnore
    open val nodes: List<EdgeNode> by lazy {
        // Init-block ensures that edges are connected: previous edge end node is the next edge start node
        edges.flatMapIndexed { i, e ->
            if (i == edges.lastIndex) listOf(e.startNode, e.endNode) else listOf(e.startNode)
        }
    }

    @get:JsonIgnore
    open val nodesWithLocation: List<Pair<EdgeNode, AlignmentPoint>> by lazy {
        edgesWithM.flatMapIndexed { i, (e, m) ->
            // Init-block ensures that edges are connected: previous edge end node is the next edge start node
            if (i == edges.lastIndex) {
                listOf(
                    e.startNode to e.firstSegmentStart.toAlignmentPoint(m.min),
                    e.endNode to e.lastSegmentEnd.toAlignmentPoint(m.min + e.segmentMValues.last().min),
                )
            } else {
                listOf(e.startNode to e.firstSegmentStart.toAlignmentPoint(m.min))
            }
        }
    }

    fun getSwitchLocation(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        nodesWithLocation.firstOrNull { (node, _) -> node.containsJoint(switchId, jointNumber) }?.second

    @get:JsonIgnore
    open val startNode: EdgeNode?
        get() = edges.firstOrNull()?.startNode

    @get:JsonIgnore
    open val endNode: EdgeNode?
        get() = edges.lastOrNull()?.endNode

    /**
     * The primary switch link at track start
     * - The inside-switch (whose geometry this track is) primarily
     * - The outside-switch (that continues after this track) secondarily
     */
    @get:JsonIgnore
    val startSwitchLink: SwitchLink?
        get() = startNode?.let { node -> pickEndJoint(node.switchOut, node.switchIn) }

    /**
     * The primary switch link at track end
     * - The inside-switch (whose geometry this track is) primarily
     * - The outside-switch (that continues after this track) secondarily
     */
    @get:JsonIgnore
    val endSwitchLink: SwitchLink?
        get() = endNode?.let { node -> pickEndJoint(node.switchIn, node.switchOut) }

    //    val outerSwitches: Pair<SwitchLink?, SwitchLink?>
    //        get() = startNode?.switchIn to endNode?.switchOut
    //
    //    val innerSwitches: List<SwitchLink>
    //        get() =
    //            nodes.flatMapIndexed { index, node ->
    //                when (index) {
    //                    0 -> listOfNotNull(node.switchOut)
    //                    nodes.lastIndex -> listOfNotNull(node.switchIn)
    //                    else -> listOfNotNull(node.switchIn, node.switchOut)
    //                }
    //            }

    @get:JsonIgnore
    val switchLinks: List<SwitchLink> by lazy {
        nodes.flatMap { node -> listOfNotNull(node.switchIn, node.switchOut) }.distinct()
    }

    @get:JsonIgnore
    val trackSwitchLinks: List<TrackSwitchLink> by lazy {
        nodesWithLocation
            .flatMap { (node, location) ->
                val switchIn = node.switchIn?.let { TrackSwitchLink(it.id, it.jointNumber, location) }
                val switchOut = node.switchOut?.let { TrackSwitchLink(it.id, it.jointNumber, location) }
                listOfNotNull(switchIn, switchOut)
            }
            .distinct()
    }

    /**
     * This picks the to-display "track end joint" from various combinations of track inner switches (switch is part of
     * track geometry) and outer switches (track ends at the switch start). Normally, the inner one is the preferred
     * one, but in cases where there are two switches following each other, a presentation joint is preferred, as that's
     * the logical node of the topology.
     */
    private fun pickEndJoint(trackInnerJoint: SwitchLink?, trackOuterJoint: SwitchLink?): SwitchLink? =
        trackInnerJoint?.takeIf { j -> j.jointRole == SwitchJointRole.MAIN }
            ?: trackOuterJoint?.takeIf { j -> j.jointRole == SwitchJointRole.MAIN }
            ?: trackInnerJoint
            ?: trackOuterJoint

    fun containsSwitch(switchId: IntId<LayoutSwitch>): Boolean = switchLinks.any { sl -> sl.id == switchId }

    abstract fun withLocationTrackId(id: IntId<LocationTrack>): LocationTrackGeometry

    fun getEdgeStartAndEnd(edgeIndices: IntRange): Pair<AlignmentPoint, AlignmentPoint> {
        val start = nodesWithLocation.first { (node, _) -> node == edges[edgeIndices.first].startNode }.second
        val end = nodesWithLocation.first { (node, _) -> node == edges[edgeIndices.last].endNode }.second
        return start to end
    }

    fun getEdgeAtM(m:Double):LayoutEdge? {
        // TODO: optimize
        return edgesWithM.firstOrNull{(_, mRange) -> mRange.contains(m)}?.first
    }
}

fun calculateEdgeMs(edges: List<LayoutEdge>): List<Range<Double>> {
    var previousEnd = 0.0
    return edges.map { edge -> Range(previousEnd, previousEnd + edge.length).also { previousEnd += edge.length } }
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
    }
    trackId?.let { id ->
        edges.firstOrNull()?.startNode?.trackBoundaryIn?.also { trackBoundary ->
            require(trackBoundary.id == id) {
                "Track geometry start node can only be the start of said track: trackId=$id trackBoundary=$trackBoundary"
            }
        }
        edges.lastOrNull()?.endNode?.trackBoundaryIn?.also { trackBoundary ->
            require(trackBoundary.id == id) {
                "Track geometry end node can only be the end of said track: trackId=$id trackBoundary=$trackBoundary"
            }
        }
    }
}

data class TmpLocationTrackGeometry(@get:JsonIgnore override val edges: List<LayoutEdge>) : LocationTrackGeometry() {
    @get:JsonIgnore
    override val startNode: EdgeNode?
        get() = edges.firstOrNull()?.startNode

    @get:JsonIgnore
    override val endNode: EdgeNode?
        get() = edges.lastOrNull()?.endNode

    init {
        verifyTrackGeometry(null, edges)
    }

    override fun withLocationTrackId(id: IntId<LocationTrack>): TmpLocationTrackGeometry {
        val newEdges = edges.map { it.reifyNodeTrackId(id) }
        return if (newEdges == edges) this else return TmpLocationTrackGeometry(newEdges)
    }

    companion object {
        fun ofSegments(segments: List<LayoutSegment>) = TmpLocationTrackGeometry(listOf(TmpLayoutEdge.of(segments)))
    }
}

data class DbLocationTrackGeometry(
    @get:JsonIgnore val trackRowVersion: LayoutRowVersion<LocationTrack>,
    @get:JsonIgnore override val edges: List<DbLayoutEdge>,
) : LocationTrackGeometry() {
    init {
        verifyTrackGeometry(trackRowVersion.id, edges)
    }

    //    @Suppress("UNCHECKED_CAST")
    //    override val edgesWithM: List<Pair<DbLayoutEdge, Range<Double>>>
    //        get() = super.edgesWithM as List<Pair<DbLayoutEdge, Range<Double>>>
    //
    //    @Suppress("UNCHECKED_CAST")
    //    override val nodes: List<DbEdgeNode>
    //        get() = super.nodes as List<DbEdgeNode>
    //
    //    @Suppress("UNCHECKED_CAST")
    //    override val nodesWithLocation: List<Pair<DbEdgeNode, AlignmentPoint>>
    //        get() = super.nodesWithLocation as List<Pair<DbEdgeNode, AlignmentPoint>>
    //
    //    override val startNode: DbEdgeNode?
    //        get() = edges.firstOrNull()?.startNode
    //
    //    override val endNode: DbEdgeNode?
    //        get() = edges.lastOrNull()?.endNode

    override fun withLocationTrackId(id: IntId<LocationTrack>): LocationTrackGeometry {
        return if (trackRowVersion.id == id) this else TmpLocationTrackGeometry(edges.map { it.reifyNodeTrackId(id) })
    }
}

sealed class LayoutEdge : IAlignment {
    abstract val startNode: EdgeNode
    abstract val endNode: EdgeNode
    override val segmentMValues: List<Range<Double>> by lazy { calculateSegmentMValues(segments) }

    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<Double>>>
        get() = segments.zip(segmentMValues)

    @get:JsonIgnore
    override val firstSegmentStart: SegmentPoint
        get() = segments.first().segmentStart

    @get:JsonIgnore
    override val lastSegmentEnd: SegmentPoint
        get() = segments.last().segmentEnd

    @get:JsonIgnore
    override val start: AlignmentPoint
        get() = firstSegmentStart.toAlignmentPoint(0.0) // alignmentStart

    @get:JsonIgnore
    override val end: AlignmentPoint
        get() = lastSegmentEnd.toAlignmentPoint(segmentMValues.last().min)

    abstract override val segments: List<LayoutSegment>

    override val boundingBox: BoundingBox by lazy {
        requireNotNull(boundingBoxCombining(segments.mapNotNull(ISegment::boundingBox))) {
            "An edge must have segments, so it must have a bounding box"
        }
    }
    @get:JsonIgnore val contentHash: Int by lazy { Objects.hash(startNode.contentHash, endNode.contentHash, segments) }

    fun withStartNode(newStartNode: EdgeNode) = TmpLayoutEdge(newStartNode, endNode, segments)

    fun withEndNode(newEndNode: EdgeNode) = TmpLayoutEdge(startNode, newEndNode, segments)

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): LayoutEdge {
        val start = startNode.withoutSwitch(switchId)
        val end = endNode.withoutSwitch(switchId)
        return this.takeIf { startNode == start && endNode == end } ?: TmpLayoutEdge(start, end, segments)
    }

    fun reifyNodeTrackId(id: IntId<LocationTrack>): LayoutEdge {
        val newStart = startNode.takeIf { n -> n.type != TRACK_BOUNDARY } ?: startNode.withInnerBoundary(id, START)
        val newEnd = endNode.takeIf { n -> n.type != TRACK_BOUNDARY } ?: endNode.withInnerBoundary(id, END)
        return if (newStart == startNode && newEnd == endNode) this else TmpLayoutEdge(newStart, newEnd, segments)
    }
}

fun verifyEdgeContent(edge: LayoutEdge) {
    // TODO: GVT-2934 fix the data and re-enable this
    // Our base data is broken so that there's bad edges like this. It's the same in original segments as well.
    //        require(startNodeId != endNodeId) { "Start and end node must be different: start=$startNodeId
    // end=$endNodeId" }
    require(edge.segments.isNotEmpty()) { "LayoutEdge must have at least one segment" }
    edge.segmentMValues.forEach { range ->
        require(range.min.isFinite() && range.min >= 0.0) { "Invalid start m: ${range.min}" }
        require(range.max.isFinite() && range.max >= range.min) { "Invalid end m: ${range.max}" }
    }
    edge.segmentMValues.zipWithNext().mapIndexed { i, (prev, next) ->
        require(abs(prev.max - next.min) < 0.001) {
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
    // TODO: GVT-2926 We shouldn't have edges like this, but we do. What's up?
    // We shouldn't really have edges between null and a joint, but due to old data, we do
    //        require(
    //            startNode.switchOut == null || endNode.switchIn == null || startNode.switchOut?.id ==
    // endNode.switchIn?.id
    //        ) {
    //            "An edge that is switch internal geometry, can only be that for one switch:
    // start=${startNode.switchOut} end=${endNode.switchIn}"
    //        }
}

data class TmpLayoutEdge(
    override val startNode: EdgeNode,
    override val endNode: EdgeNode,
    @get:JsonIgnore override val segments: List<LayoutSegment>,
) : LayoutEdge() {
    companion object {
        fun of(segments: List<LayoutSegment>) = TmpLayoutEdge(PlaceHolderEdgeNode, PlaceHolderEdgeNode, segments)
    }

    init {
        verifyEdgeContent(this)
    }
}

data class DbLayoutEdge(
    val id: IntId<LayoutEdge>,
    override val startNode: DbEdgeNode,
    override val endNode: DbEdgeNode,
    @get:JsonIgnore override val segments: List<LayoutSegment>,
) : LayoutEdge() {
    init {
        verifyEdgeContent(this)
    }
}

sealed class EdgeNode {
    companion object {
        fun trackBoundary(id: IntId<LocationTrack>, type: TrackBoundaryType): TmpEdgeNode =
            trackBoundary(TrackBoundary(id, type))

        fun trackBoundary(inner: TrackBoundary, outer: TrackBoundary? = null): TmpEdgeNode {
            val (trackA, trackB) = inNodeOrder(inner, outer)
            val portConnection = if (inner == trackA) A else B
            return TmpEdgeNode(portConnection, TmpTrackBoundaryNode(trackA, trackB))
        }

        fun switch(inner: SwitchLink?, outer: SwitchLink?): TmpEdgeNode {
            val (switchA, switchB) = inNodeOrder(inner, outer)
            val portConnection = if (inner == switchA) A else B
            return TmpEdgeNode(portConnection, TmpSwitchNode(switchA, switchB))
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

    fun containsSwitch(switchId: IntId<LayoutSwitch>) = switches.any { s -> s.id == switchId }

    fun containsJoint(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        switches.any { s -> s.matches(switchId, jointNumber) }

    @get:JsonIgnore val contentHash: Int by lazy { Objects.hash(portConnection, node.contentHash) }

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): EdgeNode =
        if (containsSwitch(switchId)) {
            val remainingSwitch = switches.singleOrNull { it.id != switchId }
            remainingSwitch?.let { switch ->
                val newConnection = if (switch == node?.portA) portConnection else portConnection.opposite
                TmpEdgeNode(newConnection, TmpSwitchNode(switch, null))
            } ?: PlaceHolderEdgeNode
        } else {
            this
        }

    fun withInnerBoundary(id: IntId<LocationTrack>, type: TrackBoundaryType) =
        takeIf { n -> n.trackBoundaryIn?.id == id && n.trackBoundaryIn?.type == type }
            ?: trackBoundary(inner = TrackBoundary(id, type), outer = trackBoundaryOut)

    fun flipPort(): TmpEdgeNode = TmpEdgeNode(portConnection.opposite, node)
}

data class DbEdgeNode(override val portConnection: NodePortType, override val node: DbLayoutNode) : EdgeNode() {
    val id: IntId<LayoutNode>
        get() = node.id
}

data class TmpEdgeNode(override val portConnection: NodePortType, override val node: LayoutNode) : EdgeNode()

data object PlaceHolderEdgeNode : EdgeNode() {
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

sealed class LayoutNode {
    abstract val portA: NodePort
    abstract val portB: NodePort?

    val ports: List<NodePort>
        get() = listOfNotNull(portA, portB)

    fun get(port: NodePortType) = if (port == A) portA else portB

    abstract val type: LayoutNodeType

    @get:JsonIgnore val contentHash: Int by lazy { Objects.hash(portA, portB) }
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
 * - Track boundaries are replaced by placeholders so as to not point to a different track
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
            // Edges disagree on the switch content -> create a new combined node of their connected ports
            else {
                val endingNode = EdgeNode.switch(inner = previous.endNode.switchIn, outer = next.startNode.switchIn)
                val startingNode = EdgeNode.switch(inner = previous.startNode.switchIn, outer = next.endNode.switchIn)
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

fun verifySwitchNode(portA: SwitchLink, portB: SwitchLink?) {
    require(portA.id != portB?.id || portA.jointNumber != portB.jointNumber) {
        "Switch node cannot have two identical ports (they should be the same single port): portA=$portA portB=$portB"
    }
}

fun verifyTrackBoundaryNode(portA: TrackBoundary, portB: TrackBoundary?) {
    require(portA.id != portB?.id) {
        "Track boundary node cannot connect twice to the same track: portA=$portA portB=$portB"
    }
}
