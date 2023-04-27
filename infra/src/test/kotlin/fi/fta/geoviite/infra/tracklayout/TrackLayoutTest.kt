package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

private const val SEED = 123321L

class TrackLayoutTest {

    private val rand = Random(SEED)

    @BeforeEach
    fun initRandom() {
        // Fix random to a specific state for each test
        rand.setSeed(SEED)
    }

    @Test
    fun slicingSegmentWorks() {
        val original = segment(10, -10.0, 10.0, -10.0, 10.0)

        val startSliceStart = 123.111
        val startSlice = original.slice(0, 2, startSliceStart)!!
        assertEquals(3, startSlice.points.size)
        assertApproximatelyEquals(original.points[0].copy(m = startSliceStart), startSlice.points[0])
        assertApproximatelyEquals(original.points[2].copy(m = original.points[2].m + startSliceStart), startSlice.points[startSlice.points.lastIndex])
        assertEquals(startSliceStart, startSlice.startM, 0.0001)
        assertEquals(original.points[2].m, startSlice.length, 0.0001)

        val endSliceStart = 123.222
        val endSlice = original.slice(8, 9, endSliceStart)!!
        assertEquals(2, endSlice.points.size)
        assertApproximatelyEquals(original.points[8].copy(m = endSliceStart), endSlice.points[0])
        assertApproximatelyEquals(
            original.points[9].copy(m = endSliceStart + original.length - original.points[8].m),
            endSlice.points[endSlice.points.lastIndex],
        )
        assertEquals(endSliceStart, endSlice.startM, 0.0001)
        assertEquals(original.points[9].m - original.points[8].m, endSlice.length, 0.0001)

        val midSliceStart = 123.333
        val midSlice = original.slice(1, 8, midSliceStart)!!
        assertEquals(8, midSlice.points.size)
        assertApproximatelyEquals(original.points[1].copy(m = midSliceStart), midSlice.points[0])
        assertApproximatelyEquals(
            original.points[8].copy(m = midSliceStart + original.points[8].m - original.points[1].m),
            midSlice.points[midSlice.points.lastIndex]
        )
        assertEquals(midSliceStart, midSlice.startM, 0.0001)
        assertEquals(original.points[8].m - original.points[1].m, midSlice.length, 0.0001)
    }

    @Test
    fun nonContinuousSegmentsAreNotAllowed() {
        assertThrows<IllegalArgumentException> {
            alignment(
                segment(toTrackLayoutPoints(
                    Point(0.0, 0.0),
                    Point(0.0, 100.0),
                )),
                segment(toTrackLayoutPoints(
                    Point(1.0, 100.0),
                    Point(1.0, 200.0),
                )),
            )
        }
    }
}
