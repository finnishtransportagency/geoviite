package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PublicationLogDifferencesTest {

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
        // NOTE: The following assertion will break after GVT-2967 because the current
        // implementation is wrong
        assertEquals(newConvergingSegment.startM + newConvergingSegment.length, result.added[0].max, 0.1)
        assertEquals(newDivergingSegment.startM, result.added[1].min, 0.1)
        assertEquals(newDivergingSegment.startM + newDivergingSegment.length, result.added[1].max, 0.1)

        assertEquals(2, result.removed.size)
        assertEquals(oldFirstSegment.startM, result.removed[0].min, 0.1)
        // NOTE: The following assertion will break after GVT-2967 because the current
        // implementation is wrong
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
