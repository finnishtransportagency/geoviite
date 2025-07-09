package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry

data class LinkedElement(
    private val edgeIndex: Int,
    private val segmentIndex: Int,
    val edge: LayoutEdge,
    val segment: LayoutSegment,
    val elementId: IndexedId<GeometryElement>?,
) {
    val idString: String
        get() = "${edgeIndex}_$segmentIndex"

    val alignmentId: IntId<GeometryAlignment>?
        get() = elementId?.parentId?.let(::IntId)
}

fun collectLinkedElements(
    geometry: LocationTrackGeometry,
    context: GeocodingContext<*>?,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
): List<LinkedElement> =
    geometry.edges.flatMapIndexed { edgeIndex, edge ->
        edge.segments
            .mapIndexed { segmentIndex, segment -> segmentIndex to segment }
            .filter { (_, s) -> overlapsAddressInterval(s, context, startAddress, endAddress) }
            .map { (segmentIndex, segment) -> LinkedElement(edgeIndex, segmentIndex, edge, segment, segment.sourceId) }
    }

private fun overlapsAddressInterval(
    segment: ISegment,
    context: GeocodingContext<*>?,
    start: TrackMeter?,
    end: TrackMeter?,
): Boolean =
    (end == null || context != null && context.getAddress(segment.segmentStart)?.first?.let { it < end } == true) &&
        (start == null || context != null && context.getAddress(segment.segmentEnd)?.first?.let { it > start } == true)
