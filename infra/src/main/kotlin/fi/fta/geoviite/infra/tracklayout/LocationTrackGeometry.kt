package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import java.util.*
import kotlin.math.abs

sealed class LocationTrackGeometry : IAlignment {
    abstract val edges: List<ILayoutEdge>
    val edgeMs: List<Range<Double>> by lazy { calculateEdgeMs(edges) }
    override val segments: List<LayoutEdgeSegment> by lazy { edges.flatMap(ILayoutEdge::segments) }
    override val segmentMs: List<Range<Double>> by lazy { calculateSegmentMs(segments) }
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(edges.mapNotNull(ILayoutEdge::boundingBox)) }

    override val segmentsWithM: List<Pair<LayoutEdgeSegment, Range<Double>>>
        get() = segments.zip(segmentMs)

    // All edges have segments, all segments have points
    val isEmpty: Boolean
        get() = edges.isEmpty()

    val isNotEmpty: Boolean
        get() = edges.isNotEmpty()

    val nodes: List<ILayoutNodeContent> by lazy {
        edges.flatMap { e -> listOf(e.startNode, e.endNode) }.distinctBy { n -> n.contentHash }
    }

    val nodesWithLocation: List<Pair<ILayoutNodeContent, AlignmentPoint>> by lazy {
        edgesWithM
            .flatMap { (e, m) ->
                listOf(
                    e.startNode to e.firstSegmentStart.toAlignmentPoint(m.min),
                    e.endNode to e.lastSegmentEnd.toAlignmentPoint(m.min + e.segmentMs.last().min),
                )
            }
            .distinctBy { (n, _) -> n.contentHash }
    }

    val edgesWithM: List<Pair<ILayoutEdge, Range<Double>>>
        get() = edges.zip(edgeMs)

    val switchIdAtStart: IntId<TrackLayoutSwitch>?
        get() = edges.firstOrNull()?.startNode?.switches?.lastOrNull()?.id

    val switchIdAtEnd: IntId<TrackLayoutSwitch>?
        get() = edges.lastOrNull()?.endNode?.switches?.firstOrNull()?.id
}

fun calculateEdgeMs(edges: List<ILayoutEdge>): List<Range<Double>> {
    var previousEnd = 0.0
    return edges.map { edge -> Range(previousEnd, previousEnd + edge.length).also { previousEnd += edge.length } }
}

data class TmpLocationTrackGeometry(override val edges: List<ILayoutEdge>) : LocationTrackGeometry()

data class DbLocationTrackGeometry(
    val trackRowVersion: LayoutRowVersion<LocationTrack>,
    override val edges: List<LayoutEdge>,
) : LocationTrackGeometry()

interface ILayoutEdge : IAlignment {
    val startNode: ILayoutNodeContent
    val endNode: ILayoutNodeContent
    override val segments: List<LayoutEdgeSegment>

    override val firstSegmentStart: SegmentPoint
        get() = segments.first().segmentStart

    override val lastSegmentEnd: SegmentPoint
        get() = segments.last().segmentEnd

    override val start: AlignmentPoint
        get() = firstSegmentStart.toAlignmentPoint(0.0) // alignmentStart

    override val end: AlignmentPoint
        get() = lastSegmentEnd.toAlignmentPoint(segmentMs.last().min)

    override val boundingBox: BoundingBox

    //    override val segmentMs: List<Range<Double>>
    val contentHash: Int
}

