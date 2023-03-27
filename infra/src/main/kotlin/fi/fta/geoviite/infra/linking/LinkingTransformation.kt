package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.tracklayout.*
import kotlin.math.max
import kotlin.math.min

const val ALIGNMENT_LINKING_SNAP = 0.001

fun replaceTrackLayoutGeometry(
    geometryAlignment: MapAlignment<GeometryAlignment>,
    layoutAlignment: LayoutAlignment,
    layoutMInterval: Range<Double>,
    geometryMInterval: Range<Double>,
): List<LayoutSegment> {
    val startSegments = sliceSegments(layoutAlignment.segments, Range(0.0, layoutMInterval.min))
    val geometrySegments = createAlignmentGeometry(geometryAlignment, geometryMInterval)
    val endSegments = sliceSegments(layoutAlignment.segments, Range(layoutMInterval.max, layoutAlignment.length))

    val startGap = createLinkingSegment(lastPoint(startSegments), firstPoint(geometrySegments))
    val endGap = createLinkingSegment(lastPoint(geometrySegments), firstPoint(endSegments))

    val combinedSegments = fixSegmentStarts(
        startSegments + listOfNotNull(startGap) + geometrySegments + listOfNotNull(endGap) + endSegments
    )
    val affectedSwitchIds = getSwitchIdsInside(layoutAlignment.segments, layoutMInterval)
    return removeSwitches(combinedSegments, affectedSwitchIds)
}

fun firstPoint(segments: List<LayoutSegment>) = segments.firstOrNull()?.points?.firstOrNull()

fun lastPoint(segments: List<LayoutSegment>) = segments.lastOrNull()?.points?.lastOrNull()

fun createAlignmentGeometry(
    geometryAlignment: MapAlignment<GeometryAlignment>,
    mRange: Range<Double>,
): List<LayoutSegment> = sliceSegments(geometryAlignment.segments, mRange, ALIGNMENT_LINKING_SNAP)

fun extendAlignmentWithGeometry(
    layoutAlignment: LayoutAlignment,
    geometryAlignment: MapAlignment<GeometryAlignment>,
    layoutM: Double,
    geometryMInterval: Range<Double>,
): List<LayoutSegment> {
    val addedSegments = sliceSegments(geometryAlignment.segments, geometryMInterval, ALIGNMENT_LINKING_SNAP)
    val newSegments =
        if (isSame(0.0, layoutM, ALIGNMENT_LINKING_SNAP)) {
            val gap = createLinkingSegment(lastPoint(addedSegments), firstPoint(layoutAlignment.segments))
            addedSegments + listOfNotNull(gap) + layoutAlignment.segments
        } else if (isSame(layoutAlignment.length, layoutM, ALIGNMENT_LINKING_SNAP)) {
            val gap = createLinkingSegment(lastPoint(layoutAlignment.segments), firstPoint(addedSegments))
            layoutAlignment.segments + listOfNotNull(gap) + addedSegments
        } else {
            throw LinkingFailureException("Alignment cannot be extended with selected m-values")
        }
    return fixSegmentStarts(newSegments)
}

fun createLinkingSegment(start: IPoint?, end: IPoint?, tolerance: Double = LAYOUT_M_DELTA): LayoutSegment? {
    if (start == null || end == null) return null
    val length = calculateDistance(LAYOUT_SRID, start, end)
    return if (length > tolerance) LayoutSegment(
        geometry = SegmentGeometry(
            resolution = max(length.toInt(), 1),
            points = listOf(
                LayoutPoint(start.x, start.y, null, 0.0, null),
                LayoutPoint(end.x, end.y, null, length, null)
            ),
        ),
        sourceId = null,
        sourceStart = null,
        switchId = null,
        startJointNumber = null,
        endJointNumber = null,
        source = GeometrySource.GENERATED,
    ) else null
}

fun getSegmentsBeforeNewGeometry(segments: List<LayoutSegment>, index: Int): List<LayoutSegment> {
    return if (index == 0) return listOf() else segments.take(index)
}

fun getSegmentsAfterNewGeometry(
    segments: List<LayoutSegment>,
    index: Int,
    start: Double,
): List<LayoutSegment> {
    if (index == segments.size - 1) return listOf()
    var segmentStart = start
    return segments.drop(index + 1).map { s ->
        s.withStartM(segmentStart).also { fixed -> segmentStart += fixed.length }
    }
}

fun getSegmentsInRange(
    segments: List<LayoutSegment>,
    startIndex: Int,
    endIndex: Int,
    start: Double,
): List<LayoutSegment> {
    var segmentStart = start
    return if (startIndex <= endIndex) {
        segments.slice(startIndex..endIndex).map { s ->
            s.withStartM(segmentStart).also { fixed -> segmentStart += fixed.length }
        }
    } else {
        listOf()
    }
}

fun toLayoutSegment(segment: ISegment): LayoutSegment =
    if (segment is LayoutSegment) segment
    else LayoutSegment(
        geometry = segment.geometry,
        sourceId = segment.sourceId,
        sourceStart = segment.sourceStart,
        switchId = null,
        startJointNumber = null,
        endJointNumber = null,
        source = segment.source,
    )

fun isLinkedToSwitch(locationTrack: LocationTrack, alignment: LayoutAlignment, switchId: IntId<TrackLayoutSwitch>) =
    locationTrack.topologyStartSwitch?.switchId == switchId ||
            locationTrack.topologyEndSwitch?.switchId == switchId ||
            alignment.segments.any { seg -> seg.switchId == switchId }

fun sliceSegments(
    segments: List<ISegment>,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP
): List<LayoutSegment> =
    segments.mapNotNull { segment ->
        if (segment.endM <= mRange.min || segment.startM >= mRange.max) {
            null
        } else if (segment.startM >= mRange.min - snapDistance && segment.endM <= mRange.max + snapDistance) {
            toLayoutSegment(segment)
        } else {
            toLayoutSegment(segment).slice(
                mRange = Range(max(mRange.min, segment.startM), min(mRange.max, segment.endM)),
                snapDistance = snapDistance
            )
        }
    }

fun removeSwitches(segments: List<LayoutSegment>, switchIds: Set<DomainId<TrackLayoutSwitch>>): List<LayoutSegment> =
    segments.map { s -> if (switchIds.contains(s.switchId)) s.withoutSwitch() else s }

fun getSwitchIdsInside(segments: List<LayoutSegment>, mRange: Range<Double>) =
    getSwitchIds(segments) { s -> mRange.min < s.endM && mRange.max > s.startM }

fun getSwitchIdsOutside(segments: List<LayoutSegment>, mRange: Range<Double>) =
    getSwitchIds(segments) { s -> mRange.min > s.startM || mRange.max < s.endM }

fun getSwitchIds(segments: List<LayoutSegment>, predicate: (LayoutSegment) -> Boolean) =
    segments.mapNotNull { s -> if (predicate(s)) s.switchId else null }.toSet()

fun fixSegmentStarts(segments: List<LayoutSegment>): List<LayoutSegment> {
    var cumulativeM = 0.0
    return segments.map { s -> s.withStartM(cumulativeM).also { cumulativeM += s.length } }
}
