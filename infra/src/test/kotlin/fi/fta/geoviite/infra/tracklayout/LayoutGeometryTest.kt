package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
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
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )

        val (startSegment, endSegment) = segment.splitAtM(30.0, 0.0)
        assertNull(endSegment)
        assertEquals(3, startSegment.points.size)
        assertEquals(0.0, startSegment.points.first().m)
        assertEquals(20.0, startSegment.points.last().m)
    }

    @Test
    fun shouldSplitFromSegmentPoint() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )

        val (startSegment, endSegment) = segment.splitAtM(10.0, 0.0001)
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
    fun splitSegmentMetadataShouldBeCorrect() {
        val segment = LayoutSegment(
            points = listOf(
                point(10.0, 10.0, 0.0),
                point(20.0, 20.0, hypot(10.0, 10.0)),
            ),
            sourceId = StringId(),
            sourceStart = 15.0,
            resolution = 2,
            switchId = StringId(),
            startJointNumber = JointNumber(2),
            endJointNumber = JointNumber(2),
            start = 10.0,
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
        assertEquals(0.0, endSegment1.points.first().m)
        assertEquals(15.0, endSegment1.points.last().m)
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
        assertEquals(0.0, endSegment2.points.first().m)
        assertEquals(5.0, endSegment2.points.last().m)
        assertEquals(15.0, endSegment2.points.first().x)
        assertEquals(20.0, endSegment2.points.last().x)
    }

    @Test
    fun segmentPointAtLengthWorks() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )
        assertEquals(point(0.0, 0.0, 0.0), segment.getPointAtLength(0.0))
        assertEquals(point(10.0, 0.0, 10.0), segment.getPointAtLength(10.0))
        assertEquals(point(20.0, 0.0, 20.0), segment.getPointAtLength(20.0))

        assertEquals(point(0.0, 0.0, 0.0), segment.getPointAtLength(-5.0))
        assertEquals(point(20.0, 0.0, 20.0), segment.getPointAtLength(25.0))

        assertEquals(point(5.0, 0.0, 5.0), segment.getPointAtLength(5.0))
        assertEquals(point(13.0, 0.0, 13.0), segment.getPointAtLength(13.0))
    }

    @Test
    fun segmentPointAtLengthSnapsCorrectly() {
        val segment = segment(
            point(0.0, 0.0, 0.0),
            point(10.0, 0.0, 10.0),
            point(20.0, 0.0, 20.0),
        )
        assertEquals(point(0.05, 0.0, 0.05), segment.getPointAtLength(0.05, 0.0))
        assertEquals(point(0.0, 0.0, 0.0), segment.getPointAtLength(0.05, 0.1))
        assertEquals(point(0.15, 0.0, 0.15), segment.getPointAtLength(0.15, 0.1))

        assertEquals(point(9.95, 0.0, 9.95), segment.getPointAtLength(9.95, 0.0))
        assertEquals(point(10.0, 0.0, 10.0), segment.getPointAtLength(9.95, 0.1))
        assertEquals(point(9.85, 0.0, 9.85), segment.getPointAtLength(9.85, 0.1))
        assertEquals(point(10.05, 0.0, 10.05), segment.getPointAtLength(10.05, 0.0))
        assertEquals(point(10.0, 0.0, 10.0), segment.getPointAtLength(10.05, 0.1))
        assertEquals(point(10.15, 0.0, 10.15), segment.getPointAtLength(10.15, 0.1))

        assertEquals(point(19.95, 0.0, 19.95), segment.getPointAtLength(19.95, 0.0))
        assertEquals(point(20.0, 0.0, 20.0), segment.getPointAtLength(19.95, 0.1))
        assertEquals(point(19.85, 0.0, 19.85), segment.getPointAtLength(19.85, 0.1))
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
                point(10.0, 0.0, 0.0),
                point(15.0, 5.0, diagonalLength),
                point(15.0, 15.0, 10.0 + diagonalLength),
            ),
        )

        assertApproximatelyEquals(
            point(0.0, 0.0, 0.0),
            alignment.getPointAtLength(0.0)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(10.0, 0.0, 0.0),
            alignment.getPointAtLength(10.0)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(15.0, 5.0, diagonalLength),
            alignment.getPointAtLength(10.0+diagonalLength)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(15.0, 15.0, 10.0+diagonalLength),
            alignment.getPointAtLength(20.0+diagonalLength)!!,
            accuracy,
        )

        assertApproximatelyEquals(
            point(0.0, 0.0, 0.0),
            alignment.getPointAtLength(-5.0)!!,
            accuracy)
        assertApproximatelyEquals(
            point(15.0, 15.0, 10.0+diagonalLength),
            alignment.getPointAtLength(50.0)!!,
            accuracy,
        )

        assertApproximatelyEquals(
            point(5.0, 0.0, 5.0),
            alignment.getPointAtLength(5.0)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            point(13.0, 3.0, hypot(3.0, 3.0)),
            alignment.getPointAtLength(10 + hypot(3.0, 3.0))!!,
            accuracy,
        )
    }

    @Test
    fun sliceWorks() {
        val pointInterval = hypot(10.0, 10.0)
        val original = LayoutSegment(
            points = listOf(
                point(10.0, 10.0, 0.0),
                point(20.0, 20.0, pointInterval),
                point(30.0, 30.0, 2*pointInterval),
                point(40.0, 40.0, 3*pointInterval),
            ),
            sourceId = StringId(),
            sourceStart = 15.0,
            resolution = 2,
            switchId = StringId(),
            startJointNumber = JointNumber(2),
            endJointNumber = JointNumber(2),
            start = 10.0,
            source = GeometrySource.IMPORTED,
        )

        val slice = original.slice(1, 2, 11.0)
        assertNotNull(slice)
        slice!!

        assertEquals(original.sourceId, slice.sourceId)
        assertEquals(original.sourceStart?.plus(pointInterval), slice.sourceStart)
        assertEquals(original.resolution, slice.resolution)
        assertEquals(original.switchId, slice.switchId)
        assertEquals(original.startJointNumber, slice.startJointNumber)
        assertEquals(original.endJointNumber, slice.endJointNumber)
        assertEquals(11.0, slice.start)
        assertEquals(original.source, slice.source)
        assertEquals(2, slice.points.size)
        assertEquals(original.points[1].x, slice.points[0].x)
        assertEquals(original.points[1].y, slice.points[0].y)
        assertEquals(0.0, slice.points[0].m)
        assertEquals(original.points[2].x, slice.points[1].x)
        assertEquals(original.points[2].y, slice.points[1].y)
        assertEquals(pointInterval, slice.points[1].m)
    }
}
