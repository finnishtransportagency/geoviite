package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SEED = 123321L

class TrackLayoutTest {

    private val rand = Random(SEED)

    @BeforeEach
    fun initRandom() {
        // Fix random to a specific state for each test
        rand.setSeed(SEED)
    }

    @Test
    fun nothingIsFilteredWhenContentIsInBounds() {
        val alignment = alignment(segment(10, 10.0, 20.0, 10.0, 20.0))
        val filtered = filterSegments(alignment, BoundingBox(5.0..25.0, 5.0..25.0))
        assertEquals(alignment.segments, filtered)
    }

    @Test
    fun segmentsAreFilteredWhenOutOfBounds() {
        val alignment = alignment(segment(10, 10.0, 20.0, 10.0, 20.0))
        val filtered = filterSegments(alignment, BoundingBox(0.0..5.0, 0.0..5.0))
        assertEquals(listOf<LayoutSegment>(), filtered)
    }

    @Test
    fun outOfBoundsSegmentsAreFilteredAndRestIsLeftWhenBoundingBoxCoversPart() {
        val seg1 = segment(10, 10.0, 20.1, 10.0, 20.1)
        val seg2 = segment(10, 20.1, 30.0, 20.1, 30.0)
        val alignment = alignment(seg1, seg2)
        val filtered = filterSegments(alignment, BoundingBox(10.0..20.0, 10.0..20.0))
        assertNotNull(filtered)
        assertEquals(listOf(seg1), filtered)
    }

    @Test
    fun alignmentIsFilteredWhenAllSegmentsGetFilteredDespiteOwnBounds() {
        val seg1 = segment(50, 0.0, 10.0, 0.0, 5.0)
        val seg2 = segment(50, 10.0, 20.0, 5.0, 15.0)
        val seg3 = segment(50, 20.0, 25.0, 15.0, 25.0)

        val alignment = alignment(seg1, seg2, seg3)
        val upperLeftBbox = BoundingBox(-10.0..5.0, 10.0..20.0)
        assertTrue(upperLeftBbox.intersects(alignment.boundingBox))
        assertFalse(upperLeftBbox.intersects(seg1.boundingBox))
        assertFalse(upperLeftBbox.intersects(seg2.boundingBox))
        assertFalse(upperLeftBbox.intersects(seg3.boundingBox))
        assertEquals(listOf<LayoutSegment>(), filterSegments(alignment, upperLeftBbox))
    }

    @Test
    fun segmentSimplificationWorks() {
        val segment = segment(50, -10.0, 10.0, -10.0, 10.0)
        assertEquals(50, segment.points.size)
        assertEquals(segment.points, simplify(1, listOf(segment)).first().points)

        val simplified2 = simplify(2, listOf(segment))
        assertEquals(1, simplified2.size)
        assertEquals(26, simplified2.first().points.size)
        assertEquals(segment.points.first(), simplified2.first().points.first())
        assertEquals(segment.points.last(), simplified2.first().points.last())

        val simplified5 = simplify(5, listOf(segment))
        assertEquals(1, simplified5.size)
        assertEquals(11, simplified5.first().points.size)
        assertEquals(segment.points.first(), simplified5.first().points.first())
        assertEquals(segment.points.last(), simplified5.first().points.last())

        val simplified10 = simplify(10, listOf(segment))
        assertEquals(1, simplified10.size)
        assertEquals(6, simplified10.first().points.size)
        assertEquals(segment.points.first(), simplified10.first().points.first())
        assertEquals(segment.points.last(), simplified10.first().points.last())
    }

    @Test
    fun slicingSegmentWorks() {
        val original = segment(10, -10.0, 10.0, -10.0, 10.0)

        val startSliceStart = 123.111
        val startSlice = original.slice(0, 2, startSliceStart)!!
        assertEquals(3, startSlice.points.size)
        assertApproximatelyEquals(original.points[0].copy(m = startSliceStart), startSlice.points[0])
        assertApproximatelyEquals(original.points[2].copy(m = original.points[2].m + startSliceStart), startSlice.points[startSlice.points.lastIndex])
        assertEquals(startSliceStart, startSlice.start, 0.0001)
        assertEquals(original.points[2].m, startSlice.length, 0.0001)

        val endSliceStart = 123.222
        val endSlice = original.slice(8, 9, endSliceStart)!!
        assertEquals(2, endSlice.points.size)
        assertApproximatelyEquals(original.points[8].copy(m = endSliceStart), endSlice.points[0])
        assertApproximatelyEquals(
            original.points[9].copy(m = endSliceStart + original.length - original.points[8].m),
            endSlice.points[endSlice.points.lastIndex],
        )
        assertEquals(endSliceStart, endSlice.start, 0.0001)
        assertEquals(original.points[9].m - original.points[8].m, endSlice.length, 0.0001)

        val midSliceStart = 123.333
        val midSlice = original.slice(1, 8, midSliceStart)!!
        assertEquals(8, midSlice.points.size)
        assertApproximatelyEquals(original.points[1].copy(m = midSliceStart), midSlice.points[0])
        assertApproximatelyEquals(
            original.points[8].copy(m = midSliceStart + original.points[8].m - original.points[1].m),
            midSlice.points[midSlice.points.lastIndex]
        )
        assertEquals(midSliceStart, midSlice.start, 0.0001)
        assertEquals(original.points[8].m - original.points[1].m, midSlice.length, 0.0001)
    }

    @Test
    fun nonContinuousSegmentsAreNotAllowed() {
        assertThrows<IllegalArgumentException> {
            alignment(
                segment(toTrackLayoutPoints(
                    Point(0.0, 0.0),
                    Point(0.0, 100.0),
                )),
                segment(toTrackLayoutPoints(
                    Point(1.0, 100.0),
                    Point(1.0, 200.0),
                )),
            )
        }
    }
}
