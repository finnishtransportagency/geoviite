package fi.fta.geoviite.infra.publication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PublicationLogComparisonTest {
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
    fun `Length comparison works properly`() {
        val changeTrue = compareLength(1.0, 2.0, 0.2, { it }, PropKey("test"))
        val changeFalse = compareLength(1.0, 1.1, 0.2, { it }, PropKey("test"))
        assertNotNull(changeTrue)
        assertNull(changeFalse)
    }

    @Test
    fun `compareChangeValues works`() {
        val changeTrue = compareChangeValues(Change(1, 2), { it }, PropKey("test"))
        val changeFalse = compareChangeValues(Change(1, 1), { it }, PropKey("test"))
        assertNotNull(changeTrue)
        assertNull(changeFalse)
    }
}
