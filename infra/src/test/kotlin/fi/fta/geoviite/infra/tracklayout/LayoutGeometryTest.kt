package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import kotlin.math.hypot
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LayoutGeometryTest {

    private val accuracy = 0.000000001

    @Test
    fun shouldReturnSegmentAsIsWhenDistanceIsOverSegmentsMValues() {
        val segment = segment(segmentPoint(0.0, 0.0, 1.0), segmentPoint(10.0, 0.0, 11.0), segmentPoint(20.0, 0.0, 21.0))

        val (startSegment, endSegment) = segment.splitAtM(30.0, 0.0)
        assertEquals(segment, startSegment)
        assertNull(endSegment)

        val (startSegment2, endSegment2) = segment.splitAtM(0.0, 0.0)
        assertEquals(segment, startSegment2)
        assertNull(endSegment2)
    }

    @Test
    fun shouldSplitFromSegmentPoint() {
        val segment =
            segment(
                segmentPoint(0.0, 0.0, 0.0),
                segmentPoint(10.0, 0.0, 10.0),
                segmentPoint(20.0, 0.0, 20.0),
                start = 1.0,
            )

        val (startSegment, endSegment) = segment.splitAtM(11.0, 0.0001)
        assertEquals(2, startSegment.alignmentPoints.size)
        assertEquals(0.0, startSegment.alignmentPoints.first().x)
        assertEquals(11.0, startSegment.alignmentPoints.last().m)

        assertNotNull(endSegment)
        assertEquals(2, endSegment!!.alignmentPoints.size)
        assertEquals(10.0, endSegment.alignmentPoints.first().x)
        assertEquals(20.0, endSegment.alignmentPoints.last().x)
        assertEquals(11.0, endSegment.alignmentPoints.first().m)
        assertEquals(21.0, endSegment.alignmentPoints.last().m)
    }

    @Test
    fun splitSegmentMetadataShouldBeCorrect() {
        val segment =
            LayoutSegment(
                geometry =
                    SegmentGeometry(
                        segmentPoints =
                            listOf(segmentPoint(10.0, 10.0, 0.0), segmentPoint(20.0, 20.0, 0.0 + hypot(10.0, 10.0))),
                        resolution = 2,
                    ),
                startM = 10.0,
                sourceId = IndexedId(1, 1),
                sourceStart = 15.0,
                switchId = IntId(1),
                startJointNumber = JointNumber(2),
                endJointNumber = JointNumber(2),
                source = GeometrySource.IMPORTED,
            )

        val (startSegment, endSegment) = segment.splitAtM(20.0, 0.5)
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
        assertEquals(segment.startM, startSegment.startM)
        assertEquals(segment.startM + startSegment.length, endSegment.startM)
        assertEquals(segment.source, startSegment.source)
        assertEquals(segment.source, endSegment.source)
    }

    @Test
    fun shouldSplitFromPointWithinTolerance() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))

        val (startSegment, endSegment) = segment.splitAtM(9.9, 0.5)
        assertEquals(2, startSegment.alignmentPoints.size)
        assertEquals(0.0, startSegment.alignmentPoints.first().x)
        assertEquals(10.0, startSegment.alignmentPoints.last().x)

        assertNotNull(endSegment)
        assertEquals(2, endSegment!!.alignmentPoints.size)
        assertEquals(10.0, endSegment.alignmentPoints.first().x)
        assertEquals(20.0, endSegment.alignmentPoints.last().x)
        assertEquals(10.0, endSegment.alignmentPoints.first().m)
        assertEquals(20.0, endSegment.alignmentPoints.last().m)
    }

    @Test
    fun shouldNotSplitWhenEndPointIsWithinTolerance() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))

        val (startSegment, endSegment) = segment.splitAtM(20.5, 1.0)
        assertNull(endSegment)
        assertEquals(3, startSegment.alignmentPoints.size)
        assertEquals(0.0, startSegment.alignmentPoints.first().m)
        assertEquals(20.0, startSegment.alignmentPoints.last().m)
    }

    @Test
    fun shouldSplitFromANewPointWhenNonePointsWithinTolerance() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))

        val (startSegment1, endSegment1) = segment.splitAtM(5.0, 0.5)
        assertEquals(2, startSegment1.alignmentPoints.size)
        assertEquals(0.0, startSegment1.alignmentPoints.first().m)
        assertEquals(5.0, startSegment1.alignmentPoints.last().m)
        assertEquals(0.0, startSegment1.alignmentPoints.first().x)
        assertEquals(5.0, startSegment1.alignmentPoints.last().x)

        assertNotNull(endSegment1)
        assertEquals(3, endSegment1!!.alignmentPoints.size)
        assertEquals(5.0, endSegment1.alignmentPoints.first().m)
        assertEquals(20.0, endSegment1.alignmentPoints.last().m)
        assertEquals(5.0, endSegment1.alignmentPoints.first().x)
        assertEquals(20.0, endSegment1.alignmentPoints.last().x)

        val (startSegment2, endSegment2) = segment.splitAtM(15.0, 0.5)
        assertEquals(3, startSegment2.alignmentPoints.size)
        assertEquals(0.0, startSegment2.alignmentPoints.first().m)
        assertEquals(15.0, startSegment2.alignmentPoints.last().m)
        assertEquals(0.0, startSegment2.alignmentPoints.first().x)
        assertEquals(15.0, startSegment2.alignmentPoints.last().x)

        assertNotNull(endSegment2)
        assertEquals(2, endSegment2!!.alignmentPoints.size)
        assertEquals(15.0, endSegment2.alignmentPoints.first().m)
        assertEquals(20.0, endSegment2.alignmentPoints.last().m)
        assertEquals(15.0, endSegment2.alignmentPoints.first().x)
        assertEquals(20.0, endSegment2.alignmentPoints.last().x)
    }

    @Test
    fun seekSegmentPointAtMWorks() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))
        assertEquals(PointSeekResult(alignmentPoint(0.0, 0.0, 0.0), 0, true), segment.seekPointAtM(0.0))
        assertEquals(PointSeekResult(alignmentPoint(10.0, 0.0, 10.0), 1, true), segment.seekPointAtM(10.0))
        assertEquals(PointSeekResult(alignmentPoint(20.0, 0.0, 20.0), 2, true), segment.seekPointAtM(20.0))

        assertEquals(PointSeekResult(alignmentPoint(0.0, 0.0, 0.0), 0, true), segment.seekPointAtM(-5.0))
        assertEquals(PointSeekResult(alignmentPoint(20.0, 0.0, 20.0), 2, true), segment.seekPointAtM(25.0))

        assertEquals(PointSeekResult(alignmentPoint(5.0, 0.0, 5.0), 1, false), segment.seekPointAtM(5.0))
        assertEquals(PointSeekResult(alignmentPoint(13.0, 0.0, 13.0), 2, false), segment.seekPointAtM(13.0))
    }

    @Test
    fun segmentPointAtLengthSnapsCorrectly() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))
        assertEquals(PointSeekResult(alignmentPoint(0.05, 0.0, 0.05), 1, false), segment.seekPointAtM(0.05, 0.0))
        assertEquals(PointSeekResult(alignmentPoint(0.0, 0.0, 0.0), 0, true), segment.seekPointAtM(0.05, 0.1))
        assertEquals(PointSeekResult(alignmentPoint(0.15, 0.0, 0.15), 1, false), segment.seekPointAtM(0.15, 0.1))

        assertEquals(PointSeekResult(alignmentPoint(9.95, 0.0, 9.95), 1, false), segment.seekPointAtM(9.95, 0.0))
        assertEquals(PointSeekResult(alignmentPoint(10.0, 0.0, 10.0), 1, true), segment.seekPointAtM(9.95, 0.1))
        assertEquals(PointSeekResult(alignmentPoint(9.85, 0.0, 9.85), 1, false), segment.seekPointAtM(9.85, 0.1))
        assertEquals(PointSeekResult(alignmentPoint(10.05, 0.0, 10.05), 2, false), segment.seekPointAtM(10.05, 0.0))
        assertEquals(PointSeekResult(alignmentPoint(10.0, 0.0, 10.0), 1, true), segment.seekPointAtM(10.05, 0.1))
        assertEquals(PointSeekResult(alignmentPoint(10.15, 0.0, 10.15), 2, false), segment.seekPointAtM(10.15, 0.1))

        assertEquals(PointSeekResult(alignmentPoint(19.95, 0.0, 19.95), 2, false), segment.seekPointAtM(19.95, 0.0))
        assertEquals(PointSeekResult(alignmentPoint(20.0, 0.0, 20.0), 2, true), segment.seekPointAtM(19.95, 0.1))
        assertEquals(PointSeekResult(alignmentPoint(19.85, 0.0, 19.85), 2, false), segment.seekPointAtM(19.85, 0.1))
    }

    @Test
    fun alignmentPointAtLengthWorks() {
        val diagonalLength = hypot(5.0, 5.0)
        val alignment =
            alignment(
                segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0)),
                segment(
                    segmentPoint(10.0, 0.0, 10.0),
                    segmentPoint(15.0, 5.0, 10.0 + diagonalLength),
                    segmentPoint(15.0, 15.0, 20.0 + diagonalLength),
                ),
            )

        assertApproximatelyEquals(segmentPoint(0.0, 0.0, 0.0), alignment.getPointAtM(0.0)!!, accuracy)
        assertApproximatelyEquals(segmentPoint(10.0, 0.0, 10.0), alignment.getPointAtM(10.0)!!, accuracy)
        assertApproximatelyEquals(
            segmentPoint(15.0, 5.0, 10.0 + diagonalLength),
            alignment.getPointAtM(10.0 + diagonalLength)!!,
            accuracy,
        )
        assertApproximatelyEquals(
            segmentPoint(15.0, 15.0, 20.0 + diagonalLength),
            alignment.getPointAtM(20.0 + diagonalLength)!!,
            accuracy,
        )

        assertApproximatelyEquals(segmentPoint(0.0, 0.0, 0.0), alignment.getPointAtM(-5.0)!!, accuracy)
        assertApproximatelyEquals(
            segmentPoint(15.0, 15.0, 20.0 + diagonalLength),
            alignment.getPointAtM(50.0)!!,
            accuracy,
        )

        assertApproximatelyEquals(segmentPoint(5.0, 0.0, 5.0), alignment.getPointAtM(5.0)!!, accuracy)
        assertApproximatelyEquals(
            segmentPoint(13.0, 3.0, 10 + hypot(3.0, 3.0)),
            alignment.getPointAtM(10 + hypot(3.0, 3.0))!!,
            accuracy,
        )
    }

    @Test
    fun sliceWorksWithNewStartM() {
        val pointInterval = hypot(10.0, 10.0)
        val original =
            LayoutSegment(
                geometry =
                    SegmentGeometry(
                        segmentPoints =
                            listOf(
                                segmentPoint(10.0, 10.0, 0.0),
                                segmentPoint(20.0, 20.0, 0.0 + pointInterval),
                                segmentPoint(30.0, 30.0, 0.0 + 2 * pointInterval),
                                segmentPoint(40.0, 40.0, 0.0 + 3 * pointInterval),
                            ),
                        resolution = 2,
                    ),
                startM = 10.0,
                sourceId = IndexedId(1, 1),
                sourceStart = 15.0,
                switchId = IntId(1),
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
        assertEquals(newStart, slice.startM)
        assertEquals(original.source, slice.source)
        assertEquals(2, slice.alignmentPoints.size)
        assertEquals(original.alignmentPoints[1].x, slice.alignmentPoints[0].x)
        assertEquals(original.alignmentPoints[1].y, slice.alignmentPoints[0].y)
        assertEquals(newStart, slice.alignmentPoints[0].m)
        assertEquals(original.alignmentPoints[2].x, slice.alignmentPoints[1].x)
        assertEquals(original.alignmentPoints[2].y, slice.alignmentPoints[1].y)
        assertEquals(newStart + pointInterval, slice.alignmentPoints[1].m, 0.00001)
    }

    @Test
    fun sliceWorksWithDefaultStartM() {
        val pointInterval = hypot(10.0, 10.0)
        val original =
            LayoutSegment(
                geometry =
                    SegmentGeometry(
                        segmentPoints =
                            listOf(
                                segmentPoint(10.0, 10.0, 0.0),
                                segmentPoint(20.0, 20.0, 0.0 + pointInterval),
                                segmentPoint(30.0, 30.0, 0.0 + 2 * pointInterval),
                                segmentPoint(40.0, 40.0, 0.0 + 3 * pointInterval),
                            ),
                        resolution = 2,
                    ),
                startM = 10.0,
                sourceId = IndexedId(1, 1),
                sourceStart = 15.0,
                switchId = IntId(1),
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
        assertEquals(original.alignmentPoints[1].m, slice.startM)
        assertEquals(original.source, slice.source)
        assertEquals(2, slice.alignmentPoints.size)
        assertEquals(original.alignmentPoints[1].x, slice.alignmentPoints[0].x)
        assertEquals(original.alignmentPoints[1].y, slice.alignmentPoints[0].y)
        assertEquals(original.alignmentPoints[1].m, slice.alignmentPoints[0].m)
        assertEquals(original.alignmentPoints[2].x, slice.alignmentPoints[1].x)
        assertEquals(original.alignmentPoints[2].y, slice.alignmentPoints[1].y)
        assertEquals(original.alignmentPoints[2].m, slice.alignmentPoints[1].m)
    }

    @Test
    fun `getMaxDirectionDeltaRads with shared segment points`() {
        val alignment = alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0)), segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        assertEquals(0.0, alignment.getMaxDirectionDeltaRads())
    }

    @Test
    fun `takeLast(n) with smaller last segment than n points`() {
        val alignment =
            alignment(
                segment(Point(0.0, 0.0), Point(3.0, 0.0)),
                segment(Point(3.0001, 0.0), Point(4.0001, 0.0), Point(5.0001, 0.0)),
            )
        assertEquals(listOf(1.0, 2.0, 3.0001, 4.0001, 5.0001), alignment.takeLast(5).map { it.x })
    }

    @Test
    fun `takeFirst(n) with smaller first segment than n points`() {
        val alignment =
            alignment(
                segment(Point(0.0, 0.0), Point(3.0, 0.0)),
                segment(Point(3.0001, 0.0), Point(4.0001, 0.0), Point(5.0001, 0.0)),
            )
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0001, 4.0001), alignment.takeFirst(5).map { it.x })
    }
}
