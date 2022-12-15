package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import kotlin.math.max

fun <T> getSegmentIndexRange(alignment: LayoutAlignment, interval: LayoutInterval<T>) =
    getRange(interval.start.segmentId, interval.end.segmentId, alignment.segments.map(LayoutSegment::id))
        ?: throw LinkingFailureException("Layout segment range invalid: alignment=${alignment.id} interval=$interval")

fun getSegmentIndexRange(alignment: MapAlignment<GeometryAlignment>, interval: GeometryInterval) =
    getRange(interval.start.segmentId, interval.end.segmentId, alignment.segments.map(MapSegment::id))
        ?: throw LinkingFailureException("Geometry segment range invalid: alignment=${alignment.id} interval=$interval")

fun <T> getRange(startValue: T, endValue: T, all: List<T>): ClosedRange<Int>? {
    val start = all.indexOf(startValue)
    val end = all.indexOf(endValue)
    return if (start >= 0 && end >= 0 && end >= start) start..end else null
}

fun <T> replaceTrackLayoutGeometry(
    geometryAlignment: MapAlignment<GeometryAlignment>,
    layoutAlignment: LayoutAlignment,
    layoutInterval: LayoutInterval<T>,
    geometryInterval: GeometryInterval,
): List<LayoutSegment> {
    val fromGeometryPoint = geometryInterval.start.point
    val toGeometryPoint = geometryInterval.end.point
    val fromLayoutPoint = layoutInterval.start.point
    val toLayoutPoint = layoutInterval.end.point

    val layoutIndexRange = getSegmentIndexRange(layoutAlignment, layoutInterval)
    val geometryIndexRange = getSegmentIndexRange(geometryAlignment, geometryInterval)

    val startLayoutSegments = if (layoutAlignment.start?.isSame(fromLayoutPoint) != true) getStartLayoutSegments(
        layoutAlignment.segments,
        layoutIndexRange.start,
        fromLayoutPoint,
        fromGeometryPoint,
    ) else emptyList()

    val geometrySegments = getSegmentsBetweenPoints(
        geometryIndexRange.start,
        geometryIndexRange.endInclusive,
        getSegmentsWithoutSwitchInformation(geometryAlignment.segments),
        fromGeometryPoint,
        toGeometryPoint,
        endLength(startLayoutSegments),
    )

    val endLayoutSegments = if (layoutAlignment.end?.isSame(toLayoutPoint) != true) getEndLayoutSegments(
        layoutAlignment.segments,
        layoutIndexRange.endInclusive,
        toLayoutPoint,
        toGeometryPoint,
        endLength(geometrySegments),
    ) else emptyList()

    return (startLayoutSegments + geometrySegments + endLayoutSegments)
}

fun endLength(segments: List<LayoutSegment>): Double =
    segments.lastOrNull()?.let(::endLength) ?: 0.0

fun endLength(segment: LayoutSegment): Double =
    segment.start + segment.length

fun extendAlignmentWithGeometry(
    layoutAlignment: LayoutAlignment,
    geometryAlignment: MapAlignment<GeometryAlignment>,
    layoutPoint: Point,
    geometryInterval: GeometryInterval,
): List<LayoutSegment> {
    val geometryIndexRange = getSegmentIndexRange(geometryAlignment, geometryInterval)
    val fromGeometryPoint = geometryInterval.start.point
    val toGeometryPoint = geometryInterval.end.point

    return if (layoutAlignment.start?.isSame(layoutPoint, LAYOUT_COORDINATE_DELTA) == true) {
        val geometrySegments = getSegmentsBetweenPoints(
            geometryIndexRange.start,
            geometryIndexRange.endInclusive,
            getSegmentsWithoutSwitchInformation(geometryAlignment.segments),
            fromGeometryPoint,
            toGeometryPoint,
            0.0,
        )

        val gapSegment = createGapConnectionSegment(toGeometryPoint, layoutPoint, endLength(geometrySegments))

        val layoutSegments = getSegmentsInRange(
            layoutAlignment.segments,
            0,
            layoutAlignment.segments.lastIndex,
            endLength(listOfNotNull(gapSegment))
        )

        geometrySegments + listOfNotNull(gapSegment) + layoutSegments
    } else if (layoutAlignment.end?.isSame(layoutPoint, LAYOUT_COORDINATE_DELTA) == true) {
        val gapSegment = createGapConnectionSegment(layoutPoint, fromGeometryPoint, endLength(layoutAlignment.segments))

        val geometrySegments = getSegmentsBetweenPoints(
            geometryIndexRange.start,
            geometryIndexRange.endInclusive,
            getSegmentsWithoutSwitchInformation(geometryAlignment.segments),
            fromGeometryPoint,
            toGeometryPoint,
            endLength(listOfNotNull(gapSegment))
        )

        layoutAlignment.segments + listOfNotNull(gapSegment) + geometrySegments
    } else {
        throw LinkingFailureException("Alignment cannot be extended with selected points")
    }
}

