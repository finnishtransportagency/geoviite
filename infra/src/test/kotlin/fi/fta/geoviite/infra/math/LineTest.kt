package fi.fta.geoviite.infra.math

import fi.fta.geoviite.infra.math.IntersectType.*
import kotlin.math.hypot
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val DOUBLE_CALC_ACCURACY = 0.00000000000001

class LineTest {

    @Test
    fun lineLengthWorks() {
        assertEquals(sqrt(2.0), lineLength(Point(0.0, 0.0), Point(1.0, 1.0)))
        assertEquals(sqrt(2.0), lineLength(Point(1.0, 1.0), Point(0.0, 0.0)))
        assertEquals(sqrt(2.0), lineLength(Point(-1.0, -1.0), Point(0.0, 0.0)))
        assertEquals(sqrt(2.0), lineLength(Point(0.0, 0.0), Point(-1.0, -1.0)))
        assertEquals(0.0, lineLength(Point(1.0, 1.0), Point(1.0, 1.0)))
        assertEquals(0.0, lineLength(Point(-1.0, -1.0), Point(-1.0, -1.0)))
        assertEquals(0.0, lineLength(Point(0.0, 0.0), Point(0.0, 0.0)))
    }

    @Test
    fun pointAlongLineWorks() {
        assertEquals(Point(3.0, 3.0), linePointAtDistance(Point(2.0, 2.0), Point(4.0, 4.0), sqrt(2.0)))
        assertEquals(Point(3.0, 3.0), linePointAtDistance(Point(4.0, 4.0), Point(2.0, 2.0), sqrt(2.0)))

        assertEquals(Point(-3.0, -3.0), linePointAtDistance(Point(-2.0, -2.0), Point(-4.0, -4.0), sqrt(2.0)))
        assertEquals(Point(-3.0, -3.0), linePointAtDistance(Point(-4.0, -4.0), Point(-2.0, -2.0), sqrt(2.0)))

        assertEquals(Point(1.0, 1.0), linePointAtDistance(Point(0.0, 0.0), Point(2.0, 2.0), sqrt(2.0)))
        assertEquals(Point(1.0, -1.0), linePointAtDistance(Point(0.0, 0.0), Point(2.0, -2.0), sqrt(2.0)))
        assertEquals(Point(-1.0, -1.0), linePointAtDistance(Point(0.0, 0.0), Point(-2.0, -2.0), sqrt(2.0)))
        assertEquals(Point(-1.0, 1.0), linePointAtDistance(Point(0.0, 0.0), Point(-2.0, 2.0), sqrt(2.0)))
    }

    @Test
    fun shouldReturnSlopeWith2GivenPoints() {
        val dummyPoint1 = Point(2.0, 2.0)
        val dummyPoint2 = Point(3.0, 3.0)
        val dummyPoint3 = Point(2.0, 2.0)
        val dummyPoint4 = Point(3.0, 4.0)
        val dummyPoint5 = Point(6.0, 4.0)
        val dummyPoint6 = Point(8.0, 2.0)
        val dummyPoint7 = Point(-2.0, -2.0)
        val dummyPoint8 = Point(-3.0, -3.0)
        val dummyPoint9 = Point(-2.0, -2.0)
        val dummyPoint10 = Point(-3.0, -4.0)
        val dummyPoint11 = Point(-6.0, -4.0)
        val dummyPoint12 = Point(-8.0, -2.0)
        val dummyPoint13 = Point(-4.0, -0.0)
        val dummyPoint14 = Point(2.0, 3.0)

        assertEquals(1.0, lineSlope(dummyPoint1, dummyPoint2))
        assertEquals(2.0, lineSlope(dummyPoint3, dummyPoint4))
        assertEquals(-1.0, lineSlope(dummyPoint5, dummyPoint6))
        assertEquals(1.0, lineSlope(dummyPoint7, dummyPoint8))
        assertEquals(2.0, lineSlope(dummyPoint9, dummyPoint10))
        assertEquals(-1.0, lineSlope(dummyPoint11, dummyPoint12))
        assertEquals(0.5, lineSlope(dummyPoint13, dummyPoint14))
    }

    @Test
    fun shouldReturnLineConstantWithStartPointAndSlope() {
        val point1 = Point(0.0, 15.0)
        val slope1 = -5.0
        val point2 = Point(2.0, 3.0)
        val slope2 = 0.5
        val point3 = Point(0.0, 5.0)
        val slope3 = -1.0
        val point4 = Point(10.0, 4.0)
        val slope4 = -0.5

        assertEquals(15.0, lineConstant(point1, slope1))
        assertEquals(2.0, lineConstant(point2, slope2))
        assertEquals(5.0, lineConstant(point3, slope3))
        assertEquals(9.0, lineConstant(point4, slope4))
    }

    @Test
    fun closestPointOnLineWorksAlongX() {
        val start = Point(1.0, 1.0)
        val end = Point(2.0, 1.0)

        assertEquals(Point(1.0, 1.0), closestPointOnLine(start, end, Point(0.0, 1.5)))
        assertEquals(Point(2.0, 1.0), closestPointOnLine(start, end, Point(3.0, 0.5)))

        assertEquals(Point(1.1, 1.0), closestPointOnLine(start, end, Point(1.1, 2.0)))
        assertEquals(Point(1.2, 1.0), closestPointOnLine(start, end, Point(1.2, 0.0)))
    }

