package fi.fta.geoviite.infra.math

import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BoundingBoxTest {

    @Test
    fun boxCornersAreCorrect() {
        assertEquals(
            listOf(Point(1.0, 3.0), Point(2.0, 3.0), Point(2.0, 4.0), Point(1.0, 4.0)),
            BoundingBox(1.0..2.0, 3.0..4.0).corners,
        )
    }

    @Test
    fun containsWorks() {
        val box = BoundingBox(1.0..2.0, 3.0..4.0)

        assertTrue(box.contains(Point(1.5, 3.5)))
        assertTrue(box.contains(Point(1.0, 3.0)))
        assertTrue(box.contains(Point(1.0, 4.0)))
        assertTrue(box.contains(Point(2.0, 3.0)))
        assertTrue(box.contains(Point(2.0, 4.0)))

        assertFalse(box.contains(Point(0.5, 3.5)))
        assertFalse(box.contains(Point(1.5, 2.5)))
        assertFalse(box.contains(Point(2.5, 3.5)))
        assertFalse(box.contains(Point(1.5, 4.5)))
        assertFalse(box.contains(Point(2.5, 4.5)))
    }

    @Test
    fun polygonContainsRecognizesPolyInsideBox() {
        val box = BoundingBox(1.0..2.0, 3.0..4.0)
        val points = listOf(Point(1.1, 3.1), Point(1.5, 3.2), Point(1.4, 3.8), Point(1.05, 3.7))
        assertTrue(box.intersects(points))
    }

    @Test
    fun polygonContainsRecognizesPolyAroundBox() {
        val box = BoundingBox(1.0..2.0, 3.0..4.0)
        val points = listOf(Point(0.5, 2.5), Point(2.5, 2.5), Point(2.5, 4.5), Point(0.5, 4.5))
        assertTrue(box.intersects(points))
    }

    @Test
    fun polygonContainsRecognizesLineThroughBox() {
        val box = BoundingBox(1.0..2.0, 3.0..4.0)
        val points = listOf(Point(0.9, 2.9), Point(2.1, 4.1))
        assertTrue(box.intersects(points))
    }

    @Test
    fun polygonContainsWorksWithOnePoint() {
        val box = BoundingBox(1.0..2.0, 3.0..4.0)
        val points = listOf(Point(1.5, 3.5))
        assertTrue(box.intersects(points))
    }

    @Test
    fun polygonContainsRecognizesPolyPartiallyIntersectingWithBox() {
        val box = BoundingBox(1.0..2.0, 3.0..4.0)
        val points = listOf(Point(0.5, 2.5), Point(2.5, 2.5), Point(1.5, 3.5))
        assertTrue(box.intersects(points))
    }

    @Test
    fun polygonContainsRecognizesPolyOutsideBox() {
        val box = BoundingBox(1.0..2.0, 3.0..4.0)
        val points = listOf(Point(0.1, 3.1), Point(0.5, 3.2), Point(0.4, 3.8), Point(0.05, 3.7))
        assertFalse(box.intersects(points))
    }

    @Test
    fun createFromPointListOfOne() {
        val bbox = boundingBoxAroundPoints(listOf(Point(10.0, 10.0)))
        assertApproximatelyEquals(Point(10.0, 10.0), bbox.min)
        assertApproximatelyEquals(Point(10.0, 10.0), bbox.max)
    }

    @Test
    fun createFromPointList() {
        val bbox = boundingBoxAroundPoints(listOf(Point(10.0, -10.0), Point(-20.0, 20.0)))
        assertApproximatelyEquals(Point(-20.0, -10.0), bbox.min)
        assertApproximatelyEquals(Point(10.0, 20.0), bbox.max)
    }

    @Test
    fun expandBoundingBox() {
        val bbox = boundingBoxAroundPoints(listOf(Point(10.0, 10.0), Point(20.0, 30.0))) + 5.0
        assertApproximatelyEquals(Point(5.0, 5.0), bbox.min)
        assertApproximatelyEquals(Point(25.0, 35.0), bbox.max)
    }

    @Test
    fun expandBoundingBoxByRatio() {
        val bbox = boundingBoxAroundPoints(listOf(Point(10.0, 10.0), Point(20.0, 30.0))) * 1.5
        assertApproximatelyEquals(Point(10 - 2.5, 10 - 5.0), bbox.min)
        assertApproximatelyEquals(Point(20 + 2.5, 30 + 5.0), bbox.max)
    }

    @Test
    fun readCenter() {
        val bbox = BoundingBox(Point(0.0, 0.0), Point(10.0, 30.0))
        assertEquals(Point(5.0, 15.0), bbox.center)
    }

    @Test
    fun centerAt() {
        val bbox = BoundingBox(Point(0.0, 0.0), Point(10.0, 10.0)).centerAt(Point(20.0, 30.0))

        assertEquals(Point(15.0, 25.0), bbox.min)
        assertEquals(Point(25.0, 35.0), bbox.max)
        assertEquals(Point(20.0, 30.0), bbox.center)
    }

    @Test
    fun minimumDistance() {
        // Inclusion, symmetrically: Always 0, because this is a minimum distance between entire
        // bounding boxes, not their perimeters
        assertEquals(
            0.0,
            BoundingBox(Point(0.0, 0.0), Point(10.0, 10.0))
                .minimumDistance(BoundingBox(Point(1.0, 1.0), Point(9.0, 9.0))),
        )
        assertEquals(
            0.0,
            BoundingBox(Point(1.0, 1.0), Point(9.0, 9.0))
                .minimumDistance(BoundingBox(Point(0.0, 0.0), Point(10.0, 10.0))),
        )

        // axial distance
        assertEquals(
            1.0,
            BoundingBox(Point(0.0, 0.0), Point(1.0, 1.0)).minimumDistance(BoundingBox(Point(2.0, 0.0), Point(3.0, 1.0))),
        )
        assertEquals(
            1.0,
            BoundingBox(Point(0.0, 11.0), Point(10.0, 20.0))
                .minimumDistance(BoundingBox(Point(0.0, 0.0), Point(10.0, 10.0))),
        )

        // diagonal distance
        assertEquals(
            sqrt(2.0),
            BoundingBox(Point(0.0, 0.0), Point(1.0, 1.0))
                .minimumDistance(BoundingBox(Point(2.0, 2.0), Point(3.0, 3.0))),
            0.00001,
        )

        // intersection
        assertEquals(
            0.0,
            BoundingBox(Point(0.0, 0.0), Point(3.0, 3.0)).minimumDistance(BoundingBox(Point(1.0, 1.0), Point(5.0, 2.0))),
        )

        // touch axially or diagonally
        assertEquals(
            0.0,
            BoundingBox(Point(0.0, 0.0), Point(1.0, 1.0)).minimumDistance(BoundingBox(Point(1.0, 0.0), Point(2.0, 1.0))),
        )
        assertEquals(
            0.0,
            BoundingBox(Point(0.0, 0.0), Point(1.0, 1.0)).minimumDistance(BoundingBox(Point(1.0, 1.0), Point(2.0, 2.0))),
        )
    }
}