fun getStartLayoutSegments(
    segments: List<LayoutSegment>,
    startLayoutIndex: Int,
    fromLayoutPoint: Point,
    fromGeometryPoint: Point,
): List<LayoutSegment> {
    val segmentsBeforeStart = getSegmentsBeforeNewGeometry(segments, startLayoutIndex)
    val partialStartJoinSegment = cutSegmentBeforePoint(segments[startLayoutIndex], fromLayoutPoint)
    val lengthBeforeGap = partialStartJoinSegment?.let(::endLength) ?: endLength(segmentsBeforeStart)
    val gapSegment = createGapConnectionSegment(fromLayoutPoint, fromGeometryPoint, lengthBeforeGap)
    return segmentsBeforeStart + listOfNotNull(partialStartJoinSegment, gapSegment)
}

fun getEndLayoutSegments(
    segments: List<LayoutSegment>,
    endLayoutIndex: Int,
    toLayoutPoint: Point,
    toGeometryPoint: Point,
    startLength: Double,
): List<LayoutSegment> {
    val gapSegment = createGapConnectionSegment(toGeometryPoint, toLayoutPoint, startLength)
    val lengthAfterGap = gapSegment?.let(::endLength) ?: startLength
    val partialEndJoinSegment = cutSegmentAfterPoint(segments[endLayoutIndex], toLayoutPoint, lengthAfterGap)
    val lengthAfterJoin = partialEndJoinSegment?.let(::endLength) ?: lengthAfterGap
    val segmentsAfterEnd = getSegmentsAfterNewGeometry(segments, endLayoutIndex, lengthAfterJoin)
    return listOfNotNull(gapSegment) + listOfNotNull(partialEndJoinSegment) + segmentsAfterEnd
}

fun getSegmentsBetweenPoints(
    fromIndex: Int,
    toIndex: Int,
    segments: List<LayoutSegment>,
    fromPoint: Point,
    toPoint: Point,
    startLength: Double,
): List<LayoutSegment> {
    val cutoutSwitches: List<DomainId<TrackLayoutSwitch>>

    // if just 1 segment
    val newSegments = if (fromIndex == toIndex) {
        val newSegment = cutSegmentBetweenStartAndEndPoints(segments[fromIndex], fromPoint, toPoint, startLength)
            ?: throw LinkingFailureException("No segments in selected geometry")

        cutoutSwitches =
            listOfNotNull(
                if (newSegment.points.size != segments[fromIndex].points.size) segments[fromIndex].switchId
                else null
            )
        listOf(newSegment)
    } else {
        val fromSegment = segments[fromIndex]
        val toSegment = segments[toIndex]

        val startSegment = cutSegmentAfterPoint(fromSegment, fromPoint, startLength)
        val lengthAfterFirst = startSegment?.let(::endLength) ?: startLength
        val midSegments = getSegmentsInRange(segments, fromIndex + 1, toIndex - 1, lengthAfterFirst)
        val lengthAfterMid = midSegments.lastOrNull()?.let(::endLength) ?: lengthAfterFirst
        val endSegment = cutSegmentBeforePoint(toSegment, toPoint, lengthAfterMid)
        val leftoverSegments = segments.take(fromIndex) + segments.takeLast(segments.lastIndex - toIndex)

        val startSegmentCutoutSwitch =
            if (startSegment?.points?.size == fromSegment.points.size) null else startSegment?.switchId
        val endSegmentCutoutSwitch =
            if (endSegment?.points?.size == toSegment.points.size) null else endSegment?.switchId

        cutoutSwitches = listOfNotNull(
            startSegmentCutoutSwitch,
            endSegmentCutoutSwitch
        ) + leftoverSegments.mapNotNull { it.switchId }

        (listOfNotNull(startSegment) + midSegments + listOfNotNull(endSegment))
    }

    return newSegments.map { segment ->
        if (cutoutSwitches.contains(segment.switchId)) segment.copy(
            switchId = null,
            startJointNumber = null,
            endJointNumber = null
        )
        else segment
    }
}

