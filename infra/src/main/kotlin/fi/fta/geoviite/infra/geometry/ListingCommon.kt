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
        .filter { s -> overlapsAddressInterval(s, context, startAddress, endAddress) }
        .map { s -> if (s.sourceId is IndexedId) s to s.sourceId else s to null }

private fun overlapsAddressInterval(
    segment: LayoutSegment,
    context: GeocodingContext?,
    start: TrackMeter?,
    end: TrackMeter?,
): Boolean =
    (end == null || context != null && context.getAddress(segment.segmentStart)?.first?.let { it < end } == true) &&
        (start == null || context != null && context.getAddress(segment.segmentEnd)?.first?.let { it > start } == true)
