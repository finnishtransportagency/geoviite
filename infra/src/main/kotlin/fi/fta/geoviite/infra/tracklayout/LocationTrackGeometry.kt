package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.linking.splitSegments
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
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLinkType.INNER
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLinkType.OUTER
import java.util.*
import kotlin.math.abs

enum class TrackSwitchLinkType {
    INNER,
    OUTER,
}

data class TrackSwitchLink(val link: SwitchLink, val location: AlignmentPoint, val type: TrackSwitchLinkType) {
    val switchId: IntId<LayoutSwitch>
        get() = link.id

    val jointNumber: JointNumber
        get() = link.jointNumber

    val jointRole: SwitchJointRole
        get() = link.jointRole
}

sealed class LocationTrackGeometry : IAlignment {
    companion object {
        val empty = TmpLocationTrackGeometry(emptyList())
    }

    @get:JsonIgnore abstract val edges: List<LayoutEdge>
    @get:JsonIgnore val edgeMs: List<Range<Double>> by lazy { calculateEdgeMValues(edges) }
    @get:JsonIgnore override val segments: List<LayoutSegment> by lazy { edges.flatMap(LayoutEdge::segments) }
    override val segmentMValues: List<Range<Double>> by lazy { calculateSegmentMValues(segments) }
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(edges.mapNotNull(LayoutEdge::boundingBox)) }

    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<Double>>>
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
    open val edgesWithM: List<Pair<LayoutEdge, Range<Double>>>
        get() = edges.zip(edgeMs)

    @get:JsonIgnore
    open val nodes: List<LayoutNode> by lazy {
        // Init-block ensures that edges are connected: previous edge end is the next edge start
        edges.flatMapIndexed { i, e ->
            if (i == edges.lastIndex) listOf(e.startNode.node, e.endNode.node) else listOf(e.startNode.node)
        }
    }

    @get:JsonIgnore
    open val nodesWithLocation: List<Pair<LayoutNode, AlignmentPoint>> by lazy {
        edgesWithM.flatMapIndexed { i, (e, m) ->
            // Init-block ensures that edges are connected: previous edge end is the next edge start
            if (i == edges.lastIndex) {
                listOf(
                    e.startNode.node to e.firstSegmentStart.toAlignmentPoint(m.min),
                    e.endNode.node to e.lastSegmentEnd.toAlignmentPoint(m.min + e.segmentMValues.last().min),
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
                    val end = e.lastSegmentEnd.toAlignmentPoint(m.min + e.segmentMValues.last().min)
                    listOfNotNull(
                        e.endNode.switchIn?.let { TrackSwitchLink(it, end, INNER) },
                        e.endNode.switchOut?.let { TrackSwitchLink(it, end, OUTER) },
                    )
                } else emptyList()
            startSwitches + endSwitches
        }
    }

    fun getSwitchLocation(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        trackSwitchLinks.firstOrNull { tsl -> tsl.link.matches(switchId, jointNumber) }?.location

    fun getSwitchLocations(switchId: IntId<LayoutSwitch>) =
        trackSwitchLinks.filter { tsl -> tsl.link.id == switchId }.map { tsl -> tsl.link to tsl.location }

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
    private fun pickPrimaryEndJoint(edgeNode: EdgeNode): SwitchLink? =
        edgeNode.switchIn?.takeIf { j -> j.jointRole == SwitchJointRole.MAIN }
            ?: edgeNode.switchOut?.takeIf { j -> j.jointRole == SwitchJointRole.MAIN }
            ?: edgeNode.switchIn
            ?: edgeNode.switchOut

    fun containsSwitch(switchId: IntId<LayoutSwitch>): Boolean = switchIds.contains(switchId)

    abstract fun withLocationTrackId(id: IntId<LocationTrack>): LocationTrackGeometry

    fun getEdgeStartAndEnd(edgeIndices: IntRange): Pair<AlignmentPoint, AlignmentPoint> {
        require(edgeIndices.first >= 0 && edgeIndices.last <= edges.lastIndex) {
            "Edge indices out of bounds: first=${edgeIndices.first} last=${edgeIndices.last} edges=${edges.size}"
        }
        val start =
            edgesWithM[edgeIndices.first].let { (edge, edgeM) -> edge.firstSegmentStart.toAlignmentPoint(edgeM.min) }
        val end =
            edgesWithM[edgeIndices.last].let { (edge, edgeM) ->
                val (segment, segmentM) = edge.segmentsWithM.last()
                segment.segmentEnd.toAlignmentPoint(edgeM.min + segmentM.min)
            }
        return start to end
    }

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): LocationTrackGeometry {
        val newEdges = edges.map { e -> e.withoutSwitch(switchId) }
        return this.takeIf { newEdges == edges } ?: TmpLocationTrackGeometry(combineEdges(newEdges))
    }

    fun withNodeReplacements(nodeSwaps: Map<NodeHash, LayoutNode>): LocationTrackGeometry =
        this.takeIf { nodes.none { nodeSwaps.containsKey(it.contentHash) } }
            ?: TmpLocationTrackGeometry(processNodeReplacements(nodeSwaps, edges))

    fun getEdgeAtMOrThrow(m: Double): Pair<LayoutEdge, Range<Double>> {
        return requireNotNull(getEdgeAtM(m)) { "Geometry does not contain edge at m $m" }
    }

    fun getEdgeAtM(m: Double): LayoutEdge? =
        edgeMs
            .binarySearch { mRange ->
                when {
                    m < mRange.min -> -1
                    m > mRange.max -> 1
                    else -> 0
                }
            }
            .takeIf { it >= 0 }
            ?.let(edges::getOrNull)

    fun mergeEdges(edgesToMerge: List<LayoutEdge>): LocationTrackGeometry {
        val newEdges =
            edges
                .fold(listOf<LayoutEdge>() to listOf<LayoutEdge>()) { (collectedEdges, collectedToMerge), edge ->
                    if (!edgesToMerge.contains(edge) || edge == edges.last()) {
                        // merge multiple into one
                        val toMerge = collectedToMerge + edge
                        val newSegments = toMerge.flatMap { edgeToMerge -> edgeToMerge.segments }
                        val newEdge = TmpLayoutEdge(toMerge.first().startNode, toMerge.last().endNode, newSegments)
                        collectedEdges + newEdge to listOf()
                    } else if (edgesToMerge.contains(edge)) {
                        collectedEdges to collectedToMerge + edge
                    } else collectedEdges + edge to collectedToMerge
                }
                .first
        return TmpLocationTrackGeometry(combineEdges(newEdges))
    }
}

