package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
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

    @get:JsonIgnore abstract val edges: List<ILayoutEdge>
    @get:JsonIgnore val edgeMs: List<Range<Double>> by lazy { calculateEdgeMs(edges) }
    @get:JsonIgnore override val segments: List<LayoutSegment> by lazy { edges.flatMap(ILayoutEdge::segments) }
    override val segmentMValues: List<Range<Double>> by lazy { calculateSegmentMValues(segments) }
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(edges.mapNotNull(ILayoutEdge::boundingBox)) }

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
    open val edgesWithM: List<Pair<ILayoutEdge, Range<Double>>>
        get() = edges.zip(edgeMs)

    // TODO: GVT-1727 Use streams instead of lists here?

    @get:JsonIgnore
    open val nodes: List<ILayoutNodeContent> by lazy {
        // Init-block ensures that edges are connected: previous edge end node is the next edge start node
        edges.flatMapIndexed { i, e ->
            if (i == edges.lastIndex) listOf(e.startNode, e.endNode) else listOf(e.startNode)
        }
    }

    @get:JsonIgnore
    open val nodesWithLocation: List<Pair<ILayoutNodeContent, AlignmentPoint>> by lazy {
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
    open val startNode: ILayoutNodeContent?
        get() = edges.firstOrNull()?.startNode

    @get:JsonIgnore
    open val endNode: ILayoutNodeContent?
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
            .flatMapIndexed { index, (node, location) ->
                when (node) {
                    is LayoutNodeSwitch -> {
                        val switchIn = node.switchIn?.let { TrackSwitchLink(it.id, it.jointNumber, location) }
                        val switchOut = node.switchOut?.let { TrackSwitchLink(it.id, it.jointNumber, location) }
                        listOfNotNull(switchIn, switchOut)
                    }
                    else -> emptyList()
                }
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

fun calculateEdgeMs(edges: List<ILayoutEdge>): List<Range<Double>> {
    var previousEnd = 0.0
    return edges.map { edge -> Range(previousEnd, previousEnd + edge.length).also { previousEnd += edge.length } }
}

data class TmpLocationTrackGeometry(@get:JsonIgnore override val edges: List<ILayoutEdge>) : LocationTrackGeometry() {
    init {
        edges.firstOrNull()?.startNode?.let { node ->
            require(node.nodeType != TRACK_END) { "First node must not be a a track end: node=$node" }
        }
        edges.lastOrNull()?.endNode?.let { node ->
            require(node.nodeType != TRACK_START) { "First node must not be a a track end: node=$node" }
        }
        edges.zipWithNext().forEach { (prev, next) ->
            require(prev.endNode.contentHash == next.startNode.contentHash) {
                "Edges should be connected: prev=${prev.endNode} next=${next.startNode}"
            }
            require(prev.endNode.nodeType == LayoutNodeType.SWITCH) {
                "Only switch nodes are allowed in the middle of the track: node=${prev.endNode}"
            }
            require(next.startNode.nodeType == LayoutNodeType.SWITCH) {
                "Only switch nodes are allowed in the middle of the track: node=${next.endNode}"
            }
        }
    }

    override fun withLocationTrackId(id: IntId<LocationTrack>): TmpLocationTrackGeometry {
        val newEdges = edges.map { it.setNodeTrackId(id) }
        return if (newEdges == edges) this else return TmpLocationTrackGeometry(newEdges)
    }
}

data class DbLocationTrackGeometry(
    @get:JsonIgnore val trackRowVersion: LayoutRowVersion<LocationTrack>,
    @get:JsonIgnore override val edges: List<LayoutEdge>,
) : LocationTrackGeometry() {
    init {
        edges.firstOrNull()?.startNode?.let { node ->
            require(node.nodeType != TRACK_END) { "First node must not be a a track end: node=$node" }
        }
        edges.lastOrNull()?.endNode?.let { node ->
            require(node.nodeType != TRACK_START) { "First node must not be a a track end: node=$node" }
        }
        edges.zipWithNext().forEach { (prev, next) ->
            require(prev.endNode.contentHash == next.startNode.contentHash) {
                "Edges should be connected: prev=${prev.endNode} next=${next.startNode}"
            }
            require(prev.endNode.nodeType == LayoutNodeType.SWITCH) {
                "Only switch nodes are allowed in the middle of the track: node=${prev.endNode}"
            }
            require(next.startNode.nodeType == LayoutNodeType.SWITCH) {
                "Only switch nodes are allowed in the middle of the track: node=${next.endNode}"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override val edgesWithM: List<Pair<LayoutEdge, Range<Double>>>
        get() = super.edgesWithM as List<Pair<LayoutEdge, Range<Double>>>

    @Suppress("UNCHECKED_CAST")
    override val nodes: List<LayoutNode>
        get() = super.nodes as List<LayoutNode>

    @Suppress("UNCHECKED_CAST")
    override val nodesWithLocation: List<Pair<LayoutNode, AlignmentPoint>>
        get() = super.nodesWithLocation as List<Pair<LayoutNode, AlignmentPoint>>

    override val startNode: LayoutNode?
        get() = super.startNode as? LayoutNode?

    override val endNode: LayoutNode?
        get() = super.endNode as? LayoutNode?

    override fun withLocationTrackId(id: IntId<LocationTrack>): LocationTrackGeometry {
        return if (trackRowVersion.id == id) this else TmpLocationTrackGeometry(edges.map { it.setNodeTrackId(id) })
    }
}

interface ILayoutEdge : IAlignment {
    val startNode: ILayoutNodeContent
    val endNode: ILayoutNodeContent
    @get:JsonIgnore override val segments: List<LayoutSegment>

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

    override val boundingBox: BoundingBox

    @get:JsonIgnore val contentHash: Int

    fun withStartNode(node: ILayoutNodeContent) = LayoutEdgeContent(node, endNode, segments)

    fun withEndNode(node: ILayoutNodeContent) = LayoutEdgeContent(endNode, node, segments)

    fun withoutSwitch(switchId: IntId<LayoutSwitch>, trackId: IntId<LocationTrack>): ILayoutEdge {
        val start = startNode.withoutSwitch(switchId) ?: LayoutNodeStartTrack(trackId)
        val end = endNode.withoutSwitch(switchId) ?: LayoutNodeStartTrack(trackId)
        return this.takeIf { startNode == start && endNode == end } ?: LayoutEdgeContent(start, end, segments)
    }

    fun setNodeTrackId(id: IntId<LocationTrack>): ILayoutEdge {
        val newStart = startNode.withTrackId(id)
        val newEnd = endNode.withTrackId(id)
        return if (newStart == startNode && newEnd == endNode) this else LayoutEdgeContent(newStart, newEnd, segments)
    }
}

data class LayoutEdgeContent(
    override val startNode: ILayoutNodeContent,
    override val endNode: ILayoutNodeContent,
    @get:JsonIgnore override val segments: List<LayoutSegment>,
) : ILayoutEdge {
    override val segmentMValues: List<Range<Double>> = calculateSegmentMValues(segments)

    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<Double>>>
        get() = segments.zip(segmentMValues)

    init {
        // TODO: GVT-2934 fix the data and re-enable this
        // Our base data is broken so that there's bad edges like this. It's the same in original segments as well.
        //        require(startNodeId != endNodeId) { "Start and end node must be different: start=$startNodeId
        // end=$endNodeId" }
        require(segments.isNotEmpty()) { "LayoutEdge must have at least one segment" }
        segmentMValues.forEach { range ->
            require(range.min.isFinite() && range.min >= 0.0) { "Invalid start m: ${range.min}" }
            require(range.max.isFinite() && range.max >= range.min) { "Invalid end m: ${range.max}" }
        }
        segmentMValues.zipWithNext().map { (prev, next) ->
            require(abs(prev.max - next.min) < 0.001) {
                "Edge segment m-values should be continuous: prev=$prev next=$next"
            }
        }
        segments.zipWithNext().map { (prev, next) ->
            require(prev.segmentEnd.isSame(next.segmentStart, 0.001)) {
                "Edge segments should begin where the previous one ends: prev=${prev.segmentEnd} next=${next.segmentStart}"
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

    override val boundingBox: BoundingBox by lazy {
        requireNotNull(boundingBoxCombining(segments.mapNotNull(LayoutSegment::boundingBox))) {
            "An edge must have segments, so it must have a bounding box"
        }
    }
    @get:JsonIgnore
    override val contentHash: Int by lazy { Objects.hash(startNode.contentHash, endNode.contentHash, segments) }
}

data class LayoutEdge(val id: IntId<LayoutEdge>, @JsonIgnore val content: LayoutEdgeContent) : ILayoutEdge by content {
    init {
        require(content.startNode is LayoutNode) { "An edge in DB must have a start node in DB (must have an ID)" }
        require(content.endNode is LayoutNode) { "An edge in DB must have an end node in DB (must have an ID)" }
    }

    override val startNode: LayoutNode
        get() = content.startNode as LayoutNode

    override val endNode: LayoutNode
        get() = content.endNode as LayoutNode

    override val boundingBox: BoundingBox by lazy {
        requireNotNull(boundingBoxCombining(segments.mapNotNull(LayoutSegment::boundingBox))) {
            "An edge must have segments, so it must have a bounding box"
        }
    }
}

enum class LayoutNodeType {
    SWITCH,
    TRACK_START,
    TRACK_END,
}

data class LayoutNode(val id: IntId<LayoutNode>, @JsonIgnore val content: LayoutNodeContent) :
    ILayoutNodeContent by content {
    init {
        require(content !is LayoutNodeTemp) { "Temp nodes are not allowed in DB" }
    }
}

interface ILayoutNodeContent {
    val switchIn: SwitchLink?
        get() = null

    val switchOut: SwitchLink?
        get() = null

    val startingTrackId: IntId<LocationTrack>?
        get() = null

    val endingTrack: IntId<LocationTrack>?
        get() = null

    val nodeType: LayoutNodeType

    @get:JsonIgnore val contentHash: Int

    fun containsJoint(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        switchIn?.matches(switchId, jointNumber) ?: false || switchOut?.matches(switchId, jointNumber) ?: false

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): ILayoutNodeContent? =
        if (switchIn?.id == switchId || switchOut?.id == switchId) {
            val switchIn = switchIn?.takeIf { it.id != switchId }
            val switchOut = switchOut?.takeIf { it.id != switchId }
            if (switchIn != null || switchOut != null) {
                LayoutNodeSwitch(switchIn, switchOut)
            } else {
                null
            }
        } else {
            this
        }

    fun withTrackId(id: IntId<LocationTrack>): ILayoutNodeContent =
        if (this is LayoutNodeTemp) {
            when (nodeType) {
                TRACK_START -> LayoutNodeStartTrack(id)
                TRACK_END -> LayoutNodeEndTrack(id)
                else -> error("Cannot set track ID for a temporary node: $this")
            }
        } else {
            this
        }
}

sealed class LayoutNodeContent : ILayoutNodeContent

data class LayoutNodeTemp(override val nodeType: LayoutNodeType) : LayoutNodeContent() {
    @get:JsonIgnore override val contentHash: Int by lazy { error("Node content is not defined: type=$nodeType") }
}

data class LayoutNodeStartTrack(override val startingTrackId: IntId<LocationTrack>) : LayoutNodeContent() {
    override val nodeType: LayoutNodeType = TRACK_START
    @get:JsonIgnore override val contentHash: Int by lazy { hashCode() }
}

data class LayoutNodeEndTrack(override val endingTrack: IntId<LocationTrack>) : LayoutNodeContent() {
    override val nodeType: LayoutNodeType = TRACK_END
    @get:JsonIgnore override val contentHash: Int by lazy { hashCode() }
}

data class LayoutNodeSwitch(override val switchIn: SwitchLink?, override val switchOut: SwitchLink?) :
    LayoutNodeContent() {
    override val nodeType: LayoutNodeType = LayoutNodeType.SWITCH
    @get:JsonIgnore override val contentHash: Int by lazy { hashCode() }

    init {
        require(switchIn != null || switchOut != null) { "A switch node must have at least one switch" }
    }
}

data class SwitchLink(val id: IntId<LayoutSwitch>, val jointRole: SwitchJointRole, val jointNumber: JointNumber) {
    fun matches(switchId: IntId<LayoutSwitch>, switchJointNumber: JointNumber) =
        id == switchId && jointNumber == switchJointNumber
}

/**
 * Combine edges from different sources into a single geometry:
 * - Between edges, both edges are linked to the same switch, if any
 * - Edges without a switch between them are combined into a single edge
 * - If edges point to having different switches between them, an exception is thrown
 */
fun combineEdges(edges: List<ILayoutEdge>): List<ILayoutEdge> {
    if (edges.isEmpty()) return edges
    val combined = mutableListOf<ILayoutEdge>()
    var previous: ILayoutEdge? = null
    for (next in edges) {
        if (previous == null) previous = next
        // Both edges agree on the node between them -> move on
        else if (previous.endNode is LayoutNodeSwitch && next.startNode is LayoutNodeSwitch) {
            require(next.startNode == previous.endNode) {
                "Cannot link edges with different switches: ${previous?.endNode} -> ${next.startNode}"
            }
            combined.add(previous)
            previous = next
        }
        // Previous has a switch node -> mark the next one to start from that node as well
        else if (previous.endNode is LayoutNodeSwitch) {
            combined.add(previous)
            previous = next.withStartNode(previous.endNode)
        }
        // Next has a switch node -> mark the previous one to end at that node as well
        else if (next.startNode is LayoutNodeSwitch) {
            combined.add(previous.withEndNode(next.startNode))
            previous = next
        }
        // Neither has a switch node -> combine them into a single edge
        else {
            previous =
                LayoutEdgeContent(
                    startNode = previous.startNode,
                    endNode = next.endNode,
                    segments = previous.segments + next.segments,
                )
        }
    }
    previous?.let(combined::add)
    return combined.toList()
}
