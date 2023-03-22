package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.tracklayout.ISegmentGeometry.PointSeekResult
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.test.assertEquals

class LayoutGeometryTest {

    private val accuracy = 0.000000001

    @Test
    fun shouldReturnSegmentAsIsWhenDistanceIsOverSegmentsMValues() {
        val segment = segment(
            point(0.0, 0.0, 1.0),
            point(10.0, 0.0, 11.0),
            point(20.0, 0.0, 21.0),
        )

        val (startSegment, endSegment) = segment.splitAtM(30.0, 0.0)
        assertEquals(segment, startSegment)
        assertNull(endSegment)

        val (startSegment2, endSegment2) = segment.splitAtM(0.0, 0.0)
        assertEquals(segment, startSegment2)
        assertNull(endSegment2)
    }

    @Test
    fun shouldSplitFromSegmentPoint() {
        val segment = segment(
            point(0.0, 0.0, 1.0),
            point(10.0, 0.0, 11.0),
            point(20.0, 0.0, 21.0),
        )

        val (startSegment, endSegment) = segment.splitAtM(11.0, 0.0001)
        assertEquals(2, startSegment.points.size)
        assertEquals(0.0, startSegment.points.first().x)
        assertEquals(11.0, startSegment.points.last().m)

        assertNotNull(endSegment)
        assertEquals(2, endSegment!!.points.size)
        assertEquals(10.0, endSegment.points.first().x)
        assertEquals(20.0, endSegment.points.last().x)
        assertEquals(11.0, endSegment.points.first().m)
        assertEquals(21.0, endSegment.points.last().m)
    }

    @Test
    fun splitSegmentMetadataShouldBeCorrect() {
        val segment = LayoutSegment(
            geometry = SegmentGeometry(
                points = listOf(
                    point(10.0, 10.0, 10.0),
                    point(20.0, 20.0, 10.0 + hypot(10.0, 10.0)),
                ),
                resolution = 2,
            ),
            sourceId = StringId(),
            sourceStart = 15.0,
            switchId = StringId(),
            startJointNumber = JointNumber(2),
            endJointNumber = JointNumber(2),
            source = GeometrySource.IMPORTED,
        )

        val (startSegment, endSegment) = segment.splitAtM(10.0, 0.5)
        assertNotNull(endSegment)
        endSegment!!
        assertEquals(segment.sourceId, startSegment.sourceId)
        assertEquals(segment.sourceId, endSegment.sourceId)
        assertEquals(segment.sourceStart, startSegment.sourceStart)
        assertEquals(segment.sourceStart?.plus(startSegment.length), endSegment.sourceStart)
        assertEquals(segment.resolution, startSegment.resolution)
        assertEquals(segment.resolution, endSegment.resolution)
        assertEquals(segment.switchId, startSegment.switchId)
        assertEquals(segment.switchId, endSegment.switchId)
        assertEquals(segment.startJointNumber, startSegment.startJointNumber)
        assertEquals(segment.startJointNumber, endSegment.startJointNumber)
        assertEquals(segment.endJointNumber, startSegment.endJointNumber)
        assertEquals(segment.endJointNumber, endSegment.endJointNumber)
        assertEquals(segment.start, startSegment.start)
        assertEquals(segment.start+startSegment.length, endSegment.start)
        assertEquals(segment.source, startSegment.source)
        assertEquals(segment.source, endSegment.source)
    }

