package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED

data class LocationTrackGeometry(val trackRowVersion: LayoutRowVersion<LocationTrack>, val edges: List<LayoutEdge>) :
    IAlignment {
    override val id: DomainId<LocationTrack> = StringId(trackRowVersion.toString())
    // TODO: GVT-1727 segment start value conversions?
    override val segments: List<LayoutEdgeSegment> by lazy { edges.flatMap(LayoutEdge::segments) }
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(edges.mapNotNull(LayoutEdge::boundingBox)) }
}

data class LocationTrackEdge(val startM: Double, val edge: LayoutEdge)

data class LayoutEdge(
    override val id: DomainId<LayoutEdge> = StringId(),
    val startNode: LayoutNode,
    val endNode: LayoutNode,
    override val segments: List<LayoutEdgeSegment>,
) : IAlignment {
    override val boundingBox: BoundingBox? by lazy {
        boundingBoxCombining(segments.mapNotNull(LayoutEdgeSegment::boundingBox))
    }

    init {
        require(startNode.id != endNode.id) { "Start and end node must be different" }
        require(segments.isNotEmpty()) { "LayoutEdge must have at least one segment" }
    }
}

data class LayoutEdgeSegment(
    @JsonIgnore override val geometry: SegmentGeometry,
    override val sourceId: IndexedId<GeometryElement>?,
    override val sourceStart: Double?,
    override val startM: Double,
    override val source: GeometrySource,
    override val id: DomainId<LayoutEdgeSegment> = deriveFromSourceId("AS", sourceId),
) : ISegmentGeometry by geometry, ISegment {
    init {
        require(source != GENERATED || segmentPoints.size == 2) { "Generated segment can't have more than 2 points" }
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
}

data class LayoutNodeStartTrack(override val startingTrackId: IntId<LocationTrack>) : LayoutNodeContent {
    override val nodeType: NodeType = NodeType.TRACK_START
}

data class LayoutNodeEndTrack(override val endingTrack: IntId<LocationTrack>) : LayoutNodeContent {
    override val nodeType: NodeType = NodeType.TRACK_END
}

data class LayoutNodeSwitches(override val switches: List<SwitchLink>) : LayoutNodeContent {
    override val nodeType: NodeType = NodeType.SWITCH
}

data class SwitchLink(val id: IntId<TrackLayoutSwitch>, val jointNumber: JointNumber)
