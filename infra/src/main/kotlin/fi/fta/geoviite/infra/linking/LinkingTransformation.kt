package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.EdgeM
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.PlaceHolderNodeConnection
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignmentM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.SegmentM
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TmpLayoutEdge
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.combineEdges
import fi.fta.geoviite.infra.tracklayout.toEdgeM
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

const val ALIGNMENT_LINKING_SNAP = 0.001

fun cutLocationTrackGeometry(geometry: LocationTrackGeometry, mRange: Range<LineM<LocationTrackM>>) =
    TmpLocationTrackGeometry.of(slice(geometry, mRange, ALIGNMENT_LINKING_SNAP), geometry.trackId)

fun cutReferenceLineGeometry(geometry: ReferenceLineGeometry, mRange: Range<LineM<ReferenceLineM>>): ReferenceLineGeometry =
    tryCreateLinkedReferenceLineGeometry(geometry, slice(geometry, mRange, ALIGNMENT_LINKING_SNAP))

fun replaceLocationTrackGeometry(
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<LineM<PlanLayoutAlignmentM>>,
    trackId: IntId<LocationTrack>,
): LocationTrackGeometry = tryCreateLinkedTrackGeometry {
    TmpLocationTrackGeometry.ofSegments(createAlignmentGeometry(geometryAlignment, geometryMRange), trackId)
}

fun replaceReferenceLineGeometry(
    originalGeometry: ReferenceLineGeometry,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<LineM<PlanLayoutAlignmentM>>,
): ReferenceLineGeometry =
    tryCreateLinkedReferenceLineGeometry(originalGeometry, createAlignmentGeometry(geometryAlignment, geometryMRange))

fun linkLocationTrackGeometrySection(
    layoutGeometry: LocationTrackGeometry,
    layoutMRange: Range<LineM<LocationTrackM>>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<LineM<PlanLayoutAlignmentM>>,
): LocationTrackGeometry =
    splice(layoutGeometry, layoutMRange, createAlignmentGeometry(geometryAlignment, geometryMRange))

fun linkLayoutGeometrySection(
    referenceLineGeometry: ReferenceLineGeometry,
    layoutMRange: Range<LineM<ReferenceLineM>>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<LineM<PlanLayoutAlignmentM>>,
): ReferenceLineGeometry = splice(referenceLineGeometry, layoutMRange, createAlignmentGeometry(geometryAlignment, geometryMRange))

private fun createAlignmentGeometry(
    geometryAlignment: PlanLayoutAlignment,
    mRange: Range<LineM<PlanLayoutAlignmentM>>,
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
                            SegmentPoint(x = start.x, y = start.y, z = null, m = LineM(0.0), cant = null),
                            SegmentPoint(x = end.x, y = end.y, z = null, m = LineM(length), cant = null),
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

private fun tryCreateLinkedReferenceLineGeometry(original: ReferenceLineGeometry, newSegments: List<LayoutSegment>): ReferenceLineGeometry =
    try {
        original.withSegments(newSegments.also(::validateSegments))
    } catch (e: IllegalArgumentException) {
        throw LinkingFailureException(
            message = "Linking selection produces invalid reference line geometry",
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
    geometry: ReferenceLineGeometry,
    mRange: Range<LineM<ReferenceLineM>>,
    added: List<LayoutSegment>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): ReferenceLineGeometry {
    val start = slice(geometry, Range(LineM(0.0), mRange.min), snapDistance)
    val startGap = listOfNotNull(createGapIfNeeded(start, added))
    val end = slice(geometry, Range(mRange.max, geometry.length), snapDistance)
    val endGap = listOfNotNull(createGapIfNeeded(added, end))
    return tryCreateLinkedReferenceLineGeometry(geometry, start + startGap + added + endGap + end)
}

fun splice(
    geometry: LocationTrackGeometry,
    mRange: Range<LineM<LocationTrackM>>,
    added: List<LayoutSegment>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): LocationTrackGeometry {
    val startEdges = slice(geometry, Range(LineM(0.0), mRange.min), snapDistance)
    val startGap = listOfNotNull(createGapIfNeeded(startEdges.lastOrNull()?.segments ?: listOf(), added))
    val endEdges = slice(geometry, Range(mRange.max, geometry.length), snapDistance)
    val endGap = listOfNotNull(createGapIfNeeded(added, endEdges.firstOrNull()?.segments ?: listOf()))
    val midEdge = TmpLayoutEdge.of(startGap + added + endGap, null)
    return tryCreateLinkedTrackGeometry {
        TmpLocationTrackGeometry.of(combineEdges(startEdges + midEdge + endEdges), geometry.trackId)
    }
}

fun <M : AlignmentM<M>> slice(
    alignment: IAlignment<M>,
    mRange: Range<LineM<M>>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutSegment> = slice(alignment.segmentsWithM, mRange, snapDistance)

fun <M : AlignmentM<M>> splitSegments(
    segments: List<Pair<LayoutSegment, Range<LineM<M>>>>,
    splitPositionM: LineM<M>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): Pair<List<LayoutSegment>, List<LayoutSegment>> =
    if (segments.isEmpty()) emptyList<LayoutSegment>() to emptyList()
    else {
        val head = slice(segments, Range(LineM(0.0), splitPositionM), snapDistance)
        val tail = slice(segments, Range(splitPositionM, segments.last().second.max), snapDistance)
        head to tail
    }

fun <M : AlignmentM<M>> slice(
    segmentsWithM: List<Pair<ISegment, Range<LineM<M>>>>,
    mRange: Range<LineM<M>>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutSegment> =
    segmentsWithM.mapNotNull { (segment, segmentM) ->
        if (segmentM.max - snapDistance <= mRange.min || segmentM.min + snapDistance >= mRange.max) {
            null
        } else if (segmentM.min >= mRange.min - snapDistance && segmentM.max <= mRange.max + snapDistance) {
            toLayoutSegment(segment)
        } else {
            val range =
                Range<LineM<SegmentM>>(
                    LineM(max(mRange.min.distance - segmentM.min.distance, 0.0)),
                    LineM(min(mRange.max.distance - segmentM.min.distance, segment.length)),
                )
            toLayoutSegment(segment).slice(segmentMRange = range, snapDistance = snapDistance)
        }
    }

fun slice(
    geometry: LocationTrackGeometry,
    mRange: Range<LineM<LocationTrackM>>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutEdge> {
    return geometry.edgesWithM.mapNotNull { (e, m) ->
        if (m.max - snapDistance <= mRange.min || m.min + snapDistance >= mRange.max) {
            null
        } else if (m.min >= mRange.min - snapDistance && m.max <= mRange.max + snapDistance) {
            e
        } else {
            slice(e, mRange.map { d -> d.toEdgeM(m.min) }, snapDistance)
        }
    }
}

fun slice(edge: LayoutEdge, mRange: Range<LineM<EdgeM>>, snapDistance: Double = ALIGNMENT_LINKING_SNAP): LayoutEdge =
    TmpLayoutEdge(
        startNode = edge.startNode.takeIf { mRange.min.distance - snapDistance <= 0.0 } ?: PlaceHolderNodeConnection,
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
