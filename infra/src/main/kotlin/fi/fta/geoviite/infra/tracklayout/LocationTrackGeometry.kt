package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.tracklayout.EdgeNodeDirection.DECREASING
import fi.fta.geoviite.infra.tracklayout.EdgeNodeDirection.INCREASING
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.SWITCH
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_END
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_START
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
}

fun calculateEdgeMs(edges: List<LayoutEdge>): List<Range<Double>> {
    var previousEnd = 0.0
    return edges.map { edge -> Range(previousEnd, previousEnd + edge.length).also { previousEnd += edge.length } }
}

fun verifyTrackGeometry(edges: List<LayoutEdge>) {
    edges.zipWithNext().forEach { (prev, next) ->
        require(prev.endNode.node?.contentHash == next.startNode.node?.contentHash) {
            "Edges should be connected: prev=${prev.endNode} next=${next.startNode}"
        }
        require(prev.endNode.type == SWITCH) {
            "Only switch nodes are allowed in the middle of the track: node=${prev.endNode}"
        }
        require(next.startNode.type == SWITCH) {
            "Only switch nodes are allowed in the middle of the track: node=${next.endNode}"
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
        verifyTrackGeometry(edges)
    }

    override fun withLocationTrackId(id: IntId<LocationTrack>): TmpLocationTrackGeometry {
        val newEdges = edges.map { it.reifyNodeTrackId(id) }
        return if (newEdges == edges) this else return TmpLocationTrackGeometry(newEdges)
    }
}

data class DbLocationTrackGeometry(
    @get:JsonIgnore val trackRowVersion: LayoutRowVersion<LocationTrack>,
    @get:JsonIgnore override val edges: List<DbLayoutEdge>,
) : LocationTrackGeometry() {
    init {
        verifyTrackGeometry(edges)
        edges.firstOrNull()?.startNode?.startingTrackId?.also { trackId ->
            require(trackId == trackRowVersion.id) {
                "Track geometry start node can only be the start of said track: trackVersion=$trackRowVersion trackId=$trackId"
            }
        }
        edges.firstOrNull()?.endNode?.endingTrackId?.also { trackId ->
            require(trackId == trackRowVersion.id) {
                "Track geometry end node can only be the end of said track: trackVersion=$trackRowVersion trackId=$trackId"
            }
        }
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

    fun withStartNode(node: EdgeNode) = TmpLayoutEdge(node, endNode, segments)

    fun withEndNode(node: EdgeNode) = TmpLayoutEdge(endNode, node, segments)

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): LayoutEdge {
        val start = startNode.withoutSwitch(switchId)
        val end = endNode.withoutSwitch(switchId)
        return this.takeIf { startNode == start && endNode == end } ?: TmpLayoutEdge(start, end, segments)
    }

    fun reifyNodeTrackId(id: IntId<LocationTrack>): LayoutEdge {
        val newStart =
            if (startNode is PlaceHolderEdgeNode) TmpEdgeNode(INCREASING, TmpTrackStartNode(id)) else startNode
        val newEnd = if (endNode is PlaceHolderEdgeNode) TmpEdgeNode(INCREASING, TmpTrackEndNode(id)) else endNode
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
    edge.segmentMValues.zipWithNext().map { (prev, next) ->
        require(abs(prev.max - next.min) < 0.001) {
            "Edge segment m-values should be continuous: prev=$prev next=$next"
        }
    }
    edge.segments.zipWithNext().map { (prev, next) ->
        require(prev.segmentEnd.isSame(next.segmentStart, 0.001)) {
            "Edge segments should begin where the previous one ends: prev=${prev.segmentEnd} next=${next.segmentStart}"
        }
    }
    require(edge.startNode.type != TRACK_END) {
        "Edge start node must not be a a track end: $edge node=${edge.startNode}"
    }
    require(edge.endNode.type != TRACK_START) {
        "Edge end node must not be a a track start: $edge node=${edge.endNode}"
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
        fun of(segments: List<LayoutSegment>) = TmpLayoutEdge(EdgeNode.placeHolder, EdgeNode.placeHolder, segments)
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

enum class EdgeNodeDirection {
    INCREASING,
    DECREASING,
}

sealed class EdgeNode {
    companion object {
        val placeHolder = PlaceHolderEdgeNode()

        fun trackStart(trackId: IntId<LocationTrack>) = TmpEdgeNode(INCREASING, TmpTrackStartNode(trackId))

        fun trackEnd(trackId: IntId<LocationTrack>) = TmpEdgeNode(INCREASING, TmpTrackEndNode(trackId))

        fun switch(linkIn: SwitchLink?, linkOut: SwitchLink?): TmpEdgeNode {
            val (switch1, switch2) = inNodeOrder(linkIn, linkOut)
            val direction = if (switch1 == linkIn) INCREASING else DECREASING
            return TmpEdgeNode(direction, TmpSwitchNode(switch1, switch2))
        }

        private fun inNodeOrder(linkIn: SwitchLink?, linkOut: SwitchLink?): Pair<SwitchLink, SwitchLink?> {
            require(linkIn != null || linkOut != null) { "A switch node must have at least one switch" }
            return when {
                linkIn == null -> requireNotNull(linkOut) to null
                linkOut == null -> linkIn to null
                linkIn.isLessThanOrEqual(linkOut) -> linkIn to linkOut
                else -> linkOut to linkIn
            }
        }
    }

    @get:JsonIgnore abstract val direction: EdgeNodeDirection
    @get:JsonIgnore abstract val node: LayoutNode?
    val type: LayoutNodeType
        get() = node?.type ?: LayoutNodeType.PLACEHOLDER

    val switchIn: SwitchLink?
        get() =
            when (direction) {
                INCREASING -> node?.switch1
                DECREASING -> node?.switch2
            }

    val switchOut: SwitchLink?
        get() =
            when (direction) {
                INCREASING -> node?.switch2
                DECREASING -> node?.switch1
            }

    val startingTrackId: IntId<LocationTrack>?
        get() = node?.startingTrackId

    val endingTrackId: IntId<LocationTrack>?
        get() = node?.endingTrackId

    fun containsJoint(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        node?.switch1?.matches(switchId, jointNumber) ?: false || node?.switch2?.matches(switchId, jointNumber) ?: false

    @get:JsonIgnore val contentHash: Int by lazy { Objects.hash(direction, node?.contentHash) }

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): EdgeNode =
        if (node?.switch1?.id == switchId || node?.switch2?.id == switchId) {
            val newSwitch1 = node?.switch1?.takeIf { it.id != switchId }
            val newSwitch2 = node?.switch2?.takeIf { it.id != switchId }
            if (newSwitch1 != null || newSwitch2 != null) {
                TmpEdgeNode(direction, TmpSwitchNode(requireNotNull(newSwitch1 ?: newSwitch2), null))
            } else {
                placeHolder
            }
        } else {
            this
        }
    //    fun withoutSwitch(switchId: IntId<LayoutSwitch>): EdgeNode? {
    //        val newNode = node?.withoutSwitch()
    //    }
}

data class DbEdgeNode(override val direction: EdgeNodeDirection, override val node: DbLayoutNode) : EdgeNode() {
    val id: IntId<LayoutNode>
        get() = node.id

    init {
        require(node.type != LayoutNodeType.PLACEHOLDER) { "DbEdgeNode must have a real node" }
    }
}

data class TmpEdgeNode(override val direction: EdgeNodeDirection, override val node: LayoutNode) : EdgeNode() {}

data class PlaceHolderEdgeNode(
    override val direction: EdgeNodeDirection = INCREASING,
    override val node: LayoutNode? = null,
) : EdgeNode()

enum class LayoutNodeType {
    SWITCH,
    TRACK_START,
    TRACK_END,
    PLACEHOLDER,
}

// enum class TrackEndType {
//    START,
//    END,
// }

sealed class LayoutNode {
    open val switch1: SwitchLink? = null
    open val switch2: SwitchLink? = null
    open val startingTrackId: IntId<LocationTrack>? = null
    open val endingTrackId: IntId<LocationTrack>? = null

    abstract val type: LayoutNodeType

    @get:JsonIgnore val contentHash: Int by lazy { Objects.hash(switch1, switch2, startingTrackId, endingTrackId) }
}

sealed class DbLayoutNode : LayoutNode() {
    abstract val id: IntId<LayoutNode>
}

sealed class TmpLayoutNode : LayoutNode()

data class DbSwitchNode(
    override val id: IntId<LayoutNode>,
    override val switch1: SwitchLink,
    override val switch2: SwitchLink?,
) : DbLayoutNode() {
    override val type: LayoutNodeType = SWITCH
}

data class TmpSwitchNode(override val switch1: SwitchLink, override val switch2: SwitchLink?) : TmpLayoutNode() {
    override val type: LayoutNodeType = SWITCH
}

data class DbTrackStartNode(override val id: IntId<LayoutNode>, override val startingTrackId: IntId<LocationTrack>) :
    DbLayoutNode() {
    override val type: LayoutNodeType = TRACK_START
}

data class DbTrackEndNode(override val id: IntId<LayoutNode>, override val endingTrackId: IntId<LocationTrack>) :
    DbLayoutNode() {
    override val type: LayoutNodeType = TRACK_END
}

data class TmpTrackStartNode(override val startingTrackId: IntId<LocationTrack>) : TmpLayoutNode() {
    override val type: LayoutNodeType = TRACK_START
}

data class TmpTrackEndNode(override val endingTrackId: IntId<LocationTrack>) : TmpLayoutNode() {
    override val type: LayoutNodeType = TRACK_END
}

// data class DbLayoutNode(val id: IntId<DbLayoutNode>, @JsonIgnore val content: LocationTrackNode) :
//    ITrackNodeContent by content {
//    init {
//        require(content !is LayoutNodeTemp) { "Temp nodes are not allowed in DB" }
//    }
// }

// interface ITrackNodeContent {}
//
// sealed class LocationTrackNode : ITrackNodeContent {
//    @get:JsonIgnore
//    override val contentHash: Int by lazy { Objects.hash(switchIn, switchOut, startingTrackId, endingTrackId) }
// }
//
// data class LayoutNodeTemp(override val nodeType: LayoutNodeType) : LocationTrackNode()
//
// data class LocationTrackStartNode(override val startingTrackId: IntId<LocationTrack>) : LocationTrackNode() {
//    override val nodeType: LayoutNodeType = TRACK_START
// }
//
// data class LocationTrackEndNode(override val endingTrackId: IntId<LocationTrack>) : LocationTrackNode() {
//    override val nodeType: LayoutNodeType = TRACK_END
// }
//
// data class LayoutNodeSwitch(override val switchIn: SwitchLink?, override val switchOut: SwitchLink?) :
//    LocationTrackNode() {
//    override val nodeType: LayoutNodeType = SWITCH
//
//    init {
//        require(switchIn != null || switchOut != null) { "A switch node must have at least one switch" }
//    }
// }

data class SwitchLink(val id: IntId<LayoutSwitch>, val jointRole: SwitchJointRole, val jointNumber: JointNumber) {
    fun matches(switchId: IntId<LayoutSwitch>, switchJointNumber: JointNumber) =
        id == switchId && jointNumber == switchJointNumber

    fun isLessThanOrEqual(other: SwitchLink) =
        id.intValue < other.id.intValue ||
            (id.intValue == other.id.intValue && jointNumber.intValue <= other.jointNumber.intValue)
}

/**
 * Combine edges from different sources into a single geometry:
 * - Between edges, both edges are linked to the same switch, if any
 * - Edges without a switch between them are combined into a single edge
 * - If edges point to having different switches between them, an exception is thrown
 */
fun combineEdges(edges: List<LayoutEdge>): List<LayoutEdge> {
    if (edges.isEmpty()) return edges
    val combined = mutableListOf<LayoutEdge>()
    var previous: LayoutEdge? = null
    for (next in edges) {
        if (previous == null) previous = next
        // Both edges agree on the node between them -> move on
        else if (previous.endNode.type == SWITCH && next.startNode.type == SWITCH) {
            require(next.startNode == previous.endNode) {
                "Cannot link edges with different switches: ${previous?.endNode} -> ${next.startNode}"
            }
            combined.add(previous)
            previous = next
        }
        // Previous has a switch node -> mark the next one to start from that node as well
        else if (previous.endNode.type == SWITCH) {
            combined.add(previous)
            previous = next.withStartNode(previous.endNode)
        }
        // Next has a switch node -> mark the previous one to end at that node as well
        else if (next.startNode.type == SWITCH) {
            combined.add(previous.withEndNode(next.startNode))
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
