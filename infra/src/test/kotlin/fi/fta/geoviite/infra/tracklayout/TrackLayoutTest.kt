package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DesignLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.SwitchNameParts
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        val originalSegmentStart = locationTrackM(123.111)
        val (startSlice, startSliceM) = original.slice(originalSegmentStart, 0, 2)!!
        assertEquals(3, startSlice.segmentPoints.size)
        assertApproximatelyEquals(original.segmentPoints[0], startSlice.segmentPoints[0])
        assertApproximatelyEquals(original.segmentPoints[2], startSlice.segmentPoints.last())
        assertEquals(original.segmentPoints[2].m.distance, startSlice.length, 0.0001)
        assertEquals(
            Range(originalSegmentStart, originalSegmentStart + original.segmentPoints[2].m.distance),
            startSliceM
        )

        val (endSlice, endSliceM) = original.slice(originalSegmentStart, 8, 9)!!
        assertEquals(2, endSlice.segmentPoints.size)
        assertApproximatelyEquals(original.segmentPoints[8].copy(m = LineM(0.0)), endSlice.segmentPoints[0])
        assertApproximatelyEquals(
            original.segmentPoints[9].copy(m = LineM(original.length - original.segmentPoints[8].m.distance)),
            endSlice.segmentPoints.last(),
        )
        assertEquals(
            original.segmentPoints[9].m.distance - original.segmentPoints[8].m.distance,
            endSlice.length,
            0.0001
        )
        assertEquals(
            Range(
                originalSegmentStart + original.segmentPoints[8].m.distance,
                originalSegmentStart + original.segmentPoints[9].m.distance,
            ),
            endSliceM,
        )

        val (midSlice, midSliceM) = original.slice(originalSegmentStart, 1, 8)!!
        assertEquals(8, midSlice.segmentPoints.size)
        assertApproximatelyEquals(original.segmentPoints[1].copy(m = LineM(0.0)), midSlice.segmentPoints[0])
        assertApproximatelyEquals(
            original.segmentPoints[8].copy(m = original.segmentPoints[8].m - original.segmentPoints[1].m),
            midSlice.segmentPoints.last(),
        )
        assertEquals(
            original.segmentPoints[8].m.distance - original.segmentPoints[1].m.distance,
            midSlice.length,
            0.0001
        )
        assertEquals(
            Range(
                original.segmentPoints[1].m.segmentToAlignmentM(originalSegmentStart),
                original.segmentPoints[8].m.segmentToAlignmentM(originalSegmentStart),
            ),
            midSliceM,
        )
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

    @Test
    fun `SwitchName structure parsing works`() {
        assertEquals(parsedSwitchName("ABC", "V123"), SwitchNameParts.tryParse(SwitchName("ABC V0123")))
        assertEquals(parsedSwitchName("ABC", "V123"), SwitchNameParts.tryParse(SwitchName("ABC   V0123")))
        assertEquals(parsedSwitchName("ABC", "V123/124"), SwitchNameParts.tryParse(SwitchName("ABC V0123/V0124")))
        assertEquals(parsedSwitchName("ABC", "V123/124"), SwitchNameParts.tryParse(SwitchName("ABC V0123/0124")))
        assertEquals(parsedSwitchName("ABC", "V001/002"), SwitchNameParts.tryParse(SwitchName("ABC V000001/02")))
        assertEquals(parsedSwitchName("ABC", "V123"), SwitchNameParts.tryParse(SwitchName("ABC__V0123")))
        assertEquals(parsedSwitchName("ABC", "V003"), SwitchNameParts.tryParse(SwitchName("ABC  V00003")))
        assertNull(SwitchNameParts.tryParse(SwitchName("ABC--V0123")))
        assertNull(SwitchNameParts.tryParse(SwitchName("ABC V0123/V0124/V0125")))
        assertNull(SwitchNameParts.tryParse(SwitchName("ABC V0123/0124/0125")))
        assertNull(SwitchNameParts.tryParse(SwitchName("ABC VASDF")))
    }

    private fun <T : LayoutAsset<T>> assertRoundTrip(version: LayoutRowVersion<T>) {
        val text = version.toString()
        val versionAgain = LayoutRowVersion<T>(text)
        val textAgain = versionAgain.toString()
        assertEquals(version, versionAgain)
        assertEquals(text, textAgain)
    }
}
