package fi.fta.geoviite.infra.math

import kotlin.math.PI
import kotlin.test.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

const val DOUBLE_CALC_DELTA = 0.00000000000001

class AngleTest {

    @Test
    fun gradsToRadsWorks() {
        val angleDelta = 0.0001

        assertEquals(0.0, gradsToRads(0.0), angleDelta)
        assertEquals(0.7854, gradsToRads(50.0), angleDelta)
        assertEquals(2.3562, gradsToRads(150.0), angleDelta)
        assertEquals(3.9270, gradsToRads(250.0), angleDelta)
        assertEquals(5.4978, gradsToRads(350.0), angleDelta)
        assertEquals(6.2832, gradsToRads(400.0), angleDelta)

        assertEquals(7.0686, gradsToRads(450.0), angleDelta)
        assertEquals(-0.7854, gradsToRads(-50.0), angleDelta)
    }

    @Test
    fun radsToGRadsWorks() {
        val angleDelta = 0.1

        assertEquals(0.0, radsToGrads(0.0), angleDelta)
        assertEquals(50.0, radsToGrads(0.7854), angleDelta)
        assertEquals(150.0, radsToGrads(2.3562), angleDelta)
        assertEquals(250.0, radsToGrads(3.9270), angleDelta)
        assertEquals(350.0, radsToGrads(5.4978), angleDelta)
        assertEquals(400.0, radsToGrads(6.2832), angleDelta)

        assertEquals(450.0, radsToGrads(7.0686), angleDelta)
        assertEquals(-50.0, radsToGrads(-0.7854), angleDelta)
    }

    @Test
    fun radsToDegreesWorks() {
        val angleDelta = 0.1
        assertEquals(171.9, radsToDegrees(3.0), angleDelta)
        assertEquals(0.0, radsToDegrees(0.0), angleDelta)
        assertEquals(180.0, radsToDegrees(3.14159), angleDelta)
        assertEquals(-180.0, radsToDegrees(-3.14159), angleDelta)
    }

    @Test
    fun degreesToRadsWorks() {
        val angleDelta = 0.1
        assertEquals(3.0, degreesToRads(171.9), angleDelta)
        assertEquals(0.0, degreesToRads(0.0), angleDelta)
        assertEquals(3.14159, degreesToRads(180.0), angleDelta)
        assertEquals(-3.14159, degreesToRads(-180.0), angleDelta)
    }

