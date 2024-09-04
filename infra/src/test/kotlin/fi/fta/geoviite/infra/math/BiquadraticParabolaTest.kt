package fi.fta.geoviite.infra.math

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BiquadraticParabolaTest {

    @Test
    fun pointAtDistanceWorksForR1000L250() {
        val accuracy = 0.001
        val biquadraticParabola = biquadraticParabolaFunction(1000.0, 250.0)
        assertApproximatelyEquals(Point(0.0, 0.0), biquadraticParabola(0.0), accuracy)
        assertApproximatelyEquals(Point(50.0, 0.017), biquadraticParabola(50.0), accuracy)
        assertApproximatelyEquals(Point(100.0, 0.267), biquadraticParabola(100.0), accuracy)
        assertApproximatelyEquals(Point(150.0, 1.348), biquadraticParabola(150.0), accuracy)
        assertApproximatelyEquals(Point(200.0, 4.098), biquadraticParabola(200.0), accuracy)
        assertApproximatelyEquals(Point(250.0, 9.115), biquadraticParabola(250.0), accuracy)
    }

    @Test
    fun pointAtDistanceWorksForNegativeR1000L250() {
        val accuracy = 0.001
        val biquadraticParabola = biquadraticParabolaFunction(-1000.0, 250.0)
        assertApproximatelyEquals(Point(0.0, 0.0), biquadraticParabola(0.0), accuracy)
        assertApproximatelyEquals(Point(50.0, -0.017), biquadraticParabola(50.0), accuracy)
        assertApproximatelyEquals(Point(100.0, -0.267), biquadraticParabola(100.0), accuracy)
        assertApproximatelyEquals(Point(150.0, -1.348), biquadraticParabola(150.0), accuracy)
        assertApproximatelyEquals(Point(200.0, -4.098), biquadraticParabola(200.0), accuracy)
        assertApproximatelyEquals(Point(250.0, -9.115), biquadraticParabola(250.0), accuracy)
    }

    @Test
    fun biquadraticSTransitionWorks() {
        val length = 180.0
        val totalD = 0.14

        var previousD = -1.0
        for (x in 0..180) {
            val currentD = biquadraticSTransition(x.toDouble(), totalD, length)
            val linearTransitionValue = (x.toDouble() / length) * totalD
            assertTrue(currentD > previousD, "D value should grow: $currentD > $previousD")
            if (x <= length / 2) {
                assertTrue(
                    currentD in 0.0..linearTransitionValue,
                    "D on 1st half should be lower than linear: 0.0 < $currentD < $linearTransitionValue",
                )
            } else {
                assertTrue(
                    currentD in linearTransitionValue..totalD,
                    "D on 2nd half should be greater than linear: $linearTransitionValue < $currentD < $totalD",
                )
            }
            previousD = currentD
        }
    }

    private fun biquadraticParabolaFunction(R: Double, L: Double) = { d: Double ->
        biquadraticParabolaPointAtOffset(d, R, L)
    }
}
