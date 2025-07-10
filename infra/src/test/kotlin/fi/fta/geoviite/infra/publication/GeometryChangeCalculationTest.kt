package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.assertEquals
import fi.fta.geoviite.infra.tracklayout.calculateSegmentMValues
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeometryChangeCalculationTest {
    @Test
    fun `getAddedIndexRangesExclusive gives the correct ranges`() {
        // Nothing found when lists are the same
        assertEquals(
            emptyList<Pair<Range<Int>, Range<Int>>>(),
            getAddedIndexRangesExclusive(listOf(1, 2, 3, 4, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
        // Add 1
        assertEquals(listOf(Range(0, 2) to Range(0, 1)), getAddedIndexRangesExclusive(listOf(1, 2), listOf(1)) { it })
        // Swap 1 old for 2 new
        assertEquals(
            listOf(Range(1, 4) to Range(1, 3)),
            getAddedIndexRangesExclusive(listOf(1, 2, 3, 4, 5), listOf(1, 2, 0, 5)) { it },
        )
        // Swap 2 old for 1 new
        assertEquals(
            listOf(Range(1, 3) to Range(1, 4)),
            getAddedIndexRangesExclusive(listOf(1, 2, 0, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
        // Add one to the end
        assertEquals(
            listOf(Range(3, 5) to Range(3, 4)),
            getAddedIndexRangesExclusive(listOf(1, 2, 3, 4, 5), listOf(1, 2, 3, 4)) { it },
        )
        // Add one to the start
        assertEquals(
            listOf(Range(-1, 1) to Range(-1, 0)),
            getAddedIndexRangesExclusive(listOf(1, 2, 3, 4, 5), listOf(2, 3, 4, 5)) { it },
        )
        // The function only seeks adds: nothing returned for removals
        assertEquals(
            emptyList<Pair<Range<Int>, Range<Int>>>(),
            getAddedIndexRangesExclusive(listOf(1, 2, 4, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
        // Alter and add to end
        assertEquals(
            listOf(Range(2, 5) to Range(2, 4)),
            getAddedIndexRangesExclusive(listOf(1, 2, 3, 6, 7), listOf(1, 2, 3, 4)) { it },
        )
        // Alter and add to beginning
        assertEquals(
            listOf(Range(-1, 2) to Range(-1, 1)),
            getAddedIndexRangesExclusive(listOf(-1, -2, 2, 3, 4, 5), listOf(1, 2, 3, 4, 5)) { it },
        )
        // Swap everyghing
        assertEquals(
            listOf(Range(-1, 4) to Range(-1, 3)),
            getAddedIndexRangesExclusive(listOf(-1, -2, -3, -4), listOf(1, 2, 3)) { it },
        )
    }

    @Test
    fun `getChangedGeometryRanges() finds nothing from identical segments`() {
        val segments =
            listOf(
                    segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0)),
                    segment(Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0)),
                )
                .let { it.zip(calculateSegmentMValues<LocationTrackM>(it)) }
        val changes = getChangedGeometryRanges(segments, segments)

        assertEquals(0, changes.added.size)
        assertEquals(0, changes.removed.size)
    }

    @Test
    fun `getChangedGeometryRanges() finds multiple changed ranges`() {
        val commonSegment = segment(Point(20.0, 0.0), Point(30.0, 0.0))

        val oldFirstSegment = segment(Point(0.0, 1.0), Point(10.0, 1.0))
        val oldConvergingSegment = segment(Point(10.0, 1.0), Point(15.0, 0.0), Point(20.0, 0.0))
        val oldDivergingSegment = segment(Point(30.0, 0.0), Point(40.0, 0.0), Point(50.0, 1.0))
        val oldSegments =
            listOf(oldFirstSegment, oldConvergingSegment, commonSegment, oldDivergingSegment).let {
                it.zip(calculateSegmentMValues<LocationTrackM>(it))
            }

        val newFirstSegment = segment(Point(0.0, 0.0), Point(10.0, 0.0))
        val newConvergingSegment = segment(Point(10.0, 0.0), Point(15.0, 0.0), Point(20.0, 0.0))
        val newDivergingSegment = segment(Point(30.0, 0.0), Point(40.0, 0.0), Point(50.0, 0.0))
        val newSegments =
            listOf(newFirstSegment, newConvergingSegment, commonSegment, newDivergingSegment).let {
                it.zip(calculateSegmentMValues<LocationTrackM>(it))
            }

        val result = getChangedGeometryRanges(newSegments, oldSegments)
        assertEquals(2, result.added.size)
        assertDoubleRange(Range(0.0, 15.0).map(::LineM), result.added[0])
        assertDoubleRange(Range(40.0, 50.0).map(::LineM), result.added[1])

        assertEquals(2, result.removed.size)
        assertDoubleRange(
            Range(0.0, 10.0 + lineLength(Point(10.0, 1.0), Point(15.0, 0.0))).map(::LineM),
            result.removed[0]
        )
        assertDoubleRange(Range(oldSegments[3].second.min + 10.0, oldSegments[3].second.max), result.removed[1])
    }

    @Test
    fun `getChangedGeometryRanges() finds added ranges`() {
        val commonSegment = segment(Point(0.0, 0.0), Point(1.0, 0.0))
        val oldSegments = listOf(commonSegment).let { it.zip(calculateSegmentMValues<LocationTrackM>(it)) }
        val newSegments = listOf(
            commonSegment,
            segment(Point(1.0, 0.0), Point(2.0, 0.0))
        ).let { it.zip(calculateSegmentMValues<LocationTrackM>(it)) }

        val result = getChangedGeometryRanges(newSegments, oldSegments)
        assertEquals(1, result.added.size)
        assertDoubleRange(Range(1.0, 2.0).map(::LineM), result.added[0])
        assertEquals(0, result.removed.size)
    }

    @Test
    fun `getChangedGeometryRanges() finds removed ranges`() {
        val commonSegment = segment(Point(0.0, 0.0), Point(1.0, 0.0))
        val oldSegments = listOf(commonSegment).let { it.zip(calculateSegmentMValues<LocationTrackM>(it)) }
        val newSegments = listOf(
            commonSegment,
            segment(Point(1.0, 0.0), Point(2.0, 0.0))
        ).let { it.zip(calculateSegmentMValues<LocationTrackM>(it)) }

        val result = getChangedGeometryRanges(oldSegments, newSegments)
        assertEquals(0, result.added.size)
        assertEquals(1, result.removed.size)
        assertDoubleRange(Range(1.0, 2.0).map(::LineM), result.removed[0])
    }

    private fun assertDoubleRange(expected: Range<LineM<LocationTrackM>>, actual: Range<LineM<LocationTrackM>>, delta: Double = 0.001) {
        assertEquals(expected.min, actual.min, delta) {
            "Double range mismatch (min different): expected=${expected} actual=${actual.min}"
        }
        assertEquals(expected.max, actual.max, delta) {
            "Double range mismatch (max different): expected=${expected} actual=${actual.min}"
        }
    }
}
