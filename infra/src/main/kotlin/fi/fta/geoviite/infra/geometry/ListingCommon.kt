package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.tracklayout.ISegment

data class LinkedElement(val segmentIndex: Int, val segment: ISegment, val elementId: IndexedId<GeometryElement>?)

fun collectLinkedElements(
    segments: List<ISegment>,
    context: GeocodingContext?,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
): List<LinkedElement> =
    segments
        .mapIndexed { index, segment -> index to segment }
        .filter { (_, s) -> overlapsAddressInterval(s, context, startAddress, endAddress) }
        .map { (i, s) -> LinkedElement(i, s, s.sourceId as? IndexedId) }

private fun overlapsAddressInterval(
    segment: ISegment,
    context: GeocodingContext?,
    start: TrackMeter?,
    end: TrackMeter?,
): Boolean =
    (end == null || context != null && context.getAddress(segment.segmentStart)?.first?.let { it < end } == true) &&
        (start == null || context != null && context.getAddress(segment.segmentEnd)?.first?.let { it > start } == true)