    @Test
    fun shouldSplitFromPointWithinTolerance() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )

        val (startSegment, endSegment) = segment.splitAtM(9.9, 0.5)
        assertEquals(2, startSegment.points.size)
        assertEquals(0.0, startSegment.points.first().x)
        assertEquals(10.0, startSegment.points.last().x)

        assertNotNull(endSegment)
        assertEquals(2, endSegment!!.points.size)
        assertEquals(10.0, endSegment.points.first().x)
        assertEquals(20.0, endSegment.points.last().x)
        assertEquals(0.0, endSegment.points.first().m)
        assertEquals(10.0, endSegment.points.last().m)
    }

    @Test
    fun shouldNotSplitWhenEndPointIsWithinTolerance() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )

        val (startSegment, endSegment) = segment.splitAtM(20.5, 1.0)
        assertNull(endSegment)
        assertEquals(3, startSegment.points.size)
        assertEquals(0.0, startSegment.points.first().m)
        assertEquals(20.0, startSegment.points.last().m)
    }

    @Test
    fun shouldSplitFromANewPointWhenNonePointsWithinTolerance() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )

        val (startSegment1, endSegment1) = segment.splitAtM(5.0, 0.5)
        assertEquals(2, startSegment1.points.size)
        assertEquals(0.0, startSegment1.points.first().m)
        assertEquals(5.0, startSegment1.points.last().m)
        assertEquals(0.0, startSegment1.points.first().x)
        assertEquals(5.0, startSegment1.points.last().x)

        assertNotNull(endSegment1)
        assertEquals(3, endSegment1!!.points.size)
        assertEquals(5.0, endSegment1.points.first().m)
        assertEquals(20.0, endSegment1.points.last().m)
        assertEquals(5.0, endSegment1.points.first().x)
        assertEquals(20.0, endSegment1.points.last().x)

        val (startSegment2, endSegment2) = segment.splitAtM(15.0, 0.5)
        assertEquals(3, startSegment2.points.size)
        assertEquals(0.0, startSegment2.points.first().m)
        assertEquals(15.0, startSegment2.points.last().m)
        assertEquals(0.0, startSegment2.points.first().x)
        assertEquals(15.0, startSegment2.points.last().x)

        assertNotNull(endSegment2)
        assertEquals(2, endSegment2!!.points.size)
        assertEquals(15.0, endSegment2.points.first().m)
        assertEquals(20.0, endSegment2.points.last().m)
        assertEquals(15.0, endSegment2.points.first().x)
        assertEquals(20.0, endSegment2.points.last().x)
    }

    @Test
    fun seekSegmentPointAtMWorks() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )
        assertEquals(
            PointSeekResult(point(0.0, 0.0, 0.0), 0, true),
            segment.seekPointAtM(0.0),
        )
        assertEquals(
            PointSeekResult(point(10.0, 0.0, 10.0), 1, true),
            segment.seekPointAtM(10.0),
        )
        assertEquals(
            PointSeekResult(point(20.0, 0.0, 20.0), 2, true),
            segment.seekPointAtM(20.0),
        )

        assertEquals(
            PointSeekResult(point(0.0, 0.0, 0.0), 0, true),
            segment.seekPointAtM(-5.0),
        )
        assertEquals(
            PointSeekResult(point(20.0, 0.0, 20.0), 2, true),
            segment.seekPointAtM(25.0),
        )

        assertEquals(
            PointSeekResult(point(5.0, 0.0, 5.0), 1, false),
            segment.seekPointAtM(5.0),
        )
        assertEquals(
            PointSeekResult(point(13.0, 0.0, 13.0), 2, false),
            segment.seekPointAtM(13.0),
        )
    }

    @Test
    fun segmentPointAtLengthSnapsCorrectly() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )
        assertEquals(
            PointSeekResult(point(0.05, 0.0, 0.05), 1, false),
            segment.seekPointAtM(0.05, 0.0),
        )
        assertEquals(
            PointSeekResult(point(0.0, 0.0, 0.0), 0, true),
            segment.seekPointAtM(0.05, 0.1),
        )
        assertEquals(
            PointSeekResult(point(0.15, 0.0, 0.15), 1, false),
            segment.seekPointAtM(0.15, 0.1),
        )

        assertEquals(
            PointSeekResult(point(9.95, 0.0, 9.95), 1, false),
            segment.seekPointAtM(9.95, 0.0),
        )
        assertEquals(
            PointSeekResult(point(10.0, 0.0, 10.0), 1, true),
            segment.seekPointAtM(9.95, 0.1),
        )
        assertEquals(
            PointSeekResult(point(9.85, 0.0, 9.85), 1, false),
            segment.seekPointAtM(9.85, 0.1),
        )
        assertEquals(
            PointSeekResult(point(10.05, 0.0, 10.05), 2, false),
            segment.seekPointAtM(10.05, 0.0),
        )
        assertEquals(
            PointSeekResult(point(10.0, 0.0, 10.0), 1, true),
            segment.seekPointAtM(10.05, 0.1),
        )
        assertEquals(
            PointSeekResult(point(10.15, 0.0, 10.15), 2, false),
            segment.seekPointAtM(10.15, 0.1),
        )

        assertEquals(
            PointSeekResult(point(19.95, 0.0, 19.95), 2, false),
            segment.seekPointAtM(19.95, 0.0),
        )
        assertEquals(
            PointSeekResult(point(20.0, 0.0, 20.0), 2, true),
            segment.seekPointAtM(19.95, 0.1),
        )
        assertEquals(
            PointSeekResult(point(19.85, 0.0, 19.85), 2, false),
            segment.seekPointAtM(19.85, 0.1),
        )
    }

    @Test
    fun alignmentPointAtLengthWorks() {
        val diagonalLength = hypot(5.0, 5.0)
        val alignment = alignment(
            segment(
                point(0.0, 0.0, 0.0),
                point(10.0, 0.0, 10.0),
            ),
            segment(
                point(10.0, 0.0, 10.0),
                point(15.0, 5.0, 10.0 + diagonalLength),
                point(15.0, 15.0, 20.0 + diagonalLength),
            ),
        )

        assertApproximatelyEquals(
            point(0.0, 0.0, 0.0),
            alignment.getPointAtM(0.0)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(10.0, 0.0, 10.0),
            alignment.getPointAtM(10.0)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(15.0, 5.0, 10.0 + diagonalLength),
            alignment.getPointAtM(10.0 + diagonalLength)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(15.0, 15.0, 20.0 + diagonalLength),
            alignment.getPointAtM(20.0 + diagonalLength)!!,
            accuracy,
        )

        assertApproximatelyEquals(
            point(0.0, 0.0, 0.0),
            alignment.getPointAtM(-5.0)!!,
            accuracy)
        assertApproximatelyEquals(
            point(15.0, 15.0, 20.0+diagonalLength),
            alignment.getPointAtM(50.0)!!,
            accuracy,
        )

        assertApproximatelyEquals(
            point(5.0, 0.0, 5.0),
            alignment.getPointAtM(5.0)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(13.0, 3.0, 10 + hypot(3.0, 3.0)),
            alignment.getPointAtM(10 + hypot(3.0, 3.0))!!,
            accuracy,
        )
    }

    @Test
    fun sliceWorksWithNewStartM() {
        val pointInterval = hypot(10.0, 10.0)
        val original = LayoutSegment(
            geometry = SegmentGeometry(
                points = listOf(
                    point(10.0, 10.0, 10.0),
                    point(20.0, 20.0, 10.0 + pointInterval),
                    point(30.0, 30.0, 10.0 + 2*pointInterval),
                    point(40.0, 40.0, 10.0 + 3*pointInterval),
                ),
                resolution = 2,
            ),
            sourceId = StringId(),
            sourceStart = 15.0,
            switchId = StringId(),
            startJointNumber = JointNumber(2),
            endJointNumber = JointNumber(2),
            source = GeometrySource.IMPORTED,
        )

        val newStart = 11.0
        val slice = original.slice(1, 2, newStart)
        assertNotNull(slice)
        slice!!

        assertEquals(slice.sourceId, original.sourceId)
        assertEquals(slice.sourceStart, original.sourceStart?.plus(pointInterval))
        assertEquals(slice.resolution, original.resolution)
        assertEquals(slice.switchId, original.switchId)
        assertEquals(slice.startJointNumber, original.startJointNumber)
        assertEquals(slice.endJointNumber, original.endJointNumber)
        assertEquals(newStart, slice.start)
        assertEquals(original.source, slice.source)
        assertEquals(2, slice.points.size)
        assertEquals(original.points[1].x, slice.points[0].x)
        assertEquals(original.points[1].y, slice.points[0].y)
        assertEquals(newStart, slice.points[0].m)
        assertEquals(original.points[2].x, slice.points[1].x)
        assertEquals(original.points[2].y, slice.points[1].y)
        assertEquals(newStart + pointInterval, slice.points[1].m, 0.00001)
    }

    @Test
    fun sliceWorksWithDefaultStartM() {
        val pointInterval = hypot(10.0, 10.0)
        val original = LayoutSegment(
            geometry = SegmentGeometry(
                points = listOf(
                    point(10.0, 10.0, 10.0),
                    point(20.0, 20.0, 10.0 + pointInterval),
                    point(30.0, 30.0, 10.0 + 2*pointInterval),
                    point(40.0, 40.0, 10.0 + 3*pointInterval),
                ),
                resolution = 2,
            ),
            sourceId = StringId(),
            sourceStart = 15.0,
            switchId = StringId(),
            startJointNumber = JointNumber(2),
            endJointNumber = JointNumber(2),
            source = GeometrySource.IMPORTED,
        )

        val slice = original.slice(1, 2)
        assertNotNull(slice)
        slice!!

        assertEquals(slice.sourceId, original.sourceId)
        assertEquals(slice.sourceStart, original.sourceStart?.plus(pointInterval))
        assertEquals(slice.resolution, original.resolution)
        assertEquals(slice.switchId, original.switchId)
        assertEquals(slice.startJointNumber, original.startJointNumber)
        assertEquals(slice.endJointNumber, original.endJointNumber)
        assertEquals(original.points[1].m, slice.start)
        assertEquals(original.source, slice.source)
        assertEquals(2, slice.points.size)
        assertEquals(original.points[1].x, slice.points[0].x)
        assertEquals(original.points[1].y, slice.points[0].y)
        assertEquals(original.points[1].m, slice.points[0].m)
        assertEquals(original.points[2].x, slice.points[1].x)
        assertEquals(original.points[2].y, slice.points[1].y)
        assertEquals(original.points[2].m, slice.points[1].m)
    }
}