fun calculateEdgeMValues(edges: List<LayoutEdge>): List<Range<Double>> {
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
        val newEdges = edges.map { it.withLocationTrackId(id) }
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

    @Suppress("UNCHECKED_CAST")
    override val edgesWithM: List<Pair<DbLayoutEdge, Range<Double>>>
        get() = super.edgesWithM as List<Pair<DbLayoutEdge, Range<Double>>>

    @Suppress("UNCHECKED_CAST")
    override val nodes: List<DbLayoutNode>
        get() = super.nodes as List<DbLayoutNode>

    @Suppress("UNCHECKED_CAST")
    override val nodesWithLocation: List<Pair<DbLayoutNode, AlignmentPoint>>
        get() = super.nodesWithLocation as List<Pair<DbLayoutNode, AlignmentPoint>>

    override val startNode: DbEdgeNode?
        get() = edges.firstOrNull()?.startNode

    override val endNode: DbEdgeNode?
        get() = edges.lastOrNull()?.endNode

    override fun withLocationTrackId(id: IntId<LocationTrack>): LocationTrackGeometry =
        this.takeIf { trackRowVersion.id == id } ?: TmpLocationTrackGeometry(edges.map { it.withLocationTrackId(id) })
}

data class EdgeHash private constructor(val value: Int) {
    companion object {
        fun of(start: EdgeNode, end: EdgeNode, segments: List<LayoutSegment>): EdgeHash =
            EdgeHash(Objects.hash(edgeNodeHash(start), edgeNodeHash(end), segmentsHash(segments)))

        private fun edgeNodeHash(edgeNode: EdgeNode): Int =
            Objects.hash(edgeNode.portConnection, edgeNode.node.contentHash)

        private fun segmentsHash(segments: List<LayoutSegment>): Int = Objects.hash(segments.map(::segmentHash))

        // Note: the segment hash isn't similarly stable by content as node hash is:
        // this will change after save & read
        private fun segmentHash(segment: LayoutSegment): Int = segment.hashCode()
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
    @get:JsonIgnore val contentHash: EdgeHash by lazy { EdgeHash.of(startNode, endNode, segments) }

    fun withSegments(newSegments: List<LayoutSegment>) = TmpLayoutEdge(startNode, endNode, newSegments)

    fun withStartNode(newStartNode: EdgeNode) = TmpLayoutEdge(newStartNode, endNode, segments)

    fun withStartNode(newStartNode: LayoutNode) = withStartNode(reconnectEdgeNode(startNode, newStartNode))

    fun withEndNode(newEndNode: EdgeNode) = TmpLayoutEdge(startNode, newEndNode, segments)

    fun withNodes(newStartNode: LayoutNode, newEndNode: LayoutNode) =
        TmpLayoutEdge(
            startNode = reconnectEdgeNode(startNode, newStartNode),
            endNode = reconnectEdgeNode(endNode, newEndNode),
            segments = segments,
        )

    fun withEndNode(newEndNode: LayoutNode) = withEndNode(reconnectEdgeNode(endNode, newEndNode))

    fun withoutSwitch(switchId: IntId<LayoutSwitch>): LayoutEdge {
        val start = startNode.withoutSwitch(switchId)
        val end = endNode.withoutSwitch(switchId)
        return this.takeIf { startNode == start && endNode == end } ?: TmpLayoutEdge(start, end, segments)
    }

    fun withLocationTrackId(id: IntId<LocationTrack>): LayoutEdge {
        val newStart = startNode.takeIf { n -> n.type != TRACK_BOUNDARY } ?: startNode.withInnerBoundary(id, START)
        val newEnd = endNode.takeIf { n -> n.type != TRACK_BOUNDARY } ?: endNode.withInnerBoundary(id, END)
        return if (newStart == startNode && newEnd == endNode) this else TmpLayoutEdge(newStart, newEnd, segments)
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
        // The replacement may result in the current edge getting merged with the next or previous one
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
                    startNode = reconnectEdgeNode(next.startNode, replacementNode),
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
                    endNode = reconnectEdgeNode(previous.endNode, replacementNode),
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
                    endNode = reconnectEdgeNode(previous.endNode, replacementNode),
                    segments = previous.segments + preSegments,
                ),
                null,
                TmpLayoutEdge(
                    startNode = reconnectEdgeNode(next.startNode, replacementNode),
                    endNode = next.endNode,
                    segments = postSegments + next.segments,
                ),
            )
        }
    }