data class LayoutEdgeContent(
    override val startNode: ILayoutNodeContent,
    override val endNode: ILayoutNodeContent,
    override val segments: List<LayoutEdgeSegment>,
    override val segmentMs: List<Range<Double>>,
) : ILayoutEdge {

    override val segmentsWithM: List<Pair<LayoutEdgeSegment, Range<Double>>>
        get() = segments.zip(segmentMs)

    init {
        // TODO: GVT-1727 fix data?
        // Our base data is broken so that there's bad edges like this. It's the same in original segments as well.
        //        require(startNodeId != endNodeId) { "Start and end node must be different: start=$startNodeId
        // end=$endNodeId" }
        require(segments.isNotEmpty()) { "LayoutEdge must have at least one segment" }
        segmentMs.forEach { range ->
            require(range.min.isFinite() && range.min >= 0.0) { "Invalid start m: ${range.min}" }
            require(range.max.isFinite() && range.max >= range.min) { "Invalid end m: ${range.max}" }
        }
        segmentMs.zipWithNext().map { (prev, next) ->
            require(abs(prev.max - next.min) < 0.001) {
                "Edge segment m-values should be continuous: prev=$prev next=$next"
            }
        }
        segments.zipWithNext().map { (prev, next) ->
            require(prev.segmentEnd.isSame(next.segmentStart, 0.001)) {
                "Edge segments should begin where the previous one ends: prev=${prev.segmentEnd} next=${next.segmentStart}"
            }
        }
    }

    override val boundingBox: BoundingBox by lazy {
        requireNotNull(boundingBoxCombining(segments.mapNotNull(LayoutEdgeSegment::boundingBox))) {
            "An edge must have segments, so it must have a bounding box"
        }
    }
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
        requireNotNull(boundingBoxCombining(segments.mapNotNull(LayoutEdgeSegment::boundingBox))) {
            "An edge must have segments, so it must have a bounding box"
        }
    }
}

data class LayoutEdgeSegment(
    @JsonIgnore override val geometry: SegmentGeometry,
    override val sourceId: IndexedId<GeometryElement>?,
    // TODO: GVT-1727 these should be BigDecimals with a limited precision
    override val sourceStart: Double?,
    override val source: GeometrySource,
    // TODO: GVT-1727 do we need ids on edges?
    //    override val id: DomainId<LayoutEdgeSegment> = deriveFromSourceId("AS", sourceId),
) : ISegmentGeometry by geometry, ISegment {
    init {
        require(source != GENERATED || segmentPoints.size == 2) { "Generated segment can't have more than 2 points" }
        // These could be combined into a sub-object to enforce this via the type
        require((sourceId == null) == (sourceStart == null)) {
            "Source id and start must be either both null or both non-null"
        }
        require(sourceStart?.isFinite() != false) { "Invalid source start length: $sourceStart" }
    }
    // TODO: segment edit operations (mostly same as LayoutSegment)
}

enum class LayoutNodeType {
    SWITCH,
    TRACK_START,
    TRACK_END,
}

data class LayoutNode(val id: IntId<LayoutNode>, @JsonIgnore val content: LayoutNodeContent) :
    ILayoutNodeContent by content

interface ILayoutNodeContent {
    val switches: List<SwitchLink>
        get() = emptyList()

    val startingTrackId: IntId<LocationTrack>?
        get() = null

    val endingTrack: IntId<LocationTrack>?
        get() = null

    val nodeType: LayoutNodeType

    val contentHash: Int
}

sealed class LayoutNodeContent : ILayoutNodeContent

data class LayoutNodeStartTrack(override val startingTrackId: IntId<LocationTrack>) : LayoutNodeContent() {
    override val nodeType: LayoutNodeType = LayoutNodeType.TRACK_START
    override val contentHash: Int by lazy { hashCode() }
}

data class LayoutNodeEndTrack(override val endingTrack: IntId<LocationTrack>) : LayoutNodeContent() {
    override val nodeType: LayoutNodeType = LayoutNodeType.TRACK_END
    override val contentHash: Int by lazy { hashCode() }
}

data class LayoutNodeSwitches(override val switches: List<SwitchLink>) : LayoutNodeContent() {
    override val nodeType: LayoutNodeType = LayoutNodeType.SWITCH
    override val contentHash: Int by lazy { hashCode() }
}

data class SwitchLink(val id: IntId<TrackLayoutSwitch>, val jointNumber: JointNumber)