fun cutSegmentBeforePoint(
    segmentToCut: LayoutSegment,
    toPoint: IPoint,
    startLength: Double = segmentToCut.start,
): LayoutSegment? {
    val fromIndex = 0
    val toIndex = segmentToCut.getPointIndex(toPoint) ?: throw LinkingFailureException("to point index not found")
    return segmentToCut.slice(fromIndex, toIndex, startLength)
}

fun cutSegmentAfterPoint(
    segmentToCut: LayoutSegment,
    fromPoint: IPoint,
    startLength: Double,
): LayoutSegment? {
    val fromIndex = segmentToCut.getPointIndex(fromPoint) ?: throw LinkingFailureException("from point index not found")
    val toIndex = segmentToCut.points.lastIndex
    return segmentToCut.slice(fromIndex, toIndex, startLength)
}

fun cutSegmentBetweenStartAndEndPoints(
    segmentToCut: LayoutSegment,
    fromPoint: Point,
    toPoint: Point,
    startLength: Double,
): LayoutSegment? {
    val fromIndex = segmentToCut.getPointIndex(fromPoint) ?: throw LinkingFailureException("from point index not found")
    val toIndex = segmentToCut.getPointIndex(toPoint) ?: throw LinkingFailureException("to point index not found")
    return segmentToCut.slice(fromIndex, toIndex, startLength)
}

fun createGapConnectionSegment(start: Point, end: Point, startLength: Double): LayoutSegment? {
    val length = calculateDistance(LAYOUT_SRID, start, end)
    return if (length > 0) LayoutSegment(
        points = listOf(
            LayoutPoint(start.x, start.y, null, 0.0, null),
            LayoutPoint(end.x, end.y, null, length, null)
        ),
        sourceId = null,
        sourceStart = null,
        resolution = max(length.toInt(), 1),
        switchId = null,
        startJointNumber = null,
        endJointNumber = null,
        start = startLength,
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
        val newSegment = s.copy(start = segmentStart)
        segmentStart += s.length
        newSegment
    }
}

fun getSegmentsInRange(
    segments: List<LayoutSegment>,
    startIndex: Int,
    endIndex: Int,
    start: Double,
): List<LayoutSegment> {
    var segmentStart = start
    return if (startIndex <=
        endIndex
    ) {
        segments.slice(startIndex..endIndex).map { s ->
            val segment = s.copy(start = segmentStart)
            segmentStart += s.length
            segment
        }
    } else {
        listOf()
    }
}

fun getSegmentsWithoutSwitchInformation(segments: List<MapSegment>): List<LayoutSegment> {
    return segments.map { segment ->
        LayoutSegment(
            points = segment.points,
            sourceId = segment.sourceId,
            sourceStart = segment.sourceStart,
            resolution = segment.resolution,
            switchId = null,
            startJointNumber = null,
            endJointNumber = null,
            start = segment.start,
            source = segment.source,
        )
    }
}

fun isLinkedToSwitch(locationTrack: LocationTrack, alignment: LayoutAlignment, switchId: IntId<TrackLayoutSwitch>) =
    locationTrack.topologyStartSwitch?.switchId == switchId ||
            locationTrack.topologyEndSwitch?.switchId == switchId ||
            alignment.segments.any { seg -> seg.switchId == switchId }
