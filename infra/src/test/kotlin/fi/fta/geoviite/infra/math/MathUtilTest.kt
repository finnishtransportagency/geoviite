package fi.fta.geoviite.infra.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MathUtilTest {

    @Test
    fun factorialWorks() {
        assertEquals(1, factorial(0))
        assertEquals(1, factorial(1))
        assertEquals(2, factorial(2))
        assertEquals(6, factorial(3))
        assertEquals(720, factorial(6))
        assertEquals(362880, factorial(9))
    }

    @Test
    fun `Distance between ranges works`() {
        // inside
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), Range(11.0, 12.0)))

        // overlapping
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), Range(5.0, 12.0)))
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), Range(5.0, 10.0)))
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), Range(18.0, 22.0)))
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), Range(20.0, 22.0)))

        // outside
        assertEquals(5.0, minimumDistance(Range(10.0, 20.0), Range(0.0, 5.0)))
        assertEquals(5.0, minimumDistance(Range(10.0, 20.0), Range(25.0, 30.0)))
        assertEquals(5.0, minimumDistance(Range(0.0, 5.0), Range(10.0, 20.0)))
        assertEquals(5.0, minimumDistance(Range(25.0, 30.0), Range(10.0, 20.0)))
    }

    @Test
    fun `Distance between range and value works`() {
        // inside
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), 11.0))

        // on edge
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), 10.0))
        assertEquals(0.0, minimumDistance(Range(10.0, 20.0), 20.0))

        // outside
        assertEquals(5.0, minimumDistance(Range(10.0, 20.0), 5.0))
        assertEquals(6.0, minimumDistance(Range(10.0, 20.0), 26.0))
    }
}
