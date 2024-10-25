package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import kotlin.math.abs

data class LocationTrackGeometry(val trackRowVersion: LayoutRowVersion<LocationTrack>, val edges: List<LayoutEdge>) :
    IAlignment {
    // TODO: Do we need an id like this? Can we just be rid of it? Should it be unique by version?
    override val id: IntId<LocationTrack> = trackRowVersion.id
    // TODO: GVT-1727 segment start value conversions?
    override val segments: List<LayoutEdgeSegment> by lazy { edges.flatMap(LayoutEdge::segments) }
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(edges.mapNotNull(LayoutEdge::boundingBox)) }
}

data class LocationTrackEdge(val startM: Double, @JsonIgnore private val edge: LayoutEdge) : IEdgeAlignment by edge {
    val endM: Double
        get() = startM + edge.length
}

interface IEdgeContent {
    val startNodeId: IntId<LayoutNode>
    val endNodeId: IntId<LayoutNode>
    val segments: List<LayoutEdgeSegment>
}

interface IEdgeAlignment : IEdgeContent, IAlignment

data class EdgeContent(
    override val startNodeId: IntId<LayoutNode>,
    override val endNodeId: IntId<LayoutNode>,
    override val segments: List<LayoutEdgeSegment>,
) : IEdgeContent {
    init {
        // TODO: GVT-1727 fix data?
        // Our base data is broken so that there's bad edges like this. It's the same in original segments as well.
        //        require(startNodeId != endNodeId) { "Start and end node must be different: start=$startNodeId
        // end=$endNodeId" }
        require(segments.isNotEmpty()) { "LayoutEdge must have at least one segment" }
        segments.zipWithNext().map { (prev, next) ->
            require(abs(prev.endM - next.startM) < 0.001) {
                "Edge segment m-values should be continuous: prev=${prev.endM} next=${next.startM}"
            }
            require(prev.segmentEnd.isSame(next.segmentStart, 0.001)) {
                "Edge segments should begin where the previous one ends: prev=${prev.segmentEnd} next=${next.segmentStart}"
            }
        }
    }

    val lazyHash: Int by lazy { hashCode() }
}

data class LayoutEdge(override val id: IntId<LayoutEdge>, @JsonIgnore val content: EdgeContent) :
    IEdgeContent by content, IEdgeAlignment {
    override val boundingBox: BoundingBox? by lazy {
        boundingBoxCombining(segments.mapNotNull(LayoutEdgeSegment::boundingBox))
    }
}

data class LayoutEdgeSegment(
    @JsonIgnore override val geometry: SegmentGeometry,
    override val sourceId: IndexedId<GeometryElement>?,
    // TODO: GVT-1727 these should be BigDecimals with a limited precision
    override val sourceStart: Double?,
    override val startM: Double,
    override val source: GeometrySource,
    override val id: DomainId<LayoutEdgeSegment> = deriveFromSourceId("AS", sourceId),
) : ISegmentGeometry by geometry, ISegment {
    init {
        require(source != GENERATED || segmentPoints.size == 2) { "Generated segment can't have more than 2 points" }
        // These could be combined into a sub-object to enforce this via the type
        require((sourceId == null) == (sourceStart == null)) {
            "Source id and start must be either both null or both non-null"
        }
        require(sourceStart?.isFinite() != false) { "Invalid source start length: $sourceStart" }
        require(startM.isFinite() && startM >= 0.0) { "Invalid start m: $startM" }
        require(endM.isFinite() && endM >= startM) { "Invalid end m: $endM" }
    }
    // TODO: segment edit operations (mostly same as LayoutSegment)
}

enum class NodeType {
    SWITCH,
    TRACK_START,
    TRACK_END,
}

data class LayoutNode(val id: IntId<LayoutNode>, @JsonIgnore val content: LayoutNodeContent) :
    LayoutNodeContent by content

interface LayoutNodeContent {
    val switches: List<SwitchLink>
        get() = emptyList()

    val startingTrackId: IntId<LocationTrack>?
        get() = null

    val endingTrack: IntId<LocationTrack>?
        get() = null

    val nodeType: NodeType

    val lazyHash: Int
}

data class LayoutNodeStartTrack(override val startingTrackId: IntId<LocationTrack>) : LayoutNodeContent {
    override val nodeType: NodeType = NodeType.TRACK_START
    override val lazyHash: Int by lazy { hashCode() }
}

data class LayoutNodeEndTrack(override val endingTrack: IntId<LocationTrack>) : LayoutNodeContent {
    override val nodeType: NodeType = NodeType.TRACK_END
    override val lazyHash: Int by lazy { hashCode() }
}

data class LayoutNodeSwitches(override val switches: List<SwitchLink>) : LayoutNodeContent {
    override val nodeType: NodeType = NodeType.SWITCH
    override val lazyHash: Int by lazy { hashCode() }
}

data class SwitchLink(val id: IntId<TrackLayoutSwitch>, val jointNumber: JointNumber)
