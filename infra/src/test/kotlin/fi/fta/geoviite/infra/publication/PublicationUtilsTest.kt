package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PublicationUtilsTest {
    @Test
    fun `Comparing values works`() {
        val a = 1
        val b = 2
        val change = compareChange({ true }, a, b, { it }, PropKey("test"))
        assertNotNull(change)
        assertEquals(change!!.value.oldValue, a)
        assertEquals(change.value.newValue, b)
        assertEquals(change.propKey.key.toString(), "test")
    }

    @Test
    fun `Returns null if predicate is false`() {
        val change = compareChange({ false }, 1, 2, { it }, PropKey("test"))
        assertNull(change)
    }

    @Test
    fun `Values are transformed`() {
        val change = compareChange({ true }, 1, 2, { it.toString() }, PropKey("test"))
        assertNotNull(change)
        assertEquals(change!!.value.oldValue, "1")
        assertEquals(change.value.newValue, "2")
    }

    @Test
    fun `Lazy values are fetched if predicate is true`() {
        var a = 1
        var b = 2
        val aMutator = { a++ }
        val bMutator = { b++ }
        val change = compareChangeLazy({ true }, aMutator, bMutator, { it }, PropKey("test"))
        assertNotNull(change)
        assertEquals(a, 2)
        assertEquals(b, 3)
    }

    @Test
    fun `Lazy values are not fetched if predicate is false`() {
        var a = 1
        var b = 2
        val aMutator = { a++ }
        val bMutator = { b++ }
        val change = compareChangeLazy({ false }, aMutator, bMutator, { it }, PropKey("test"))
        assertNull(change)
        assertEquals(a, 1)
        assertEquals(b, 2)
    }

    @Test
    fun `Double comparison works properly`() {
        val changeTrue = compareDouble(1.0, 2.0, 0.2, { it }, PropKey("test"))
        val changeFalse = compareDouble(1.0, 1.1, 0.2, { it }, PropKey("test"))
        assertNotNull(changeTrue)
        assertNull(changeFalse)
    }

    @Test
    fun `compareChangeValues works`() {
        val changeTrue = compareChangeValues(1, 2, { it }, PropKey("test"))
        val changeFalse = compareChangeValues(1, 1, { it }, PropKey("test"))
        assertNotNull(changeTrue)
        assertNull(changeFalse)
    }

    // formatLocation tests
    @Test
    fun `formatLocation works`() {
        val location = Point(1.0, 2.0001)
        val formatted = formatLocation(location)
        assertEquals(formatted, "1.000 E, 2.000 N")
    }
}
