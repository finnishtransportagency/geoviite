package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.PlaceHolderNodeConnection
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TmpLayoutEdge
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.combineEdges
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

const val ALIGNMENT_LINKING_SNAP = 0.001

fun cutLocationTrackGeometry(geometry: LocationTrackGeometry, mRange: Range<Double>) =
    TmpLocationTrackGeometry.of(slice(geometry, mRange, ALIGNMENT_LINKING_SNAP), geometry.trackId)

fun cutLayoutGeometry(alignment: LayoutAlignment, mRange: Range<Double>): LayoutAlignment =
    tryCreateLinkedAlignment(alignment, slice(alignment, mRange, ALIGNMENT_LINKING_SNAP))

fun replaceLocationTrackGeometry(
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
    trackId: IntId<LocationTrack>,
): LocationTrackGeometry = tryCreateLinkedTrackGeometry {
    TmpLocationTrackGeometry.ofSegments(createAlignmentGeometry(geometryAlignment, geometryMRange), trackId)
}

fun replaceLayoutGeometry(
    layoutAlignment: LayoutAlignment,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LayoutAlignment =
    tryCreateLinkedAlignment(layoutAlignment, createAlignmentGeometry(geometryAlignment, geometryMRange))

fun linkLocationTrackGeometrySection(
    layoutGeometry: LocationTrackGeometry,
    layoutMRange: Range<Double>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LocationTrackGeometry =
    splice(layoutGeometry, layoutMRange, createAlignmentGeometry(geometryAlignment, geometryMRange))

fun linkLayoutGeometrySection(
    layoutAlignment: LayoutAlignment,
    layoutMRange: Range<Double>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LayoutAlignment = splice(layoutAlignment, layoutMRange, createAlignmentGeometry(geometryAlignment, geometryMRange))

private fun createAlignmentGeometry(
    geometryAlignment: PlanLayoutAlignment,
    mRange: Range<Double>,
): List<LayoutSegment> = slice(geometryAlignment, mRange, ALIGNMENT_LINKING_SNAP)

private fun createLinkingSegment(start: IPoint?, end: IPoint?, tolerance: Double = LAYOUT_M_DELTA): LayoutSegment? {
    if (start == null || end == null) return null
    val length = calculateDistance(LAYOUT_SRID, start, end)
    return if (length > tolerance)
        LayoutSegment(
            geometry =
                SegmentGeometry(
                    resolution = max(length.toInt(), 1),
                    segmentPoints =
                        listOf(
                            SegmentPoint(x = start.x, y = start.y, z = null, m = 0.0, cant = null),
                            SegmentPoint(x = end.x, y = end.y, z = null, m = length, cant = null),
                        ),
                ),
            sourceId = null,
            sourceStartM = null,
            source = GeometrySource.GENERATED,
        )
    else null
}

private fun toLayoutSegment(segment: ISegment): LayoutSegment =
    if (segment is LayoutSegment) segment
    else
        LayoutSegment(
            geometry = segment.geometry,
            sourceId = segment.sourceId as? IndexedId,
            sourceStartM = segment.sourceStartM,
            source = segment.source,
        )

private fun tryCreateLinkedTrackGeometry(creator: () -> TmpLocationTrackGeometry): LocationTrackGeometry =
    try {
        creator()
    } catch (e: IllegalArgumentException) {
        throw LinkingFailureException(
            message = "Linking selection produces invalid location track geometry",
            cause = e,
            localizedMessageKey = "alignment-geometry",
        )
    }

private fun tryCreateLinkedAlignment(original: LayoutAlignment, newSegments: List<LayoutSegment>): LayoutAlignment =
    try {
        original.withSegments(newSegments.also(::validateSegments))
    } catch (e: IllegalArgumentException) {
        throw LinkingFailureException(
            message = "Linking selection produces invalid alignment",
            cause = e,
            localizedMessageKey = "alignment-geometry",
        )
    }

private fun validateSegments(newSegments: List<LayoutSegment>) =
    newSegments.forEachIndexed { index, segment ->
        newSegments.getOrNull(index - 1)?.let { previous ->
            val diff = angleDiffRads(previous.endDirection, segment.startDirection)
            if (diff > PI / 2)
                throw LinkingFailureException(
                    message =
                        "Linked geometry has over 90 degree angles between segments: " +
                            "segments=${index-1}-$index prevEnd=${previous.endDirection} next=${segment.endDirection} angle=${radsToDegrees(diff)} point=${segment.segmentStart}",
                    localizedMessageKey = "segments-sharp-angle",
                )
        }
    }

fun splice(
    alignment: LayoutAlignment,
    mRange: Range<Double>,
    added: List<LayoutSegment>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): LayoutAlignment {
    val start = slice(alignment, Range(0.0, mRange.min), snapDistance)
    val startGap = listOfNotNull(createGapIfNeeded(start, added))
    val end = slice(alignment, Range(mRange.max, alignment.length), snapDistance)
    val endGap = listOfNotNull(createGapIfNeeded(added, end))
    return tryCreateLinkedAlignment(alignment, start + startGap + added + endGap + end)
}

fun splice(
    geometry: LocationTrackGeometry,
    mRange: Range<Double>,
    added: List<LayoutSegment>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): LocationTrackGeometry {
    val startEdges = slice(geometry, Range(0.0, mRange.min), snapDistance)
    val startGap = listOfNotNull(createGapIfNeeded(startEdges.lastOrNull()?.segments ?: listOf(), added))
    val endEdges = slice(geometry, Range(mRange.max, geometry.length), snapDistance)
    val endGap = listOfNotNull(createGapIfNeeded(added, endEdges.firstOrNull()?.segments ?: listOf()))
    val midEdge = TmpLayoutEdge.of(startGap + added + endGap, null)
    return tryCreateLinkedTrackGeometry {
        TmpLocationTrackGeometry.of(combineEdges(startEdges + midEdge + endEdges), geometry.trackId)
    }
}

fun slice(
    alignment: IAlignment,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutSegment> = slice(alignment.segmentsWithM, mRange, snapDistance)

fun splitSegments(
    segments: List<Pair<LayoutSegment, Range<Double>>>,
    splitPositionM: Double,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): Pair<List<LayoutSegment>, List<LayoutSegment>> =
    if (segments.isEmpty()) emptyList<LayoutSegment>() to emptyList()
    else {
        val head = slice(segments, Range(0.0, splitPositionM), snapDistance)
        val tail = slice(segments, Range(splitPositionM, segments.last().second.max), snapDistance)
        head to tail
    }

fun slice(
    segmentsWithM: List<Pair<ISegment, Range<Double>>>,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutSegment> =
    segmentsWithM.mapNotNull { (segment, segmentM) ->
        if (segmentM.max - snapDistance <= mRange.min || segmentM.min + snapDistance >= mRange.max) {
            null
        } else if (segmentM.min >= mRange.min - snapDistance && segmentM.max <= mRange.max + snapDistance) {
            toLayoutSegment(segment)
        } else {
            val range = Range(max(mRange.min - segmentM.min, 0.0), min(mRange.max - segmentM.min, segment.length))
            toLayoutSegment(segment).slice(segmentMRange = range, snapDistance = snapDistance)
        }
    }

fun slice(
    geometry: LocationTrackGeometry,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutEdge> {
    return geometry.edgesWithM.mapNotNull { (e, m) ->
        if (m.max - snapDistance <= mRange.min || m.min + snapDistance >= mRange.max) {
            null
        } else if (m.min >= mRange.min - snapDistance && m.max <= mRange.max + snapDistance) {
            e
        } else {
            slice(e, Range(mRange.min - m.min, mRange.max - m.min), snapDistance)
        }
    }
}

fun slice(edge: LayoutEdge, mRange: Range<Double>, snapDistance: Double = ALIGNMENT_LINKING_SNAP): LayoutEdge =
    TmpLayoutEdge(
        startNode = edge.startNode.takeIf { mRange.min - snapDistance <= 0.0 } ?: PlaceHolderNodeConnection,
        endNode = edge.endNode.takeIf { mRange.max + snapDistance >= edge.length } ?: PlaceHolderNodeConnection,
        segments = slice(edge.segmentsWithM, mRange, snapDistance),
    )

fun createGapIfNeeded(preceding: List<LayoutSegment>, following: List<LayoutSegment>): LayoutSegment? {
    val start = lastPoint(preceding)
    val end = firstPoint(following)
    return if (start != null && end != null) createLinkingSegment(start, end) else null
}

private fun firstPoint(segments: List<LayoutSegment>) = segments.firstOrNull()?.segmentStart

private fun lastPoint(segments: List<LayoutSegment>) = segments.lastOrNull()?.segmentEnd
