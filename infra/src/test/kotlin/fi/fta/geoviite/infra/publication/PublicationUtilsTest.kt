package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.geocodingContext
import fi.fta.geoviite.infra.tracklayout.segment
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

    @Test
    fun `formatLocation works`() {
        val location = Point(1.0, 2.0001)
        val formatted = formatLocation(location)
        assertEquals(formatted, "1.000 E, 2.000 N")
    }

    @Test
    fun `summarizeAlignmentChanges detects change in the middle of track`() {
        val oldAlignment = alignment(
            segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0))
        )
        val newAlignment = alignment(
            segment(Point(0.0, 0.0), Point(1.0, 1.2), Point(2.0, 1.4), Point(3.0, 1.2), Point(4.0, 0.0))
        )

        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldAlignment, newAlignment)
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals(2.0, row.changedLengthM)
        assertEquals(row.maxDistance, 1.4, 0.1)
    }

    @Test
    fun `summarizeAlignmentChanges detects track shortened by removing segment at start`() {
        val oldAlignment = alignment(
            segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0)),
            segment(Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0)),
        )
        val newAlignment = alignment(oldAlignment.segments[1])
        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldAlignment, newAlignment)
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals(1.0, row.changedLengthM, 0.1)
    }

    @Test
    fun `summarizeAlignmentChanges detects track shortened by removing segment at end`() {
        val oldAlignment = alignment(
            segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0)),
            segment(Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0)),
        )
        val newAlignment = alignment(oldAlignment.segments[0])
        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldAlignment, newAlignment)
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals(1.0, row.changedLengthM, 0.1)
    }

    @Test
    fun `summarizeAlignmentChanges detects multiple changes based on segment identity match`() {
        val oldAlignment = alignment(
            ((0..4).map { segmentIx ->
                segment(*(0..4).map { pointIx ->
                    Point(segmentIx * 4.0 + pointIx * 1.0, 0.0)
                }.toTypedArray())
            })
        )
        val newAlignment = alignment(
            oldAlignment.segments[0],
            segment(Point(4.0, 0.0), Point(5.0, 1.2), Point(6.0, 1.5), Point(7.0, 1.2), Point(8.0, 0.0)),
            oldAlignment.segments[2],
            segment(Point(12.0, 0.0), Point(13.0, 1.2), Point(15.0, 2.0)),
            segment(Point(15.0, 2.0), Point(16.0, 3.0), Point(17.0, 4.0), Point(18.0, 5.0)),
        )

        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldAlignment, newAlignment)
        print(result)
        assertEquals(2, result.size)
        assertEquals(1.5, result[0].maxDistance, 0.1)
        assertEquals(2.0, result[0].changedLengthM)
        assertEquals(5.0, result[1].maxDistance, 0.1)
        assertEquals(7.0, result[1].changedLengthM)
    }

    private fun xAxisGeocodingContext() = geocodingContext((0..30).map { x -> Point(x.toDouble(), 0.0)})
}

