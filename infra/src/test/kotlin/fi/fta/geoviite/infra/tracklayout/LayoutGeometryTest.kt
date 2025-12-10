package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.publication.getMaxDirectionDeltaRads
import kotlin.math.hypot
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LayoutGeometryTest {

    private val accuracy = 0.000000001

    @Test
    fun shouldReturnSegmentAsIsWhenDistanceIsOverSegmentsMValues() {
        val segment = segment(Point(0.0, 0.0), Point(10.0, 0.0), Point(20.0, 0.0))

        val (startSegment, endSegment) = segment.splitAtM(LineM(30.0), 0.0)
        assertEquals(segment, startSegment)
        assertNull(endSegment)

        val (startSegment2, endSegment2) = segment.splitAtM(LineM(0.0), 0.0)
        assertEquals(segment, startSegment2)
        assertNull(endSegment2)
    }

    @Test
    fun shouldSplitFromSegmentPoint() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))

        val (startSegment, endSegment) = segment.splitAtM(LineM(10.0), 0.0001)
        assertEquals(2, startSegment.segmentPoints.size)
        assertEquals(0.0, startSegment.segmentPoints.first().x)
        assertEquals(LineM(10.0), startSegment.segmentPoints.last().m)

        assertNotNull(endSegment)
        assertEquals(2, endSegment!!.segmentPoints.size)
        assertEquals(10.0, endSegment.segmentPoints.first().x)
        assertEquals(20.0, endSegment.segmentPoints.last().x)
        assertEquals(LineM(0.0), endSegment.segmentPoints.first().m)
        assertEquals(LineM(10.0), endSegment.segmentPoints.last().m)
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
                sourceId = IndexedId(1, 1),
                sourceStartM = LayoutSegment.sourceStartM(15.0),
                source = GeometrySource.IMPORTED,
            )

        val (startSegment, endSegment) = segment.splitAtM(LineM(10.0), 0.5)
        assertNotNull(endSegment)
        endSegment!!
        assertEquals(segment.sourceId, startSegment.sourceId)
        assertEquals(segment.sourceId, endSegment.sourceId)
        assertEquals(segment.sourceStartM, startSegment.sourceStartM)
        assertEquals(segment.addedSourceStart(startSegment.length), endSegment.sourceStartM)
        assertEquals(segment.resolution, startSegment.resolution)
        assertEquals(segment.resolution, endSegment.resolution)
        assertEquals(segment.source, startSegment.source)
        assertEquals(segment.source, endSegment.source)
    }

    @Test
    fun shouldSplitFromPointWithinTolerance() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))

        val (startSegment, endSegment) = segment.splitAtM(LineM(9.9), 0.5)
        assertEquals(2, startSegment.segmentPoints.size)
        assertEquals(0.0, startSegment.segmentPoints.first().x)
        assertEquals(10.0, startSegment.segmentPoints.last().x)

        assertNotNull(endSegment)
        assertEquals(2, endSegment!!.segmentPoints.size)
        assertEquals(10.0, endSegment.segmentPoints.first().x)
        assertEquals(20.0, endSegment.segmentPoints.last().x)
        assertEquals(LineM(0.0), endSegment.segmentPoints.first().m)
        assertEquals(LineM(10.0), endSegment.segmentPoints.last().m)
    }

    @Test
    fun shouldNotSplitWhenEndPointIsWithinTolerance() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))

        val (startSegment, endSegment) = segment.splitAtM(LineM(20.5), 1.0)
        assertNull(endSegment)
        assertEquals(3, startSegment.segmentPoints.size)
        assertEquals(LineM(0.0), startSegment.segmentPoints.first().m)
        assertEquals(LineM(20.0), startSegment.segmentPoints.last().m)
    }

    @Test
    fun shouldSplitFromANewPointWhenNonePointsWithinTolerance() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))

        val (startSegment1, endSegment1) = segment.splitAtM(LineM(5.0), 0.5)
        assertEquals(2, startSegment1.segmentPoints.size)
        assertEquals(LineM(0.0), startSegment1.segmentPoints.first().m)
        assertEquals(LineM(5.0), startSegment1.segmentPoints.last().m)
        assertEquals(0.0, startSegment1.segmentPoints.first().x)
        assertEquals(5.0, startSegment1.segmentPoints.last().x)

        assertNotNull(endSegment1)
        assertEquals(3, endSegment1!!.segmentPoints.size)
        assertEquals(LineM(0.0), endSegment1.segmentPoints.first().m)
        assertEquals(LineM(15.0), endSegment1.segmentPoints.last().m)
        assertEquals(5.0, endSegment1.segmentPoints.first().x)
        assertEquals(20.0, endSegment1.segmentPoints.last().x)

        val (startSegment2, endSegment2) = segment.splitAtM(LineM(15.0), 0.5)
        assertEquals(3, startSegment2.segmentPoints.size)
        assertEquals(LineM(0.0), startSegment2.segmentPoints.first().m)
        assertEquals(LineM(15.0), startSegment2.segmentPoints.last().m)
        assertEquals(0.0, startSegment2.segmentPoints.first().x)
        assertEquals(15.0, startSegment2.segmentPoints.last().x)

        assertNotNull(endSegment2)
        assertEquals(2, endSegment2!!.segmentPoints.size)
        assertEquals(LineM(0.0), endSegment2.segmentPoints.first().m)
        assertEquals(LineM(5.0), endSegment2.segmentPoints.last().m)
        assertEquals(15.0, endSegment2.segmentPoints.first().x)
        assertEquals(20.0, endSegment2.segmentPoints.last().x)
    }

    @Test
    fun seekSegmentPointAtMWorks() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))
        assertEquals(
            PointSeekResult(locationTrackPoint(0.0, 0.0, 0.0), 0, true),
            segment.seekPointAtM(LineM(0.0), locationTrackM(0.0), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(10.0, 0.0, 10.0), 1, true),
            segment.seekPointAtM(LineM(0.0), locationTrackM(10.0), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(20.0, 0.0, 20.0), 2, true),
            segment.seekPointAtM(LineM(0.0), locationTrackM(20.0), 0.0),
        )

        assertEquals(
            PointSeekResult(locationTrackPoint(0.0, 0.0, 0.0), 0, true),
            segment.seekPointAtM(LineM(0.0), locationTrackM(-5.0), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(20.0, 0.0, 20.0), 2, true),
            segment.seekPointAtM(LineM(0.0), locationTrackM(25.0), 0.0),
        )

        assertEquals(
            PointSeekResult(locationTrackPoint(5.0, 0.0, 5.0), 1, false),
            segment.seekPointAtM(LineM(0.0), locationTrackM(5.0), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(13.0, 0.0, 13.0), 2, false),
            segment.seekPointAtM(LineM(0.0), locationTrackM(13.0), 0.0),
        )
    }

    @Test
    fun segmentPointAtLengthSnapsCorrectly() {
        val segment = segment(segmentPoint(0.0, 0.0, 0.0), segmentPoint(10.0, 0.0, 10.0), segmentPoint(20.0, 0.0, 20.0))
        assertEquals(
            PointSeekResult(locationTrackPoint(0.05, 0.0, 0.05), 1, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(0.05), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(0.0, 0.0, 0.0), 0, true),
            segment.seekPointAtM(locationTrackM(0.0), LineM(0.05), 0.1),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(0.15, 0.0, 0.15), 1, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(0.15), 0.1),
        )

        assertEquals(
            PointSeekResult(locationTrackPoint(9.95, 0.0, 9.95), 1, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(9.95), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(10.0, 0.0, 10.0), 1, true),
            segment.seekPointAtM(locationTrackM(0.0), LineM(9.95), 0.1),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(9.85, 0.0, 9.85), 1, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(9.85), 0.1),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(10.05, 0.0, 10.05), 2, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(10.05), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(10.0, 0.0, 10.0), 1, true),
            segment.seekPointAtM(locationTrackM(0.0), LineM(10.05), 0.1),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(10.15, 0.0, 10.15), 2, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(10.15), 0.1),
        )

        assertEquals(
            PointSeekResult(locationTrackPoint(19.95, 0.0, 19.95), 2, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(19.95), 0.0),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(20.0, 0.0, 20.0), 2, true),
            segment.seekPointAtM(locationTrackM(0.0), LineM(19.95), 0.1),
        )
        assertEquals(
            PointSeekResult(locationTrackPoint(19.85, 0.0, 19.85), 2, false),
            segment.seekPointAtM(locationTrackM(0.0), LineM(19.85), 0.1),
        )
    }

    @Test
    fun alignmentPointAtLengthWorks() {
        val diagonalLength = hypot(5.0, 5.0)
        val alignment =
            referenceLineGeometry(
                segment(Point(0.0, 0.0), Point(10.0, 0.0)),
                segment(Point(10.0, 0.0), Point(15.0, 5.0), Point(15.0, 15.0)),
            )

        assertApproximatelyEquals(segmentPoint(0.0, 0.0, 0.0), alignment.getPointAtM(LineM(0.0))!!, accuracy)
        assertApproximatelyEquals(segmentPoint(10.0, 0.0, 10.0), alignment.getPointAtM(LineM(10.0))!!, accuracy)
        assertApproximatelyEquals(
            segmentPoint(15.0, 5.0, 10.0 + diagonalLength),
            alignment.getPointAtM(LineM(10.0 + diagonalLength))!!,
            accuracy,
        )
        assertApproximatelyEquals(
            segmentPoint(15.0, 15.0, 20.0 + diagonalLength),
            alignment.getPointAtM(LineM(20.0 + diagonalLength))!!,
            accuracy,
        )

        assertApproximatelyEquals(segmentPoint(0.0, 0.0, 0.0), alignment.getPointAtM(LineM(-5.0))!!, accuracy)
        assertApproximatelyEquals(
            segmentPoint(15.0, 15.0, 20.0 + diagonalLength),
            alignment.getPointAtM(LineM(50.0))!!,
            accuracy,
        )

        assertApproximatelyEquals(segmentPoint(5.0, 0.0, 5.0), alignment.getPointAtM(LineM(5.0))!!, accuracy)
        assertApproximatelyEquals(
            segmentPoint(13.0, 3.0, 10 + hypot(3.0, 3.0)),
            alignment.getPointAtM(LineM(10 + hypot(3.0, 3.0)))!!,
            accuracy,
        )
    }

    @Test
    fun `Slice by point indices works`() {
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
                sourceId = IndexedId(1, 1),
                sourceStartM = LayoutSegment.sourceStartM(15.0),
                source = GeometrySource.IMPORTED,
            )

        val (slice, sliceM) = original.slice(LineM<ReferenceLineM>(10.0), 1, 2)!!

        assertEquals(slice.sourceId, original.sourceId)
        assertEquals(slice.sourceStartM, original.addedSourceStart(pointInterval))
        assertEquals(slice.resolution, original.resolution)
        assertEquals(original.source, slice.source)
        assertEquals(2, slice.segmentPoints.size)
        assertEquals(original.segmentPoints[1].x, slice.segmentPoints[0].x)
        assertEquals(original.segmentPoints[1].y, slice.segmentPoints[0].y)
        assertEquals(LineM(0.0), slice.segmentPoints[0].m)
        assertEquals(original.segmentPoints[2].x, slice.segmentPoints[1].x)
        assertEquals(original.segmentPoints[2].y, slice.segmentPoints[1].y)
        assertEquals(LineM(pointInterval), slice.segmentPoints[1].m, 0.00001)
        assertEquals(Range(10.0 + pointInterval, 10.0 + 2 * pointInterval).map(::LineM), sliceM)
    }

    @Test
    fun `getMaxDirectionDeltaRads with shared segment points`() {
        val geometry =
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0)), segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        assertEquals(0.0, getMaxDirectionDeltaRads(geometry))
    }

    @Test
    fun `takeLast(n) with smaller last segment than n points`() {
        val geometry =
            referenceLineGeometry(
                segment(Point(0.0, 0.0), Point(3.0, 0.0)),
                segment(Point(3.0001, 0.0), Point(4.0001, 0.0), Point(5.0001, 0.0)),
            )
        assertEquals(listOf(1.0, 2.0, 3.0001, 4.0001, 5.0001), geometry.takeLast(5).map { it.x })
    }

    @Test
    fun `takeFirst(n) with smaller first segment than n points`() {
        val geometry =
            referenceLineGeometry(
                segment(Point(0.0, 0.0), Point(3.0, 0.0)),
                segment(Point(3.0001, 0.0), Point(4.0001, 0.0), Point(5.0001, 0.0)),
            )
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0001, 4.0001), geometry.takeFirst(5).map { it.x })
    }
}
