package fi.fta.geoviite.infra.math

import kotlin.math.PI
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CircleTest {

    @Test
    fun circleArcAngleWorks() {
        val delta = 0.00001

        assertEquals(0.0, circleArcAngle(1.0, 0.0), delta)
        assertEquals(0.5 * PI, circleArcAngle(1.0, sqrt(2.0)), delta)
        assertEquals(PI, circleArcAngle(1.0, 2.0), delta)

        assertEquals(0.5 * PI, circleArcAngle(5.0, sqrt(50.0)), delta)
    }

    @Test
    fun circleArcLengthWorks() {
        val delta = 0.00001

        assertEquals(0.0, circleArcLength(1.0, 0.0), delta)
        assertEquals(0.5 * PI, circleArcLength(1.0, sqrt(2.0)), delta)
        assertEquals(PI, circleArcLength(1.0, 2.0), delta)

        assertEquals(2.5 * PI, circleArcLength(5.0, sqrt(50.0)), delta)
    }

    @Test
    fun circleSubArcLengthWorks() {
        val radius = 4.0
        val angle = 2.0944
        val expectedSubLength = 8.38
        assertEquals(expectedSubLength, circleSubArcLength(radius, angle), 0.01)
    }

    @Test
    fun arcYatXWorksAtEdges() {
        val delta = 0.00000001
        val radius = 10.0

        assertEquals(0.0, circleArcYAtX(Point(0.0, 0.0), radius, -10.0), delta)
        assertEquals(0.0, circleArcYAtX(Point(0.0, 0.0), radius, 10.0), delta)

        assertEquals(5.0, circleArcYAtX(Point(5.0, 5.0), radius, -5.0), delta)
        assertEquals(5.0, circleArcYAtX(Point(5.0, 5.0), radius, 15.0), delta)

        assertEquals(0.0, circleArcYAtX(Point(0.0, 0.0), -1 * radius, -10.0), delta)
        assertEquals(0.0, circleArcYAtX(Point(0.0, 0.0), -1 * radius, 10.0), delta)

        assertEquals(5.0, circleArcYAtX(Point(5.0, 5.0), -1 * radius, -5.0), delta)
        assertEquals(5.0, circleArcYAtX(Point(5.0, 5.0), -1 * radius, 15.0), delta)
    }

    @Test
    fun arcYatXShouldProvidePositiveHeightForNegativeRadius() {
        val delta = 0.000001
        val radius = -10.0

        assertEquals(10.0, circleArcYAtX(Point(0.0, 0.0), radius, 0.0), delta)

        assertEquals(9.949874, circleArcYAtX(Point(0.0, 0.0), radius, 1.0), delta)
        assertEquals(9.949874, circleArcYAtX(Point(0.0, 0.0), radius, -1.0), delta)

        assertEquals(8.660254, circleArcYAtX(Point(0.0, 0.0), radius, -5.0), delta)
        assertEquals(8.660254, circleArcYAtX(Point(0.0, 0.0), radius, 5.0), delta)

        assertEquals(4.358898, circleArcYAtX(Point(0.0, 0.0), radius, -9.0), delta)
        assertEquals(4.358898, circleArcYAtX(Point(0.0, 0.0), radius, 9.0), delta)

        assertEquals(11.660254, circleArcYAtX(Point(1.0, 3.0), radius, -4.0), delta)
        assertEquals(11.660254, circleArcYAtX(Point(1.0, 3.0), radius, 6.0), delta)
    }

    @Test
    fun arcYatXShouldProvideNegativeHeightForPositiveRadius() {
        val delta = 0.000001
        val radius = 10.0

        assertEquals(-10.0, circleArcYAtX(Point(0.0, 0.0), radius, 0.0), delta)

        assertEquals(-9.949874, circleArcYAtX(Point(0.0, 0.0), radius, 1.0), delta)
        assertEquals(-9.949874, circleArcYAtX(Point(0.0, 0.0), radius, -1.0), delta)

        assertEquals(-8.660254, circleArcYAtX(Point(0.0, 0.0), radius, -5.0), delta)
        assertEquals(-8.660254, circleArcYAtX(Point(0.0, 0.0), radius, 5.0), delta)

        assertEquals(-4.358898, circleArcYAtX(Point(0.0, 0.0), radius, -9.0), delta)
        assertEquals(-4.358898, circleArcYAtX(Point(0.0, 0.0), radius, 9.0), delta)

        assertEquals(-5.660254, circleArcYAtX(Point(1.0, 3.0), radius, -4.0), delta)
        assertEquals(-5.660254, circleArcYAtX(Point(1.0, 3.0), radius, 6.0), delta)
    }
}