    @Test
    fun rotateAngleWorks() {
        assertEquals(0.0, rotateAngle(1.0, -1.0), DOUBLE_CALC_DELTA)
        assertEquals(0.0, rotateAngle(-1.0, 1.0), DOUBLE_CALC_DELTA)
        assertEquals(2.0, rotateAngle(1.0, 1.0), DOUBLE_CALC_DELTA)
        assertEquals(-2.0, rotateAngle(-1.0, -1.0), DOUBLE_CALC_DELTA)
        assertEquals(0.0, rotateAngle(PI, -PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, rotateAngle(0.0, -PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, rotateAngle(0.0, PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, rotateAngle(0.0, 3 * PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, rotateAngle(0.0, -3 * PI), DOUBLE_CALC_DELTA)
        assertEquals(0.1, rotateAngle(0.0, -4 * PI + 0.1), DOUBLE_CALC_DELTA)
        assertEquals(0.1, rotateAngle(0.0, 4 * PI + 0.1), DOUBLE_CALC_DELTA)
        assertEquals(0.8 * PI, rotateAngle(-0.6 * PI, -0.6 * PI), DOUBLE_CALC_DELTA)
        assertEquals(-0.8 * PI, rotateAngle(0.6 * PI, 0.6 * PI), DOUBLE_CALC_DELTA)
        assertEquals(0.0, rotateAngle(0.6 * PI, -0.6 * PI), DOUBLE_CALC_DELTA)
        assertEquals(0.0, rotateAngle(-0.6 * PI, 0.6 * PI), DOUBLE_CALC_DELTA)
    }

    @Test
    fun angleDiffWorks() {
        assertEquals(0.0, angleDiffRads(0.0, 0.0))
        assertEquals(0.1, angleDiffRads(1.1, 1.2), DOUBLE_CALC_DELTA)
        assertEquals(0.1, angleDiffRads(1.2, 1.1), DOUBLE_CALC_DELTA)
        assertEquals(0.1, angleDiffRads(PI + 1.1, PI + 1.2), DOUBLE_CALC_DELTA)
        assertEquals(0.1, angleDiffRads(PI + 1.2, PI + 1.1), DOUBLE_CALC_DELTA)
        assertEquals(0.2, angleDiffRads(-0.1, 0.1), DOUBLE_CALC_DELTA)
        assertEquals(0.2, angleDiffRads(0.1, -0.1), DOUBLE_CALC_DELTA)
        assertEquals(0.0, angleDiffRads(-0.1, -0.1), DOUBLE_CALC_DELTA)
        assertEquals(2.3, angleDiffRads(-PI - 1.1, PI + 1.2), DOUBLE_CALC_DELTA)
        assertEquals(2.3, angleDiffRads(PI + 1.2, -PI - 1.1), DOUBLE_CALC_DELTA)
    }

    @Test
    fun angleAvgWorks() {
        assertEquals(0.0, angleAvgRads(0.1, -0.1), DOUBLE_CALC_DELTA)
        assertEquals(0.1, angleAvgRads(0.0, 0.2), DOUBLE_CALC_DELTA)
        assertEquals(-0.2, angleAvgRads(-0.1, -0.3), DOUBLE_CALC_DELTA)
        assertEquals(0.0, angleAvgRads(0.4 * PI, -0.4 * PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, angleAvgRads(0.6 * PI, -0.6 * PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, angleAvgRads(0.9 * PI, -0.9 * PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, angleAvgRads(PI, -PI), DOUBLE_CALC_DELTA)
        assertEquals(0.8 * PI, angleAvgRads(0.9 * PI, 0.7 * PI), DOUBLE_CALC_DELTA)
        assertEquals(0.9 * PI, angleAvgRads(0.7 * PI, -0.9 * PI), DOUBLE_CALC_DELTA)
        assertEquals(-0.9 * PI, angleAvgRads(0.9 * PI, -0.7 * PI), DOUBLE_CALC_DELTA)
    }

    @Test
    fun `interpolateAngleRads works`() {
        assertEquals(0.05, interpolateAngleRads(0.1, -0.1, 0.25), DOUBLE_CALC_DELTA)
        assertEquals(-0.05, interpolateAngleRads(0.1, -0.1, 0.75), DOUBLE_CALC_DELTA)
        assertEquals(PI - 0.05, interpolateAngleRads(PI - 0.1, -PI + 0.1, 0.25), DOUBLE_CALC_DELTA)
        assertEquals(-PI + 0.05, interpolateAngleRads(PI - 0.1, -PI + 0.1, 0.75), DOUBLE_CALC_DELTA)
        assertEquals(-PI, interpolateAngleRads(PI - 0.1, -PI + 0.3, 0.25))
    }

    @Test
    fun angleBetweenWorks() {
        assertTrue(angleIsBetween(1.0, 5.0, 2.0))
        assertTrue(angleIsBetween(1.0, 5.0, 1.0))
        assertTrue(angleIsBetween(1.0, 5.0, 5.0))
        assertFalse(angleIsBetween(1.0, 5.0, 0.9))
        assertFalse(angleIsBetween(1.0, 5.0, 5.1))

        assertTrue(angleIsBetween(5.0, 1.0, 12.0))
        assertTrue(angleIsBetween(5.0, 1.0, 0.0))
        assertTrue(angleIsBetween(5.0, 1.0, 1.0))
        assertTrue(angleIsBetween(5.0, 1.0, 5.0))
        assertFalse(angleIsBetween(5.0, 1.0, 2.0))

        assertTrue(angleIsBetween(-5.0, -1.0, -2.0))
        assertTrue(angleIsBetween(-5.0, -1.0, -1.0))
        assertTrue(angleIsBetween(-5.0, -1.0, -5.0))
        assertFalse(angleIsBetween(-5.0, -1.0, -0.9))
        assertFalse(angleIsBetween(-5.0, -1.0, -5.1))
        assertFalse(angleIsBetween(-5.0, -1.0, 2.1))

        assertTrue(angleIsBetween(1.0, -2.0, -12.0))
        assertTrue(angleIsBetween(1.0, -2.0, 10.0))
        assertTrue(angleIsBetween(1.0, -2.0, -2.0))
        assertTrue(angleIsBetween(1.0, -2.0, 1.0))
        assertFalse(angleIsBetween(1.0, -2.0, 0.0))
    }

    @Test
    fun relativeAngleWorks() {
        assertEquals(-0.2, relativeAngle(-5 * PI + 0.1, 5 * PI - 0.1), DOUBLE_CALC_DELTA)
        assertEquals(0.2, relativeAngle(5 * PI - 0.1, -5 * PI + 0.1), DOUBLE_CALC_DELTA)
        assertEquals(0.0, relativeAngle(0.0, 0.0), DOUBLE_CALC_DELTA)
        assertEquals(0.0, relativeAngle(0.7, 0.7), DOUBLE_CALC_DELTA)
        assertEquals(0.1, relativeAngle(0.7, 0.8), DOUBLE_CALC_DELTA)
        assertEquals(0.1, relativeAngle(3.08, 3.18), DOUBLE_CALC_DELTA)
        assertEquals(-0.1, relativeAngle(0.7, 0.6), DOUBLE_CALC_DELTA)
        assertEquals(-0.1, relativeAngle(3.18, 3.08), DOUBLE_CALC_DELTA)
        assertEquals(3.0, relativeAngle(0.0, 3.0), DOUBLE_CALC_DELTA)
        assertEquals(5.0 - PI * 2.0, relativeAngle(0.0, 5.0), DOUBLE_CALC_DELTA)
        assertEquals(PI * 2.0 - 5.0, relativeAngle(5.0, 0.0), DOUBLE_CALC_DELTA)

        // sign flip exactly around pi
        assertEquals(-PI + 0.01, relativeAngle(0.0, PI + 0.01), DOUBLE_CALC_DELTA)
        assertEquals(PI - 0.01, relativeAngle(0.0, PI - 0.01), DOUBLE_CALC_DELTA)
        // results in half-closed [-PI..PI) range
        assertEquals(-PI, relativeAngle(0.0, PI), DOUBLE_CALC_DELTA)
        assertEquals(-PI, relativeAngle(PI, 0.0), DOUBLE_CALC_DELTA)

        for (x in (0..7)) {
            for (y in (0..7)) {
                val a = x.toDouble()
                val b = y.toDouble()
                assertEquals(
                    relativeAngle(a, b),
                    -relativeAngle(b, a),
                    DOUBLE_CALC_DELTA,
                    "relativeAngle antisymmetry with (a, b) = ($a, $b",
                )
            }
        }
    }

    @Test
    fun geoMathAngleConversionWorks() {
        verifyMathGeoConversion(0.0, 0.5 * PI)
        verifyMathGeoConversion(0.1 * PI, 0.4 * PI)
        verifyMathGeoConversion(0.6 * PI, 1.9 * PI)
        verifyMathGeoConversion(0.9 * PI, 1.6 * PI)
        verifyMathGeoConversion(-0.1 * PI, 0.6 * PI)
        verifyMathGeoConversion(-0.4 * PI, 0.9 * PI)
        verifyMathGeoConversion(-0.6 * PI, 1.1 * PI)
        verifyMathGeoConversion(-0.9 * PI, 1.4 * PI)
    }

    private fun verifyMathGeoConversion(mathRads: Double, geoRads: Double) {
        val delta = 0.00000001
        assertEquals(geoRads, radsMathToGeo(mathRads), delta, "math=PI*${mathRads/PI} geo=PI*${geoRads/PI}")
        assertEquals(mathRads, radsGeoToMath(geoRads), delta, "math=PI*${mathRads/PI} geo=PI*${geoRads/PI}")
        assertEquals(
            geoRads,
            radsMathToGeo(radsGeoToMath(geoRads)),
            delta,
            "math=PI*${mathRads/PI} geo=PI*${geoRads/PI}",
        )
        assertEquals(
            mathRads,
            radsGeoToMath(radsMathToGeo(mathRads)),
            delta,
            "math=PI*${mathRads/PI} geo=PI*${geoRads/PI}",
        )
    }
}
