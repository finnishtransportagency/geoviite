package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.RotationDirection.CCW
import fi.fta.geoviite.infra.common.RotationDirection.CW
import fi.fta.geoviite.infra.math.*
import kotlin.math.*
import kotlin.test.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

const val COMPARE_DELTA = 0.000001

class GeometryElementTest {

    @Test
    fun lineCalculationWorks() {
        assertGeometryValid(
            line(
                length = 121.133,
                start = Point(x = 2497947.988500, y = 6661582.954000),
                end = Point(x = 2497847.364500, y = 6661515.514000),
            ),
            accuracy = 0.001,
        )
    }

    @Test
    fun lineVariationsCalculationWorks() {
        assertGrowsConsistently(lineFromOrigin(50.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(lineFromOrigin(150.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(lineFromOrigin(250.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(lineFromOrigin(350.0), positiveX = true, positiveY = false)

        assertGrowsConsistently(lineToOrigin(50.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(lineToOrigin(150.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(lineToOrigin(250.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(lineToOrigin(350.0), positiveX = true, positiveY = false)
    }

    @Test
    fun curveCalculationWorksCcw() {
        assertGeometryValid(
            curve(
                length = 37.654,
                rotation = CCW,
                radius = 1070.0,
                chord = 37.651564,
                start = Point(x = 2499138.729000, y = 6662277.682000),
                end = Point(x = 2499109.274000, y = 6662254.229000),
                center = Point(x = 2499790.389000, y = 6661429.013000),
            ),
            accuracy = 0.001,
        )
    }

    @Test
    fun curveCalculationWorksCw() {
        assertGeometryValid(
            curve(
                length = 204.018,
                rotation = CW,
                radius = 806.0,
                chord = 203.473678,
                start = Point(x = 2498762.696000, y = 6661941.804000),
                end = Point(x = 2498585.977000, y = 6661840.948000),
                center = Point(x = 2498278.021000, y = 6662585.797000),
            ),
            accuracy = 0.001,
        )
    }

    @Test
    fun curveVariationsCalculationWorks() {
        assertGrowsConsistently(curveFromOrigin(CW, 0.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(curveFromOrigin(CCW, 0.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(curveFromOrigin(CW, 100.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(curveFromOrigin(CCW, 100.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(curveFromOrigin(CW, 200.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(curveFromOrigin(CCW, 200.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(curveFromOrigin(CW, 300.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(curveFromOrigin(CCW, 300.0), positiveX = true, positiveY = false)

        assertGrowsConsistently(curveToOrigin(CW, 0.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(curveToOrigin(CCW, 0.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(curveToOrigin(CW, 100.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(curveToOrigin(CCW, 100.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(curveToOrigin(CW, 200.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(curveToOrigin(CCW, 200.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(curveToOrigin(CW, 300.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(curveToOrigin(CCW, 300.0), positiveX = false, positiveY = false)
    }

    @Test
    fun clothoidCalculationWorksFlatteningBetweenCurvesCw() {
        assertGeometryValid(
            clothoid(
                constant = 404.795,
                length = 103.0,
                rotation = CW,
                dirStartGrads = 100.0 - 290.647958,
                dirEndGrads = 100.0 - 295.27125,
                radiusStart = 2559.0,
                radiusEnd = 981.0,
                start = Point(x = 2485163.625000, y = 6663015.781500),
                end = Point(x = 2485061.340000, y = 6663003.865000),
            ),
            accuracy = 0.001,
        )
    }

    @Test
    fun clothoidCalculationWorksFlatteningCw() {
        assertGeometryValid(
            clothoid(
                constant = 377.831,
                length = 178.0,
                rotation = CW,
                dirStartGrads = 100.0 - 255.173986,
                dirEndGrads = 100.0 - 262.238719,
                radiusStart = 802.0,
                radiusEnd = null,
                start = Point(x = 2500648.286000, y = 6663306.527500),
                end = Point(x = 2500504.550000, y = 6663201.697000),
            ),
            accuracy = 0.001,
        )
    }

    @Test
    fun clothoidCalculationWorksSteepeningCcw() {
        assertGeometryValid(
            clothoid(
                constant = 375.819,
                length = 132.0,
                rotation = CCW,
                dirStartGrads = 100.0 - 262.238719,
                dirEndGrads = 100.0 - 258.311905,
                radiusStart = null,
                radiusEnd = 1070.0,
                start = Point(x = 2499246.623000, y = 6662353.689000),
                end = Point(x = 2499138.729000, y = 6662277.682000),
            ),
            accuracy = 0.001,
        )
    }

    @Test
    fun clothoidSteepeningVariationsCalculationWorks() {
        // Steepening curves: start straight in origin, curve as it heads outwards
        assertGrowsConsistently(clothoidFromOrigin(CW, 0.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(clothoidFromOrigin(CCW, 0.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(clothoidFromOrigin(CW, 100.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(clothoidFromOrigin(CCW, 100.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(clothoidFromOrigin(CW, 200.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(clothoidFromOrigin(CCW, 200.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(clothoidFromOrigin(CW, 300.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(clothoidFromOrigin(CCW, 300.0), positiveX = true, positiveY = false)
    }

    @Test
    fun clothoidFlatteningVariationsCalculationWorks() {
        // Flattening curves: start curved outside origin, straighten out as it heads in, ending at
        // origin
        assertGrowsConsistently(clothoidToOrigin(CW, 0.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(clothoidToOrigin(CCW, 0.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(clothoidToOrigin(CW, 100.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(clothoidToOrigin(CCW, 100.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(clothoidToOrigin(CW, 200.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(clothoidToOrigin(CCW, 200.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(clothoidToOrigin(CW, 300.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(clothoidToOrigin(CCW, 300.0), positiveX = false, positiveY = false)
    }

    @Test
    fun biquadraticParabolaCalculationWorksL162FlatteningCcw() {
        assertGeometryValid(
            biquadraticParabola(
                length = 162.0,
                rotation = CCW,
                dirStartGrads = 100.0 - 283.253815,
                dirEndGrads = 100.0 - 279.117589,
                radiusStart = 1244.0,
                radiusEnd = null,
                start = Point(x = 2485815.150000, y = 6663189.957500),
                end = Point(x = 2485660.856000, y = 6663140.695000),
            ),
            accuracy = 0.1,
        ) // Poor accuracy in this calculation type
    }

    @Test
    fun biquadraticParabolaCalculationWorksL100SteepeningCw() {
        assertGeometryValid(
            biquadraticParabola(
                length = 100.0,
                rotation = CW,
                dirStartGrads = 100.0 - 279.117589,
                dirEndGrads = 100.0 - 280.361230,
                radiusStart = null,
                radiusEnd = 2559.0,
                start = Point(x = 2485660.856000, y = 6663140.695000),
                end = Point(x = 2485566.007000, y = 6663109.018500),
            ),
            accuracy = 0.1,
        ) // Poor accuracy in this calculation type
    }

    @Test
    fun biquadraticParabolaVariationsCalculationWorks() {
        // Steepening curves: start straight in origin, curve as it heads outwards
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CCW, 0.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CW, 0.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CW, 100.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CCW, 100.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CW, 200.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CCW, 200.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CW, 300.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(biquadraticParabolaFromOrigin(CCW, 300.0), positiveX = true, positiveY = false)
    }

    @Test
    fun biquadraticParabolaFlatteningVariationsCalculationWorks() {
        // Flattening curves: start curved outside origin, straighten out as it heads in, ending at
        // origin
        assertGrowsConsistently(biquadraticParabolaToOrigin(CW, 0.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaToOrigin(CCW, 0.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(biquadraticParabolaToOrigin(CW, 100.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaToOrigin(CCW, 100.0), positiveX = true, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaToOrigin(CW, 200.0), positiveX = false, positiveY = false)
        assertGrowsConsistently(biquadraticParabolaToOrigin(CCW, 200.0), positiveX = false, positiveY = true)
        assertGrowsConsistently(biquadraticParabolaToOrigin(CW, 300.0), positiveX = true, positiveY = false)
        assertGrowsConsistently(biquadraticParabolaToOrigin(CCW, 300.0), positiveX = false, positiveY = false)
    }

    @Test
    fun lineBoundsWorks() {
        assertBoundsWorks(line(Point(10.12, 21.01), Point(34.65, 56.43)), 0.1)
        assertBoundsWorks(line(Point(10.12, 21.01), Point(-34.65, -56.43)), 0.1)
        assertBoundsWorks(line(Point(-10.12, -21.01), Point(-34.65, -56.43)), 0.1)
        assertBoundsWorks(line(Point(-10.12, 21.01), Point(34.65, -56.43)), 0.1)
        assertBoundsWorks(line(Point(0.0, 0.0), Point(10.0, 0.0)), 0.1)
        assertBoundsWorks(line(Point(0.0, 0.0), Point(0.0, 10.0)), 0.1)
        assertBoundsWorks(line(Point(0.0, 0.0), Point(-10.0, 0.0)), 0.1)
        assertBoundsWorks(line(Point(0.0, 0.0), Point(0.0, -10.0)), 0.1)
    }

    @Test
    fun curveBoundsWorks() {
        val center = Point(20.0, 40.0)
        val radius = 500.0
        val anglePartCount = 50
        for (anglePart in 0..anglePartCount) {
            val startAngle = 2.0 * PI * (anglePart.toDouble() / anglePartCount.toDouble())
            val endAngle = 2.0 * PI * ((anglePart.toDouble() + 1.0) / anglePartCount.toDouble())
            val start = pointInDirection(center, radius, startAngle)
            val end = pointInDirection(center, radius, endAngle)
            assertBoundsWorks(curve(CCW, radius, start, end, center), 0.1 * lineLength(start, end))
            assertBoundsWorks(curve(CW, radius, end, start, center), 0.1 * lineLength(start, end))
        }
    }

    @Test
    fun clothoidBoundsWorks() {
        val constant = 200.0
        val length = 50.0
        val anglePartCount = 50
        for (anglePart in 0..anglePartCount) {
            val startAngle = 2.0 * PI * (anglePart.toDouble() / anglePartCount.toDouble())
            val start = Point(10.0, 20.0)
            assertBoundsWorks(clothoidSteepening(constant, length, startAngle, start, CW), 1.0)
            assertBoundsWorks(clothoidSteepening(constant, length, startAngle, start, CCW), 1.0)
            assertBoundsWorks(clothoidFlattening(constant, length, startAngle, start, CW), 1.0)
            assertBoundsWorks(clothoidFlattening(constant, length, startAngle, start, CCW), 1.0)
        }
    }

    @Test
    fun circleLengthUntilWorksWithCwCurveAlongPositiveY() {
        val start = Point(1.0, 1.0)
        val center = Point(1.0, 2.0)
        val end = Point(1.0, 3.0)

        val circle = curve(CW, 1.0, start, end, center)
        assertEquals(PI, circle.calculatedLength)

        assertEquals(PI / 2, circle.getLengthUntil(Point(0.0, 2.0)))
        assertEquals(PI / 2, circle.getLengthUntil(Point(-1.0, 2.0)))
        assertEquals(PI / 4, circle.getLengthUntil(Point(0.0, 1.0)))
        assertEquals(3 * (PI / 4), circle.getLengthUntil(Point(0.0, 3.0)))
        assertEquals(0.0, circle.getLengthUntil(Point(2.0, 1.0)))
        assertEquals(circle.calculatedLength, circle.getLengthUntil(Point(2.0, 3.0)))
    }

    @Test
    fun circleLengthUntilWorksWithCwCurveAlongNegativeY() {
        val start = Point(1.0, 3.0)
        val center = Point(1.0, 2.0)
        val end = Point(1.0, 1.0)

        val circle = curve(CW, 1.0, start, end, center)
        assertEquals(PI, circle.calculatedLength)

        assertEquals(PI / 2, circle.getLengthUntil(Point(2.0, 2.0)))
        assertEquals(PI / 2, circle.getLengthUntil(Point(3.0, 2.0)))
        assertEquals(PI / 4, circle.getLengthUntil(Point(2.0, 3.0)))
        assertEquals(3 * (PI / 4), circle.getLengthUntil(Point(2.0, 1.0)))
        assertEquals(0.0, circle.getLengthUntil(Point(0.0, 3.0)))
        assertEquals(circle.calculatedLength, circle.getLengthUntil(Point(0.0, 1.0)))
    }

    @Test
    fun circleLengthUntilWorksWithCcwCurveAlongPositiveY() {
        val start = Point(1.0, 1.0)
        val center = Point(1.0, 2.0)
        val end = Point(1.0, 3.0)

        val circle = curve(CCW, 1.0, start, end, center)
        assertEquals(PI, circle.calculatedLength)

        assertEquals(PI / 2, circle.getLengthUntil(Point(2.0, 2.0)))
        assertEquals(PI / 2, circle.getLengthUntil(Point(3.0, 2.0)))
        assertEquals(PI / 4, circle.getLengthUntil(Point(2.0, 1.0)))
        assertEquals(3 * (PI / 4), circle.getLengthUntil(Point(2.0, 3.0)))
        assertEquals(0.0, circle.getLengthUntil(Point(0.0, 1.0)))
        assertEquals(circle.calculatedLength, circle.getLengthUntil(Point(0.0, 3.0)))
    }

    @Test
    fun circleLengthUntilWorksWithCcwCurveAlongNegativeY() {
        val start = Point(1.0, 3.0)
        val center = Point(1.0, 2.0)
        val end = Point(1.0, 1.0)

        val circle = curve(CCW, 1.0, start, end, center)
        assertEquals(PI, circle.calculatedLength)

        assertEquals(PI / 2, circle.getLengthUntil(Point(0.0, 2.0)))
        assertEquals(PI / 2, circle.getLengthUntil(Point(-1.0, 2.0)))
        assertEquals(PI / 4, circle.getLengthUntil(Point(0.0, 3.0)))
        assertEquals(3 * (PI / 4), circle.getLengthUntil(Point(0.0, 1.0)))
        assertEquals(0.0, circle.getLengthUntil(Point(2.0, 3.0)))
        assertEquals(circle.calculatedLength, circle.getLengthUntil(Point(2.0, 1.0)))
    }

    @Test
    fun lineLengthUntilWorks() {
        val line = line(start = Point(100.0, 100.0), end = Point(0.0, 0.0))

        assertLengthUntilExact(line, line.start, 0.0)
        assertLengthUntilExact(line, line.end, hypot(100.0, 100.0))
        assertLengthUntilExact(line, Point(50.0, 50.0), hypot(50.0, 50.0))
        assertEquals(hypot(50.0, 50.0), line.getLengthUntil(Point(0.0, 100.0)), COMPARE_DELTA)
        assertEquals(hypot(50.0, 50.0), line.getLengthUntil(Point(100.0, 0.0)), COMPARE_DELTA)
    }

    @Test
    fun curveLengthUntilWorksCw() {
        val radius = 100.0
        val curve =
            curve(
                rotation = CW,
                radius = radius,
                start = Point(x = 200.0, y = 100.0),
                end = Point(x = 100.0, y = 0.0),
                center = Point(x = 100.0, y = 100.0),
            )

        assertLengthUntilExact(curve, curve.start, 0.0)
        assertLengthUntilExact(curve, curve.end, circleArcLength(radius) / 4)

        val quarterTarget = pointInDirection(curve.center, radius, -PI / 4)
        assertLengthUntilExact(curve, quarterTarget, circleArcLength(radius) / 8)

        val distantQuarterTarget = pointInDirection(curve.center, radius * 2, -PI / 4)
        assertEquals(circleArcLength(radius) / 8, curve.getLengthUntil(distantQuarterTarget), COMPARE_DELTA)
    }

    @Test
    fun curveLengthUntilWorksCcw() {
        val radius = 100.0
        val center = Point(x = 100.0, y = 100.0)
        val curve =
            curve(
                rotation = CCW,
                radius = radius,
                start = pointInDirection(center, radius, 3 * PI / 4),
                end = pointInDirection(center, radius, -3 * PI / 4),
                center = center,
            )

        assertLengthUntilExact(curve, curve.start, 0.0)
        assertLengthUntilExact(curve, curve.end, circleArcLength(radius) / 4)

        val quarterTarget = Point(x = 0.0, y = 100.0)
        assertLengthUntilExact(curve, quarterTarget, circleArcLength(radius) / 8)

        val distantQuarterTarget = Point(x = -100.0, y = 100.0)
        assertEquals(circleArcLength(radius) / 8, curve.getLengthUntil(distantQuarterTarget), COMPARE_DELTA)
    }

    @Test
    fun biquadraticParabolaLengthUntilWorksCw() {
        val parabola =
            biquadraticParabola(
                length = 100.0,
                rotation = CW,
                dirStartGrads = 100.0 - 279.117589,
                dirEndGrads = 100.0 - 280.361230,
                radiusStart = null,
                radiusEnd = 2559.0,
                start = Point(x = 2485660.856000, y = 6663140.695000),
                end = Point(x = 2485566.007000, y = 6663109.018500),
            )
        assertEquals(0.0, parabola.getLengthUntil(parabola.start), COMPARE_DELTA)
        assertEquals(parabola.calculatedLength, parabola.getLengthUntil(parabola.end), 0.1)
        assertLengthUntilAlongElement(parabola, 0.1)
    }

    @Test
    fun biquadraticParabolaLengthUntilWorksCcw() {
        val parabola =
            biquadraticParabola(
                length = 162.0,
                rotation = CCW,
                dirStartGrads = 100.0 - 283.253815,
                dirEndGrads = 100.0 - 279.117589,
                radiusStart = 1244.0,
                radiusEnd = null,
                start = Point(x = 2485815.150000, y = 6663189.957500),
                end = Point(x = 2485660.856000, y = 6663140.695000),
            )
        assertEquals(0.0, parabola.getLengthUntil(parabola.start), COMPARE_DELTA)
        assertEquals(parabola.calculatedLength, parabola.getLengthUntil(parabola.end), 0.1)
        assertLengthUntilAlongElement(parabola, 0.1)
    }

    @Test
    fun clothoidLengthUntilWorksCw() {
        val clothoid =
            clothoid(
                constant = 404.795,
                rotation = CW,
                dirStartGrads = 100.0 - 290.647958,
                dirEndGrads = 100.0 - 295.27125,
                radiusStart = 2559.0,
                radiusEnd = 981.0,
                start = Point(x = 2485163.625000, y = 6663015.781500),
                end = Point(x = 2485061.340000, y = 6663003.865000),
            )
        assertEquals(0.0, clothoid.getLengthUntil(clothoid.start), COMPARE_DELTA)
        assertEquals(clothoid.calculatedLength, clothoid.getLengthUntil(clothoid.end), 0.001)
        assertLengthUntilAlongElement(clothoid, 0.001)
    }

    @Test
    fun clothoidLengthUntilWorksCcw() {
        val clothoid =
            clothoid(
                constant = 375.819,
                rotation = CCW,
                dirStartGrads = 100.0 - 262.238719,
                dirEndGrads = 100.0 - 258.311905,
                radiusStart = null,
                radiusEnd = 1070.0,
                start = Point(x = 2499246.623000, y = 6662353.689000),
                end = Point(x = 2499138.729000, y = 6662277.682000),
            )
        assertEquals(0.0, clothoid.getLengthUntil(clothoid.start), COMPARE_DELTA)
        assertEquals(clothoid.calculatedLength, clothoid.getLengthUntil(clothoid.end), 0.001)
        assertLengthUntilAlongElement(clothoid, 0.001)
    }

    private fun assertGeometryValid(element: GeometryElement, accuracy: Double) {
        assertEquals(element.length.toDouble(), element.calculatedLength, accuracy)
        assertApproximatelyEquals(element.start, element.getCoordinateAt(0.0), accuracy)
        assertApproximatelyEquals(element.end, element.getCoordinateAt(element.length.toDouble()), accuracy)
    }

    private fun assertGrowsConsistently(element: GeometryElement, positiveX: Boolean, positiveY: Boolean) {
        var previous: Point = element.getCoordinateAt(0.0)
        val totalLength = element.calculatedLength.toInt()
        for (dist in 1..totalLength step 1) {
            val current: Point = element.getCoordinateAt(dist.toDouble())
            if (positiveX) {
                assertTrue(current.x > previous.x, "X should grow: current=${current.x} previous=${previous.x}")
            } else {
                assertTrue(current.x < previous.x, "X should shrink: current=${current.x} previous=${previous.x}")
            }
            if (positiveY) {
                assertTrue(current.y > previous.y, "Y should grow: current=${current.y} previous=${previous.y}")
            } else {
                assertTrue(current.y < previous.y, "Y should shrink: current=${current.y} previous=${previous.y}")
            }
            val portion = dist.toDouble() / totalLength.toDouble()
            val maxDist = element.length.toDouble() * portion + 0.2 // Allow for accuracy deviation
            val offsetFromStart = current - element.getCoordinateAt(0.0)
            assertTrue(
                abs(offsetFromStart.x) <= maxDist,
                "Step $dist: X-offset=abs(${current.x}) should be less than ${element.length.toDouble()}*$portion=$maxDist",
            )
            assertTrue(
                abs(offsetFromStart.y) <= maxDist,
                "Step $dist: Y-offset=abs(${current.y}) should be less than ${element.length.toDouble()}*$portion=$maxDist",
            )
            previous = current
        }
    }

    private fun assertBoundsWorks(element: GeometryElement, testOffset: Double) {
        val bbox = boundingBoxAroundPointsOrNull(element.bounds)!!
        assert(element.calculatedLength > 0.0)
        val step = element.calculatedLength / 10.0
        var distance = 0.0
        while (distance <= element.calculatedLength) {
            val point = element.getCoordinateAt(distance)
            assertTrue(
                bbox.contains(point),
                "Bounding box should contain all element points: bbox=$bbox point=$point element=$element",
            )
            distance += step
        }

        val minPoint = Point(min(element.start.x, element.end.x), min(element.start.y, element.end.y))
        assertFalse(bbox.contains(minPoint - Point(testOffset, 0.0)))
        assertFalse(bbox.contains(minPoint - Point(0.0, testOffset)))

        val maxPoint = Point(max(element.start.x, element.end.x), max(element.start.y, element.end.y))
        assertFalse(bbox.contains(maxPoint + Point(testOffset, 0.0)))
        assertFalse(bbox.contains(maxPoint + Point(0.0, testOffset)))
    }

    private fun assertLengthUntilExact(element: GeometryElement, target: Point, expected: Double) {
        val lengthUntil = element.getLengthUntil(target)
        assertEquals(expected, lengthUntil, COMPARE_DELTA)
        assertTrue(target.isSame(element.getCoordinateAt(lengthUntil), COMPARE_DELTA))
    }

    private fun assertLengthUntilAlongElement(element: GeometryElement, delta: Double = COMPARE_DELTA) {
        val steps = 5
        for (i in 0..steps) {
            val length = (i / steps) * element.calculatedLength
            val point = element.getCoordinateAt(length)
            val lengthUntil = element.getLengthUntil(point)
            assertEquals(length, lengthUntil, delta)
        }
    }
}
