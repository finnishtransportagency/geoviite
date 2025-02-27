package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.geocodingContext
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.singleSegmentWithInterpolatedPoints
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PublicationUtilsTest {
    @Test
    fun `Comparing values works`() {
        val a = 1
        val b = 2
        val change = compareChange({ true }, a, b, { it }, PropKey("test"))
        assertNotNull(change)
        assertEquals(change!!.value.oldValue, a)
        assertEquals(change.value.newValue, b)
        assertEquals(change.propKey.key.toString(), "test")
    }

    @Test
    fun `Returns null if predicate is false`() {
        val change = compareChange({ false }, 1, 2, { it }, PropKey("test"))
        assertNull(change)
    }

    @Test
    fun `Values are transformed`() {
        val change = compareChange({ true }, 1, 2, { it.toString() }, PropKey("test"))
        assertNotNull(change)
        assertEquals(change!!.value.oldValue, "1")
        assertEquals(change.value.newValue, "2")
    }

    @Test
    fun `Length comparison works properly`() {
        val changeTrue = compareLength(1.0, 2.0, 0.2, { it }, PropKey("test"))
        val changeFalse = compareLength(1.0, 1.1, 0.2, { it }, PropKey("test"))
        assertNotNull(changeTrue)
        assertNull(changeFalse)
    }

    @Test
    fun `compareChangeValues works`() {
        val changeTrue = compareChangeValues(Change(1, 2), { it }, PropKey("test"))
        val changeFalse = compareChangeValues(Change(1, 1), { it }, PropKey("test"))
        assertNotNull(changeTrue)
        assertNull(changeFalse)
    }

    @Test
    fun `formatLocation works`() {
        val location = Point(1.0, 2.0001)
        val formatted = formatLocation(location)
        assertEquals(formatted, "1.000 E, 2.000 N")
    }

    @Test
    fun `summarizeAlignmentChanges detects change to track geometry`() {
        val oldAlignment =
            alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0)))
        val newAlignment =
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.2), Point(2.0, 1.4), Point(3.0, 1.2), Point(4.0, 0.0)))

        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldAlignment, newAlignment)
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals(2.0, row.changedLengthM)
        assertEquals(row.maxDistance, 1.4, 0.1)
    }

    @Test
    fun `summarizeAlignmentChanges detects multiple changes based on segment identity match`() {
        val oldAlignment =
            alignment(
                ((0..4).map { segmentIx ->
                    segment(*(0..4).map { pointIx -> Point(segmentIx * 4.0 + pointIx * 1.0, 0.0) }.toTypedArray())
                })
            )
        val newAlignment =
            alignment(
                oldAlignment.segments[0],
                segment(Point(4.0, 0.0), Point(5.0, 1.2), Point(6.0, 1.5), Point(7.0, 1.2), Point(8.0, 0.0)),
                oldAlignment.segments[2],
                segment(Point(12.0, 0.0), Point(13.0, 1.2), Point(15.0, 2.0)),
                segment(Point(15.0, 2.0), Point(16.0, 3.0), Point(17.0, 4.0), Point(18.0, 5.0)),
            )

        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldAlignment, newAlignment)
        assertEquals(2, result.size)
        assertEquals(1.4, result[0].maxDistance, 0.1)
        assertEquals(2.0, result[0].changedLengthM)
        assertEquals(5.0, result[1].maxDistance, 0.1)
        assertEquals(5.0, result[1].changedLengthM)
    }

    @Test
    fun `summarizeAlignmentChanges detects multiple changes in a single segment`() {
        val oldAlignment = alignment(segment(Point(0.0, 0.0), Point(60.0, 0.0)))
        val newAlignment =
            alignment(
                singleSegmentWithInterpolatedPoints(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                    Point(10.0, 2.0),
                    Point(15.0, 0.0),
                    Point(30.0, 0.0),
                    Point(40.0, 3.0),
                    Point(50.0, 0.0),
                    Point(60.0, 0.0),
                )
            )

        val result =
            summarizeAlignmentChanges(
                xAxisGeocodingContext(),
                oldAlignment,
                alignment(segment(toSegmentPoints(to3DMPoints(newAlignment.allSegmentPoints.toList())))),
            )
        assertEquals(2, result.size)
        assertEquals(2.0, result[0].maxDistance, 0.1)
        assertEquals(4.0, result[0].changedLengthM)
        assertEquals(3.0, result[1].maxDistance, 0.1)
        assertEquals(12.0, result[1].changedLengthM)
    }

    private fun xAxisGeocodingContext() = geocodingContext((0..60).map { x -> Point(x.toDouble(), 0.0) })

    @Test
    fun `getAddedIndexRanges works`() {
        // Nothing found when lists are the same
        assertEquals(
            emptyList<Pair<Range<Int>, Range<Int>>>(),
            getAddedIndexRanges(listOf(1, 2, 3, 4, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
        // Swap 1 old for 2 new
        assertEquals(
            listOf(Range(2, 3) to Range(2, 2)),
            getAddedIndexRanges(listOf(1, 2, 3, 4, 5), listOf(1, 2, 0, 5)) { it },
        )
        // Swap 2 old for 1 new
        assertEquals(
            listOf(Range(2, 2) to Range(2, 3)),
            getAddedIndexRanges(listOf(1, 2, 0, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
        // Add one to the end: the old index is over-indexing to indicate past the end of the list
        assertEquals(
            listOf(Range(4, 4) to Range(4, 4)),
            getAddedIndexRanges(listOf(1, 2, 3, 4, 5), listOf(1, 2, 3, 4)) { it },
        )
        // Add one to the start: the old index is under-indexing to indicate before the start of the list
        assertEquals(
            listOf(Range(0, 0) to Range(-1, -1)),
            getAddedIndexRanges(listOf(1, 2, 3, 4, 5), listOf(2, 3, 4, 5)) { it },
        )
        // The function only seeks adds: nothing returned for removals
        assertEquals(
            emptyList<Pair<Range<Int>, Range<Int>>>(),
            getAddedIndexRanges(listOf(1, 2, 4, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
        // Alter and add to end
        assertEquals(
            listOf(Range(3, 4) to Range(3, 3)),
            getAddedIndexRanges(listOf(1, 2, 3, 6, 7), listOf(1, 2, 3, 4)) { it },
        )
        // Alter and add to beginning
        assertEquals(
            listOf(Range(0, 1) to Range(0, 0)),
            getAddedIndexRanges(listOf(-1, -2, 2, 3, 4, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
    }

    @Test
    fun `getChangedGeometryRanges() finds nothing from identical segments`() {
        val segments =
            listOf(
                segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0)),
                segment(Point(3.0, 0.0), Point(4.0, 0.0), Point(5.0, 0.0), startM = 3.0),
            )
        val changes = getChangedGeometryRanges(segments, segments)

        assertEquals(0, changes.added.size)
        assertEquals(0, changes.removed.size)
    }

    @Test
    fun `getChangedGeometryRanges() finds multiple changed ranges`() {
        val commonSegment = segment(Point(20.0, 0.0), Point(30.0, 0.0))

        val oldFirstSegment = segment(Point(0.0, 1.0), Point(10.0, 1.0), startM = 0.0)
        val oldConvergingSegment =
            segment(Point(10.0, 1.0), Point(15.0, 0.0), Point(20.0, 0.0), startM = oldFirstSegment.length)
        val commonSegmentOfOld = commonSegment.copy(startM = oldConvergingSegment.startM + oldConvergingSegment.length)
        val oldDivergingSegment =
            segment(
                Point(30.0, 0.0),
                Point(40.0, 1.0),
                Point(50.0, 1.0),
                startM = commonSegmentOfOld.startM + commonSegmentOfOld.length,
            )
        val oldSegments = listOf(oldFirstSegment, oldConvergingSegment, commonSegmentOfOld, oldDivergingSegment)

        val newFirstSegment = segment(Point(0.0, 0.0), Point(10.0, 0.0), startM = 0.0)
        val newConvergingSegment = segment(Point(10.0, 0.0), Point(20.0, 0.0), startM = newFirstSegment.length)
        val newCommonSegment = commonSegment.copy(startM = newConvergingSegment.startM + newConvergingSegment.length)
        val newDivergingSegment =
            segment(
                Point(30.0, 0.0),
                Point(40.0, 0.0),
                Point(50.0, 0.0),
                startM = newCommonSegment.startM + newCommonSegment.length,
            )
        val newSegments = listOf(newFirstSegment, newConvergingSegment, newCommonSegment, newDivergingSegment)

        val result = getChangedGeometryRanges(newSegments, oldSegments)
        assertEquals(2, result.added.size)
        assertEquals(newFirstSegment.startM, result.added[0].min, 0.1)
        // NOTE: The following assertion will break after GVT-2967 because the current implementation is wrong
        assertEquals(newConvergingSegment.startM + newConvergingSegment.length, result.added[0].max, 0.1)
        assertEquals(newDivergingSegment.startM, result.added[1].min, 0.1)
        assertEquals(newDivergingSegment.startM + newDivergingSegment.length, result.added[1].max, 0.1)

        assertEquals(2, result.removed.size)
        assertEquals(oldFirstSegment.startM, result.removed[0].min, 0.1)
        // NOTE: The following assertion will break after GVT-2967 because the current implementation is wrong
        assertEquals(oldConvergingSegment.startM + oldConvergingSegment.length, result.removed[0].max, 0.1)
        assertEquals(oldDivergingSegment.startM, result.removed[1].min, 0.1)
        assertEquals(oldDivergingSegment.startM + oldDivergingSegment.length, result.removed[1].max, 0.1)
    }

    @Test
    fun `getChangedGeometryRanges() finds added ranges`() {
        val commonSegment = segment(Point(0.0, 0.0), Point(1.0, 0.0))
        val oldSegments = listOf(commonSegment)
        val newSegments = listOf(commonSegment, segment(Point(1.0, 0.0), Point(2.0, 0.0), startM = 1.0))

        val result = getChangedGeometryRanges(newSegments, oldSegments)
        assertEquals(1, result.added.size)
        assertEquals(1.0, result.added[0].min, 0.1)
        assertEquals(2.0, result.added[0].max, 0.1)
        assertEquals(0, result.removed.size)
    }

    @Test
    fun `getChangedGeometryRanges() finds removed ranges`() {
        val commonSegment = segment(Point(0.0, 0.0), Point(1.0, 0.0))
        val oldSegments = listOf(commonSegment)
        val newSegments = listOf(commonSegment, segment(Point(1.0, 0.0), Point(2.0, 0.0), startM = 1.0))

        val result = getChangedGeometryRanges(oldSegments, newSegments)
        assertEquals(0, result.added.size)
        assertEquals(1, result.removed.size)
        assertEquals(1.0, result.removed[0].min, 0.1)
        assertEquals(2.0, result.removed[0].max, 0.1)
    }
}
