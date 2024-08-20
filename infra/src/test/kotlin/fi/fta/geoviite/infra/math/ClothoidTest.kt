package fi.fta.geoviite.infra.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClothoidTest {

    @Test
    fun lengthCalculationWorks() {
        val delta = 0.00001

        assertEquals(0.0, clothoidLength(123.45, null, null), delta)
        assertEquals(0.0, clothoidLength(123.45, 12.3, 12.3), delta)

        assertEquals(30.0, clothoidLength(228.407531, null, 1739.0), delta)
        assertEquals(30.0, clothoidLength(228.407531, 1739.0, null), delta)

        assertEquals(30.0, clothoidLength(135.157689, 1739.0, 451.0), delta)
        assertEquals(30.0, clothoidLength(135.157689, 451.0, 1739.0), delta)
    }

    @Test
    fun lengthAtRadiusWorks() {
        val delta = 0.00001
        assertEquals(30.0, clothoidLengthAtRadius(228.407531, 1739.0), delta)
        assertEquals(36.0, clothoidLengthAtRadius(181.989011, 920.0), delta)
        assertEquals(35.0, clothoidLengthAtRadius(139.122248, 553.0), delta)
        assertEquals(70.0, clothoidLengthAtRadius(219.134662, 686.0), delta)
    }

    @Test
    fun angleAtLengthWorks() {
        val delta = 0.00001
        assertEquals(0.0, clothoidTwistAtLength(0.0, 0.0), delta)
        assertEquals(1.0, clothoidTwistAtLength(1.0, 2.0), delta)
        assertEquals(0.5, clothoidTwistAtLength(1.0, 1.0), delta)
        assertEquals(0.25, clothoidTwistAtLength(2.0, 1.0), delta)
    }

    @Test
    fun pointAtOffsetWorks() {
        assertCoordinateAtOffset(1414.213562, 400.0, Pair(399.936005, 5.332725))
        assertCoordinateAtOffset(228.407531, 30.0, Pair(29.999777, 0.086256))
        assertCoordinateAtOffset(181.989011, 36.0, Pair(35.998622, 0.234776))
        assertCoordinateAtOffset(326.113000, 150.0, Pair(149.832633, 5.284754))
        assertCoordinateAtOffset(472.679000, 75.0, Pair(74.99888294202714, 0.3148311978089424))
    }

    private fun assertCoordinateAtOffset(
        constantA: Double,
        distance: Double,
        expected: Pair<Double, Double>
    ) {
        val delta = 0.001
        val actual = clothoidPointAtOffset(constantA, distance)
        assertEquals(expected.first, actual.x, delta)
        assertEquals(expected.second, actual.y, delta)
    }
}
