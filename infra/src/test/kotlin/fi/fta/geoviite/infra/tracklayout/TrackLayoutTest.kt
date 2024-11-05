package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DesignLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        assertEquals(3, startSlice.alignmentPoints.size)
        assertApproximatelyEquals(original.alignmentPoints[0].copy(m = startSliceStart), startSlice.alignmentPoints[0])
        assertApproximatelyEquals(
            original.alignmentPoints[2].copy(m = original.alignmentPoints[2].m + startSliceStart),
            startSlice.alignmentPoints[startSlice.alignmentPoints.lastIndex],
        )
        assertEquals(startSliceStart, startSlice.startM, 0.0001)
        assertEquals(original.alignmentPoints[2].m, startSlice.length, 0.0001)

        val endSliceStart = 123.222
        val endSlice = original.slice(8, 9, endSliceStart)!!
        assertEquals(2, endSlice.alignmentPoints.size)
        assertApproximatelyEquals(original.alignmentPoints[8].copy(m = endSliceStart), endSlice.alignmentPoints[0])
        assertApproximatelyEquals(
            original.alignmentPoints[9].copy(m = endSliceStart + original.length - original.alignmentPoints[8].m),
            endSlice.alignmentPoints[endSlice.alignmentPoints.lastIndex],
        )
        assertEquals(endSliceStart, endSlice.startM, 0.0001)
        assertEquals(original.alignmentPoints[9].m - original.alignmentPoints[8].m, endSlice.length, 0.0001)

        val midSliceStart = 123.333
        val midSlice = original.slice(1, 8, midSliceStart)!!
        assertEquals(8, midSlice.alignmentPoints.size)
        assertApproximatelyEquals(original.alignmentPoints[1].copy(m = midSliceStart), midSlice.alignmentPoints[0])
        assertApproximatelyEquals(
            original.alignmentPoints[8].copy(
                m = midSliceStart + original.alignmentPoints[8].m - original.alignmentPoints[1].m
            ),
            midSlice.alignmentPoints[midSlice.alignmentPoints.lastIndex],
        )
        assertEquals(midSliceStart, midSlice.startM, 0.0001)
        assertEquals(original.alignmentPoints[8].m - original.alignmentPoints[1].m, midSlice.length, 0.0001)
    }

    @Test
    fun nonContinuousSegmentsAreNotAllowed() {
        assertThrows<IllegalArgumentException> {
            alignment(
                segment(toSegmentPoints(Point(0.0, 0.0), Point(0.0, 100.0))),
                segment(toSegmentPoints(Point(1.0, 100.0), Point(1.0, 200.0))),
            )
        }
    }

    @Test
    fun `LayoutRowVersion toString() and parse roundtripping`() {
        assertRoundTrip(LayoutRowVersion(IntId<LocationTrack>(10), LayoutBranch.main.official, 4))
        assertRoundTrip(LayoutRowVersion(IntId<LocationTrack>(10), LayoutBranch.main.draft, 4))
        assertRoundTrip(
            LayoutRowVersion(IntId<LocationTrack>(10), DesignLayoutContext.of(IntId(4), PublicationState.OFFICIAL), 4)
        )
        assertRoundTrip(
            LayoutRowVersion(IntId<LocationTrack>(10), DesignLayoutContext.of(IntId(4), PublicationState.DRAFT), 4)
        )
    }

    private fun <T> assertRoundTrip(version: LayoutRowVersion<T>) {
        val text = version.toString()
        val versionAgain = LayoutRowVersion<T>(text)
        val textAgain = versionAgain.toString()
        kotlin.test.assertEquals(version, versionAgain)
        kotlin.test.assertEquals(text, textAgain)
    }
}
