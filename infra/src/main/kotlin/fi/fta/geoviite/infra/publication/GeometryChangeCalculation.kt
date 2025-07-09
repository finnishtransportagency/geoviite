package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.segmentToAlignmentM
import kotlin.math.max
import kotlin.math.min

data class GeometryChangeRanges<M : AlignmentM<M>>(
    val added: List<Range<LineM<M>>>,
    val removed: List<Range<LineM<M>>>,
)

fun <M : AlignmentM<M>> getChangedGeometryRanges(
    newSegments: List<Pair<LayoutSegment, Range<LineM<M>>>>,
    oldSegments: List<Pair<LayoutSegment, Range<LineM<M>>>>,
) =
    GeometryChangeRanges(
        added = getAddedSegmentMRanges(newSegments, oldSegments),
        removed = getAddedSegmentMRanges(oldSegments, newSegments),
    )

fun <M : AlignmentM<M>> getAddedSegmentMRanges(
    newSegments: List<Pair<LayoutSegment, Range<LineM<M>>>>,
    oldSegments: List<Pair<LayoutSegment, Range<LineM<M>>>>,
): List<Range<LineM<M>>> {
    val addedSegmentIndices = getAddedIndexRangesExclusive(newSegments, oldSegments) { (s, _) -> s.geometry.id }
    return addedSegmentIndices.flatMap { (newRange, oldRange) ->
        val newPoints = getPointsWithMExclusive(newSegments, newRange)
        val oldPoints = getPointsWithMExclusive(oldSegments, oldRange)
        getAddedIndexRangesExclusive(newPoints, oldPoints) { it.first }
            .map { (newPointRange, _) ->
                // The exclusive range over-indexes if the last point is inside the interval
                // However, we can then just use the last point itself for m-value as it will be a segment end
                val start = newPoints[max(0, newPointRange.min)]
                val end = newPoints[min(newPoints.lastIndex, newPointRange.max)]
                Range(start.second, end.second)
            }
    }
}

fun <M : AlignmentM<M>> getPointsWithMExclusive(
    segments: List<Pair<LayoutSegment, Range<LineM<M>>>>,
    indexRange: Range<Int>,
): List<Pair<Point, LineM<M>>> =
    indexRange
        .takeIf { r -> r.max - r.min >= 2 }
        ?.let { exclusive -> Range(exclusive.min + 1, exclusive.max - 1) }
        ?.let { inclusive ->
            (inclusive.min..inclusive.max).flatMap { i ->
                segments.getOrNull(i)?.let { (segment, m) ->
                    segment.segmentPoints
                        .let { if (i == inclusive.min) it else it.subList(0, it.lastIndex) }
                        .map { p -> p.toPoint() to p.m.segmentToAlignmentM(m.min) }
                } ?: emptyList()
            }
        } ?: emptyList()

fun <T, S> getAddedIndexRangesExclusive(
    newObjects: List<T>,
    oldObjects: List<T>,
    compareBy: (T) -> S,
): List<Pair<Range<Int>, Range<Int>>> {
    val oldCompareObjects = oldObjects.mapIndexed { i, o -> compareBy(o) to i }.toMap()
    val addedIndexRanges = mutableListOf<Pair<Range<Int>, Range<Int>>>()
    var prevMatchedIndex: Pair<Int, Int>? = null
    newObjects.forEachIndexed { i, o ->
        val match = oldCompareObjects[compareBy(o)]
        if (match != null || i == newObjects.lastIndex) {
            val startIndex = prevMatchedIndex?.first ?: -1
            val endIndex = if (match != null) i else i + 1
            // Something was only added if the exclusive range has at least one point between the ends
            if (endIndex - startIndex > 1) {
                val oldStartIndex = prevMatchedIndex?.second ?: -1
                val oldEndIndex = match ?: (oldObjects.lastIndex + 1)
                addedIndexRanges.add(Range(startIndex, endIndex) to Range(oldStartIndex, oldEndIndex))
            }
        }
        if (match != null) prevMatchedIndex = i to match
    }
    return addedIndexRanges
}
