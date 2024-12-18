package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

const val ALIGNMENT_LINKING_SNAP = 0.001

fun cutLayoutGeometry(alignment: LayoutAlignment, mRange: Range<Double>): LayoutAlignment {
    val cutSegments = sliceSegments(alignment, mRange, ALIGNMENT_LINKING_SNAP)
    val newSegments = removeSwitches(cutSegments, getSwitchIdsOutside(alignment, mRange))
    return tryCreateLinkedAlignment(alignment, newSegments)
}

fun replaceLayoutGeometry(
    layoutAlignment: LayoutAlignment,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LayoutAlignment {
    val geometrySegments = createAlignmentGeometry(geometryAlignment, geometryMRange)
    return tryCreateLinkedAlignment(layoutAlignment, geometrySegments)
}

fun linkLayoutGeometrySection(
    layoutAlignment: LayoutAlignment,
    layoutMRange: Range<Double>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LayoutAlignment {
    val segments =
        if (layoutMRange.min == layoutMRange.max) {
            extendSegmentsWithGeometry(layoutAlignment, layoutMRange.min, geometryAlignment, geometryMRange)
        } else {
            replaceSegmentsWithGeometry(layoutAlignment, layoutMRange, geometryAlignment, geometryMRange)
        }
    return tryCreateLinkedAlignment(layoutAlignment, segments)
}

private fun createAlignmentGeometry(
    geometryAlignment: PlanLayoutAlignment,
    mRange: Range<Double>,
): List<LayoutSegment> = sliceSegments(geometryAlignment, mRange, ALIGNMENT_LINKING_SNAP)

private fun extendSegmentsWithGeometry(
    layoutAlignment: LayoutAlignment,
    layoutM: Double,
    geometryAlignment: PlanLayoutAlignment,
    geometryMInterval: Range<Double>,
): List<LayoutSegment> {
    val addedSegments = sliceSegments(geometryAlignment, geometryMInterval, ALIGNMENT_LINKING_SNAP)
    return if (isSame(0.0, layoutM, ALIGNMENT_LINKING_SNAP)) {
        val gap = createLinkingSegment(lastPoint(addedSegments), firstPoint(layoutAlignment.segments))
        addedSegments + listOfNotNull(gap) + layoutAlignment.segments
    } else if (isSame(layoutAlignment.length, layoutM, ALIGNMENT_LINKING_SNAP)) {
        val gap = createLinkingSegment(lastPoint(layoutAlignment.segments), firstPoint(addedSegments))
        layoutAlignment.segments + listOfNotNull(gap) + addedSegments
    } else {
        throw LinkingFailureException("Alignment cannot be extended with selected m-values")
    }
}

private fun replaceSegmentsWithGeometry(
    layoutAlignment: LayoutAlignment,
    layoutMInterval: Range<Double>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMInterval: Range<Double>,
): List<LayoutSegment> {
    val startSegments = sliceSegments(layoutAlignment, Range(0.0, layoutMInterval.min))
    val geometrySegments = createAlignmentGeometry(geometryAlignment, geometryMInterval)
    val endSegments = sliceSegments(layoutAlignment, Range(layoutMInterval.max, layoutAlignment.length))

    val startGap = createLinkingSegment(lastPoint(startSegments), firstPoint(geometrySegments))
    val endGap = createLinkingSegment(lastPoint(geometrySegments), firstPoint(endSegments))

    val combinedSegments =
        (startSegments + listOfNotNull(startGap) + geometrySegments + listOfNotNull(endGap) + endSegments)
    val affectedSwitchIds = getSwitchIdsInside(layoutAlignment, layoutMInterval)
    return removeSwitches(combinedSegments, affectedSwitchIds)
}

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
            sourceStart = null,
            switchId = null,
            startJointNumber = null,
            endJointNumber = null,
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
            sourceStart = segment.sourceStart,
            switchId = null,
            startJointNumber = null,
            endJointNumber = null,
            source = segment.source,
        )

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
                            "segment=${segment.id} prevEnd=${previous.endDirection} next=${segment.endDirection} angle=${radsToDegrees(diff)} point=${segment.segmentStart}",
                    localizedMessageKey = "segments-sharp-angle",
                )
        }
    }

fun sliceSegments(
    alignment: IAlignment,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutSegment> =
    alignment.segmentsWithM.mapNotNull { (segment, segmentM) ->
        if (segmentM.max <= mRange.min || segmentM.min >= mRange.max) {
            null
        } else if (segmentM.min >= mRange.min - snapDistance && segmentM.max <= mRange.max + snapDistance) {
            toLayoutSegment(segment)
        } else {
            val range = Range(max(mRange.min - segmentM.min, 0.0), min(mRange.max - segmentM.min, segment.length))
            toLayoutSegment(segment).slice(segmentMRange = range, snapDistance = snapDistance)
        }
    }

private fun firstPoint(segments: List<LayoutSegment>) = segments.firstOrNull()?.segmentStart

private fun lastPoint(segments: List<LayoutSegment>) = segments.lastOrNull()?.segmentEnd

fun removeSwitches(segments: List<LayoutSegment>, switchIds: Set<IntId<TrackLayoutSwitch>>): List<LayoutSegment> =
    segments.map { s -> if (switchIds.contains(s.switchId)) s.withoutSwitch() else s }

fun getSwitchIdsInside(alignment: LayoutAlignment, mRange: Range<Double>) =
    getSwitchIds(alignment) { m -> mRange.min < m.max && mRange.max > m.min }

fun getSwitchIdsOutside(alignment: LayoutAlignment, mRange: Range<Double>) =
    getSwitchIds(alignment) { m -> mRange.min > m.min || mRange.max < m.max }

private fun getSwitchIds(alignment: LayoutAlignment, predicate: (Range<Double>) -> Boolean) =
    alignment.segmentsWithM.mapNotNull { (s, m) -> if (predicate(m)) s.switchId else null }.toSet()