    @Test
    fun closestPointOnLineWorksAlongY() {
        val start = Point(1.0, 1.0)
        val end = Point(1.0, 2.0)

        assertEquals(Point(1.0, 1.0), closestPointOnLine(start, end, Point(0.5, 0.0)))
        assertEquals(Point(1.0, 2.0), closestPointOnLine(start, end, Point(1.5, 3.0)))

        assertEquals(Point(1.0, 1.3), closestPointOnLine(start, end, Point(2.0, 1.3)))
        assertEquals(Point(1.0, 1.4), closestPointOnLine(start, end, Point(0.0, 1.4)))
    }

    @Test
    fun pointDistanceToLineWorks() {
        val start = Point(1.0, 1.0)
        val end = Point(2.0, 2.0)

        assertEquals(0.0, pointDistanceToLine(start, end, Point(1.0, 1.0)))
        assertEquals(0.0, pointDistanceToLine(start, end, Point(2.0, 2.0)))
        assertEquals(0.0, pointDistanceToLine(start, end, Point(1.5, 1.5)))

        assertEquals(hypot(0.5, 0.5), pointDistanceToLine(start, end, Point(1.0, 2.0)), DOUBLE_CALC_ACCURACY)
        assertEquals(hypot(0.5, 0.5), pointDistanceToLine(start, end, Point(2.0, 1.0)), DOUBLE_CALC_ACCURACY)

        assertEquals(hypot(0.5, 0.5), pointDistanceToLine(start, end, Point(2.5, 2.5)), DOUBLE_CALC_ACCURACY)
        assertEquals(hypot(0.5, 0.5), pointDistanceToLine(start, end, Point(0.5, 0.5)), DOUBLE_CALC_ACCURACY)

        assertEquals(hypot(0.5, 0.5), pointDistanceToLine(start, end, Point(1.5, 2.5)), DOUBLE_CALC_ACCURACY)
        assertEquals(hypot(0.5, 0.5), pointDistanceToLine(start, end, Point(2.5, 1.5)), DOUBLE_CALC_ACCURACY)
    }

    @Test
    fun lineIntersectionWorks() {
        for (xIntersection in -5..5) {
            for (yIntersection in -5..5) {
                val xi = xIntersection.toDouble()
                val yi = yIntersection.toDouble()
                assertIntersection(
                    Point(xi, yi),
                    inSegment1 = WITHIN,
                    inSegment2 = WITHIN,
                    lineIntersection(
                        Point(xi - 10.0, yi),
                        Point(xi + 10.0, yi),
                        Point(xi, yi - 10.0),
                        Point(xi, yi + 10.0),
                    )!!,
                )
                assertIntersection(
                    Point(xi, yi),
                    inSegment1 = WITHIN,
                    inSegment2 = WITHIN,
                    lineIntersection(
                        Point(xi - 4.0, yi - 3.0),
                        Point(xi + 4.0, yi + 3.0),
                        Point(xi + 2.0, yi - 4.0),
                        Point(xi - 2.0, yi + 4.0),
                    )!!,
                )
                assertIntersection(
                    Point(xi, yi),
                    inSegment1 = AFTER,
                    inSegment2 = WITHIN,
                    lineIntersection(
                        Point(xi - 10.0, yi),
                        Point(xi - 1.0, yi),
                        Point(xi, yi - 10.0),
                        Point(xi, yi + 10.0),
                    )!!,
                )
                assertIntersection(
                    Point(xi, yi),
                    inSegment1 = BEFORE,
                    inSegment2 = WITHIN,
                    lineIntersection(
                        Point(xi - 1.0, yi),
                        Point(xi - 10.0, yi),
                        Point(xi, yi - 10.0),
                        Point(xi, yi + 10.0),
                    )!!,
                )
                assertIntersection(
                    Point(xi, yi),
                    inSegment1 = WITHIN,
                    inSegment2 = AFTER,
                    lineIntersection(
                        Point(xi - 10.0, yi),
                        Point(xi + 10.0, yi),
                        Point(xi, yi + 10.0),
                        Point(xi, yi + 1.0),
                    )!!,
                )
                assertIntersection(
                    Point(xi, yi),
                    inSegment1 = WITHIN,
                    inSegment2 = BEFORE,
                    lineIntersection(
                        Point(xi - 10.0, yi),
                        Point(xi + 10.0, yi),
                        Point(xi, yi + 1.0),
                        Point(xi, yi + 10.0),
                    )!!,
                )
            }
        }
    }

    private fun assertIntersection(
        point: Point,
        inSegment1: IntersectType,
        inSegment2: IntersectType,
        intersection: Intersection,
    ) {
        assertEquals(point, intersection.point)
        assertEquals(inSegment1, intersection.inSegment1)
        assertEquals(inSegment2, intersection.inSegment2)
    }
}
