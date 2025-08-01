package fi.fta.geoviite.infra.math

import kotlin.math.PI
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PointTest {

    @Test
    fun pointInDirectionFromOriginWorks() {
        assertApproximatelyEquals(Point(1.0, 0.0), pointInDirection(1.0, 0.0))
        assertApproximatelyEquals(Point(0.0, 1.0), pointInDirection(1.0, 0.5 * PI))
        assertApproximatelyEquals(Point(-1.0, 0.0), pointInDirection(1.0, PI))
        assertApproximatelyEquals(Point(0.0, -1.0), pointInDirection(1.0, 1.5 * PI))
        assertApproximatelyEquals(Point(1.0, 0.0), pointInDirection(1.0, 2 * PI))

        assertApproximatelyEquals(Point(sqrt(0.5), sqrt(0.5)), pointInDirection(1.0, 0.25 * PI))
        assertApproximatelyEquals(Point(-sqrt(0.5), sqrt(0.5)), pointInDirection(1.0, 0.75 * PI))
        assertApproximatelyEquals(Point(-sqrt(0.5), -sqrt(0.5)), pointInDirection(1.0, 1.25 * PI))
        assertApproximatelyEquals(Point(sqrt(0.5), -sqrt(0.5)), pointInDirection(1.0, 1.75 * PI))

        assertApproximatelyEquals(Point(2.5, 0.0), pointInDirection(2.5, 0.0))
        assertApproximatelyEquals(Point(0.0, 10.1), pointInDirection(10.1, 0.5 * PI))
    }

    @Test
    fun pointInDirectionFromOtherPointWorks() {
        val startPoint = Point(4.0, 3.0)
        assertApproximatelyEquals(startPoint + Point(1.0, 0.0), pointInDirection(startPoint, 1.0, 0.0))
        assertApproximatelyEquals(startPoint + Point(0.0, 1.0), pointInDirection(startPoint, 1.0, 0.5 * PI))
        assertApproximatelyEquals(startPoint + Point(-1.0, 0.0), pointInDirection(startPoint, 1.0, PI))
        assertApproximatelyEquals(startPoint + Point(0.0, -1.0), pointInDirection(startPoint, 1.0, 1.5 * PI))
        assertApproximatelyEquals(startPoint + Point(1.0, 0.0), pointInDirection(startPoint, 1.0, 2 * PI))

        assertApproximatelyEquals(
            startPoint + Point(sqrt(0.5), sqrt(0.5)),
            pointInDirection(startPoint, 1.0, 0.25 * PI),
        )
        assertApproximatelyEquals(
            startPoint + Point(-sqrt(0.5), sqrt(0.5)),
            pointInDirection(startPoint, 1.0, 0.75 * PI),
        )
        assertApproximatelyEquals(
            startPoint + Point(-sqrt(0.5), -sqrt(0.5)),
            pointInDirection(startPoint, 1.0, 1.25 * PI),
        )
        assertApproximatelyEquals(
            startPoint + Point(sqrt(0.5), -sqrt(0.5)),
            pointInDirection(startPoint, 1.0, 1.75 * PI),
        )

        assertApproximatelyEquals(startPoint + Point(2.5, 0.0), pointInDirection(startPoint, 2.5, 0.0))
        assertApproximatelyEquals(startPoint + Point(0.0, 10.1), pointInDirection(startPoint, 10.1, 0.5 * PI))
    }

    @Test
    fun directionTowardsPointWorks() {
        val delta = 0.00001

        assertEquals(0.0, directionTowardsPoint(Point(1.0, 0.0)), delta)
        assertEquals(0.5 * PI, directionTowardsPoint(Point(0.0, 1.0)), delta)
        assertEquals(PI, directionTowardsPoint(Point(-1.0, 0.0)), delta)
        assertEquals(-0.5 * PI, directionTowardsPoint(Point(0.0, -1.0)), delta)

        assertEquals(0.25 * PI, directionTowardsPoint(Point(1.0, 1.0)), delta)
        assertEquals(0.75 * PI, directionTowardsPoint(Point(-1.0, 1.0)), delta)
        assertEquals(-0.75 * PI, directionTowardsPoint(Point(-1.0, -1.0)), delta)
        assertEquals(-0.25 * PI, directionTowardsPoint(Point(1.0, -1.0)), delta)

        assertEquals(0.0, directionTowardsPoint(Point(2.5, 0.0)), delta)
        assertEquals(0.5 * PI, directionTowardsPoint(Point(0.0, 10.1)), delta)
    }

    @Test
    fun rotateAroundOrigin() {
        assertApproximatelyEquals(Point(12.34, 45.67), rotateAroundOrigin(2 * PI, Point(12.34, 45.67)))
        assertApproximatelyEquals(Point(12.34, 45.67), rotateAroundOrigin(0.0, Point(12.34, 45.67)))
        assertApproximatelyEquals(Point(-12.34, -45.67), rotateAroundOrigin(PI, Point(12.34, 45.67)))
        assertApproximatelyEquals(Point(-45.67, 12.34), rotateAroundOrigin(PI / 2, Point(12.34, 45.67)))
        assertApproximatelyEquals(Point(45.67, -12.34), rotateAroundOrigin(3 * PI / 2, Point(12.34, 45.67)))
        assertApproximatelyEquals(Point(45.67, -12.34), rotateAroundOrigin(-PI / 2, Point(12.34, 45.67)))
    }

    @Test
    fun isSameWorks() {
        assertTrue(Point(1.12345, 2.12345).isSame(Point(1.12345, 2.12345), 0.0001))
        assertTrue(Point(1.12345, 2.12345).isSame(Point(1.12346, 2.12346), 0.0001))
        assertFalse(Point(1.12345, 2.12345).isSame(Point(1.12345, 2.12346), 0.000001))
        assertFalse(Point(1.12345, 2.12345).isSame(Point(1.12346, 2.12345), 0.000001))
        assertFalse(Point(1.12345, 2.12345).isSame(Point(1.12346, 2.12346), 0.000001))
    }
}

fun assertApproximatelyEquals(p1: IPoint, p2: IPoint, accuracy: Double = 0.0001) {
    assertEquals(p1.x, p2.x, accuracy, "The points should be near-equal: p1=$p1 p2=$p2 accuracy=$accuracy")
    assertEquals(p1.y, p2.y, accuracy, "The points should be near-equal: p1=$p1 p2=$p2 accuracy=$accuracy")
    if (p1 is IPoint3DM<*> && p2 is IPoint3DM<*>) {
        assertEquals(p1.m.distance, p2.m.distance, accuracy, "The points should be near-equal: p1=$p1 p2=$p2 accuracy=$accuracy")
    }
    if (p1 is IPoint3DZ && p2 is IPoint3DZ) {
        assertEquals(p1.z, p2.z, accuracy, "The points should be near-equal: p1=$p1 p2=$p2 accuracy=$accuracy")
    }
}
