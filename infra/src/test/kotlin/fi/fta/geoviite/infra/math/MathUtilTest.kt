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
}