private fun reconnectEdgeNode(currentEdgeNode: EdgeNode, newNode: LayoutNode): TmpEdgeNode =
    when {
        currentEdgeNode.type == TRACK_BOUNDARY && newNode.type == SWITCH ->
            TmpEdgeNode(B, newNode).also { require(newNode.portB == null) }

        newNode.portA == currentEdgeNode.innerPort -> TmpEdgeNode(A, newNode)
        newNode.portB == currentEdgeNode.innerPort -> TmpEdgeNode(B, newNode)
        // The connection port doesn't exist on the new node -> cannot reconnect
        // If the outer ports match, we could connect to outer.reversed, but that would be wrong:
        // one side of the edge would be inner-switch while the other side is not
        else -> error("Unable to replace edge node: current=$currentEdgeNode new=$newNode")
    }

fun verifyEdgeContent(edge: LayoutEdge) {
    // TODO: GVT-2934 fix the data and re-enable this
    // Our base data is broken so that there's bad edges like this. It's the same in original
    // segments as well.
    //        require(startNodeId != endNodeId) { "Start and end node must be different:
    // start=$startNodeId
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
    //            startNode.switchOut == null || endNode.switchIn == null || startNode.switchOut?.id
    // ==
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

    fun containsSwitch(switchId: IntId<LayoutSwitch>) = node.containsSwitch(switchId)

    fun containsJoint(switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
        node.containsJoint(switchId, jointNumber)

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

    fun containsInnerSwitch(switchId: IntId<LayoutSwitch>): Boolean =
        portA.let { port -> (port as? SwitchLink)?.id == switchId }

    fun containsInnerJoint(switchId: IntId<LayoutSwitch>, joint: JointNumber): Boolean =
        portA.let { port -> (port as? SwitchLink)?.matches(switchId, joint) ?: false }

    fun containsOuterJoint(switchId: IntId<LayoutSwitch>, joint: JointNumber): Boolean =
        portB.let { port -> (port as? SwitchLink)?.matches(switchId, joint) ?: false }

    abstract val type: LayoutNodeType

    companion object {
        fun of(link1: SwitchLink, link2: SwitchLink? = null): LayoutNode =
            inNodeOrder(link1, link2).let { (portA, portB) -> TmpSwitchNode(portA, portB) }

        fun of(link1: TrackBoundary, link2: TrackBoundary? = null) =
            inNodeOrder(link1, link2).let { (portA, portB) -> TmpTrackBoundaryNode(portA, portB) }
    }

    @get:JsonIgnore val contentHash: NodeHash by lazy { NodeHash.of(portA, portB) }
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
                val endingNode = EdgeNode.switch(inner = previous.endNode.switchIn, outer = next.startNode.switchIn)
                val startingNode = EdgeNode.switch(inner = next.startNode.switchIn, outer = previous.endNode.switchIn)
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
    require(portA.id != portB?.id) {
        "Switch node cannot have two connections to the same switch (1 joint of a switch in 1 location): portA=$portA portB=$portB"
    }
    //        require(portA.id != portB?.id || portA.jointNumber != portB.jointNumber) {
    //    "Switch node cannot have two identical ports (they should be the same single port):
    // portA=$portA portB=$portB"
    // }
}

fun verifyTrackBoundaryNode(portA: TrackBoundary, portB: TrackBoundary?) {
    require(portA.id != portB?.id) {
        "Track boundary node cannot connect twice to the same track: portA=$portA portB=$portB"
    }
}
