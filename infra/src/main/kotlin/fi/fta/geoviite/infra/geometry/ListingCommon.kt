package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.tracklayout.LayoutSegment

fun collectLinkedElements(
    segments: List<LayoutSegment>,
    context: GeocodingContext?,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
) =
    segments
        .filter { segment -> overlapsAddressInterval(segment, context, startAddress, endAddress) }
        .map { s -> if (s.sourceId is IndexedId) s to s.sourceId else s to null }

private fun overlapsAddressInterval(
    segment: LayoutSegment,
    context: GeocodingContext?,
    start: TrackMeter?,
    end: TrackMeter?,
): Boolean =
    (end == null || context != null && getStartAddress(segment, context)?.let { it < end } == true) &&
        (start == null || context != null && getEndAddress(segment, context)?.let { it > start } == true)

private fun getStartAddress(segment: LayoutSegment, context: GeocodingContext) =
    context.getAddress(segment.alignmentStart)?.first

private fun getEndAddress(segment: LayoutSegment, context: GeocodingContext) =
    context.getAddress(segment.alignmentEnd)?.first
