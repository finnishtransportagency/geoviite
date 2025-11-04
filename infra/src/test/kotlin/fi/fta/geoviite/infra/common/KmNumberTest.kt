package fi.fta.geoviite.infra.common

import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KmNumberTest {

    @Test
    fun additionAndSubtractionWorks() {
        val km = KmNumber(123)
        val m1 = BigDecimal("55.123")
        val m2 = BigDecimal("22.321")
        assertEquals(TrackMeter(km, m1 + m2), TrackMeter(km, m1) + m2)
        assertEquals(TrackMeter(km, m1 - m2), TrackMeter(km, m1) - m2)
    }

    @Test
    fun parsingWorks() {
        assertEquals(TrackMeter(KmNumber(704), 85.123, 3), TrackMeter(704, 85.123, 3))
        assertEquals(TrackMeter(KmNumber(704), 85.123, 3), TrackMeter("0704", 85.123, 3))
        assertEquals(TrackMeter(KmNumber(704), 85.123, 3), TrackMeter("0704+85.123"))
        assertEquals(TrackMeter(KmNumber(704, "B"), 85.123, 3), TrackMeter("0704B+85.123"))
    }

    @Test
    fun comparisonWorks() {
        assertTrue(TrackMeter(123, 1.2, 1) > TrackMeter(123, 1.1, 1))
        assertTrue(TrackMeter(123, 1.2, 1) > TrackMeter(122, 1.3, 1))
        assertTrue(TrackMeter(123, 10.0, 1) > TrackMeter(123, 9.9, 1))

        assertEquals(1, TrackMeter(1, 1).compareTo(TrackMeter(1, 0)))
        assertEquals(-1, TrackMeter(1, 1).compareTo(TrackMeter(1, 2)))
        assertEquals(0, TrackMeter(1, 1).compareTo(TrackMeter(1, 1)))

        assertEquals(0, TrackMeter(1, 1).compareTo(TrackMeter(1, 1.0, 1)))
        assertEquals(1, TrackMeter(1, 1).compareTo(TrackMeter(1, 0.9, 1)))
        assertEquals(-1, TrackMeter(1, 1).compareTo(TrackMeter(1, 1.1, 1)))

        assertNotEquals(TrackMeter(1, 1), TrackMeter(1, 1.0, 1))
    }

    @Test
    fun isSameWorks() {
        // Same when the numbers match, even if the decimal scale is different
        assertTrue(TrackMeter(123, 1.2, 1).isSame(TrackMeter(123, 1.20, 2)))
        assertTrue(TrackMeter(123, 1.20, 2).isSame(TrackMeter(123, 1.2, 1)))

        // Not same if the number doesn't match
        assertFalse(TrackMeter(123, 1.2, 1).isSame(TrackMeter(123, 1.21, 2)))
        assertFalse(TrackMeter(123, 1.21, 2).isSame(TrackMeter(123, 1.2, 1)))

        // Only compare until desired decimal count
        assertTrue(TrackMeter(123, 1.2, 1).isSame(TrackMeter(123, 1.21, 2), 1))
        assertTrue(TrackMeter(123, 1.21, 2).isSame(TrackMeter(123, 1.2, 1), 1))
        assertTrue(TrackMeter(123, 1.21, 2).isSame(TrackMeter(123, 1.20, 2), 1))

        // When cutting decimals, do proper rounding
        assertFalse(TrackMeter(123, 1.2, 1).isSame(TrackMeter(123, 1.26, 2), 1))
        assertFalse(TrackMeter(123, 1.26, 2).isSame(TrackMeter(123, 1.2, 1), 1))
        assertFalse(TrackMeter(123, 1.26, 2).isSame(TrackMeter(123, 1.20, 2), 1))
    }

    @Test
    fun floorWorks() {
        assertEquals(TrackMeter(123, 1.5, 1), TrackMeter(123, 1.567, 3).floor(1))
        assertEquals(TrackMeter(123, 1.5, 1), TrackMeter(123, 1.5, 1).floor(1))
        assertEquals(TrackMeter(123, 1.5, 1), TrackMeter(123, 1.50, 2).floor(1))
    }

    @Test
    fun ceilWorks() {
        assertEquals(TrackMeter(123, 1.6, 1), TrackMeter(123, 1.567, 3).ceil(1))
        assertEquals(TrackMeter(123, 1.5, 1), TrackMeter(123, 1.5, 1).ceil(1))
        assertEquals(TrackMeter(123, 1.5, 1), TrackMeter(123, 1.50, 2).ceil(1))
    }

    @Test
    fun roundDecimalsWork() {
        assertEquals(TrackMeter(123, 1.6, 1), TrackMeter(123, 1.567, 3).round(1))
    }

    @Test
    fun addressesWorkInRanges() {
        assertTrue(TrackMeter(704, 87) in TrackMeter(704, 85.123, 3)..TrackMeter(704, 87.123, 3))
        assertTrue(TrackMeter(704, 87) in TrackMeter(704, 87)..TrackMeter(704, 87))
        assertTrue(TrackMeter(704, 87) in TrackMeter(704, 9)..TrackMeter(704, 123))
        assertTrue(TrackMeter(704, 87) in TrackMeter(703, 999.123, 3)..TrackMeter(705, 7.123, 3))
    }

    @Test
    fun addressFormattingWorks() {
        assertEquals("0001+0000", TrackMeter(1, 0).toString())
        assertEquals("0001+0001", TrackMeter(1, 1).toString())
        assertEquals("0001+0001", TrackMeter(1, 1.123, 0).toString())
        assertEquals("0001+0002", TrackMeter(1, 1.523, 0).toString())
        assertEquals("0001+0001.111", TrackMeter(1, 1.111111, 3).toString())
        assertEquals("9999+9999.999999", TrackMeter(9999, 9999.999999, 6).toString())
        assertEquals("1234+0123.45", TrackMeter("1234+123.45").toString())
        assertEquals("1234+1234.56", TrackMeter("1234+1234.56").toString())
        assertEquals("0000+0000.000", TrackMeter("0000+0000.000").toString())
        assertEquals("0000+0000", TrackMeter("0000+0000").toString())
    }

    @Test
    fun addressRangesWork() {
        assertTrue(TrackMeter("0001+0002.0") in TrackMeter("0001+0002")..TrackMeter("0001+0003"))
        assertTrue(TrackMeter("0001+0002.521") in TrackMeter("0001+0002")..TrackMeter("0001+0003"))
        assertTrue(TrackMeter("0001+0003.0") in TrackMeter("0001+0002")..TrackMeter("0001+0003"))
        assertTrue(TrackMeter("0001+0002") in TrackMeter("0001+0002.000")..TrackMeter("0001+0003.000"))
        assertTrue(TrackMeter("0001+0003") in TrackMeter("0001+0002.000")..TrackMeter("0001+0003.000"))

        assertFalse(TrackMeter("0001+0001.999") in TrackMeter("0001+0002")..TrackMeter("0001+0003"))
        assertFalse(TrackMeter("0001+0003.001") in TrackMeter("0001+0002")..TrackMeter("0001+0003"))
        assertFalse(TrackMeter("0001+0001.999") in TrackMeter("0001+0002.0")..TrackMeter("0001+0003.0"))
        assertFalse(TrackMeter("0001+0003.001") in TrackMeter("0001+0002.0")..TrackMeter("0001+0003.0"))
    }

    @Test
    fun stripTrailingZeroesWorks() {
        assertNotEquals(TrackMeter(123, 321), TrackMeter(123, 321.000, 3))
        assertEquals(TrackMeter(123, 321), TrackMeter(123, 321.000, 3).stripTrailingZeroes())
        assertEquals(TrackMeter(123, 300), TrackMeter(123, 300.000, 3).stripTrailingZeroes())
    }

    @Test
    fun `hasIntegerPrecision works`() {
        assertFalse(TrackMeter("1234+1234.1234").hasIntegerPrecision())
        assertFalse(TrackMeter("1234+1234.0000").hasIntegerPrecision())
        assertTrue(TrackMeter("1234+1234").hasIntegerPrecision())
    }

    @Test
    fun `matchesIntegerValue works`() {
        assertFalse(TrackMeter("1234+1234.1234").matchesIntegerValue())
        assertTrue(TrackMeter("1234+1234.0000").matchesIntegerValue())
        assertTrue(TrackMeter("1234+1234").matchesIntegerValue())
    }

    @Test
    fun `negative km numbers are not allowed`() {
        assertThrows<IllegalArgumentException> { KmNumber(-1) }
    }

    @Test
    fun `5 digit km numbers are not allowed`() {
        assertThrows<IllegalArgumentException> { KmNumber(10000) }
    }

    @Test
    fun `negative track meters are not allowed`() {
        assertThrows<IllegalArgumentException> { TrackMeter(KmNumber.ZERO, -1) }
        assertThrows<IllegalArgumentException> { TrackMeter(KmNumber.ZERO, 1) - BigDecimal.TEN }
        assertThrows<IllegalArgumentException> { TrackMeter(KmNumber.ZERO, -0.000001, 6) }
        assertThrows<IllegalArgumentException> { TrackMeter(KmNumber.ZERO, -0.0009, 3) }
        assertEquals(TrackMeter.ZERO.round(3), TrackMeter(KmNumber.ZERO, -0.0001, 3))
    }

    @Test
    fun `5 digit track meters are not allowed`() {
        assertThrows<IllegalArgumentException> { TrackMeter(KmNumber.ZERO, 10000) }
        assertThrows<IllegalArgumentException> { TrackMeter(KmNumber.ZERO, 9999) + BigDecimal.TEN }
        assertThrows<IllegalArgumentException> { TrackMeter(KmNumber.ZERO, 9999.999999, 3) }
        assertEquals(TrackMeter("0000+9999.999"), TrackMeter(KmNumber.ZERO, 9999.9991, 3))
    }
}
