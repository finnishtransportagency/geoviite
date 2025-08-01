import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.summarizeAlignmentChanges
import fi.fta.geoviite.infra.tracklayout.geocodingContext
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.singleSegmentWithInterpolatedPoints
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemarksUpdaterServiceTest {

    private fun xAxisGeocodingContext() = geocodingContext((0..60).map { x -> Point(x.toDouble(), 0.0) })

    @Test
    fun `summarizeAlignmentChanges detects change to track geometry`() {
        val oldGeometry =
            trackGeometryOfSegments(
                segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0))
            )
        val newGeometry =
            trackGeometryOfSegments(
                segment(Point(0.0, 0.0), Point(1.0, 1.2), Point(2.0, 1.4), Point(3.0, 1.2), Point(4.0, 0.0))
            )

        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldGeometry, newGeometry)
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals(2.0, row.changedLengthM)
        assertEquals(row.maxDistance, 1.4, 0.1)
    }

    @Test
    fun `summarizeAlignmentChanges detects multiple changes on different segments`() {
        val oldGeometry =
            trackGeometryOfSegments(
                ((0..4).map { segmentIx ->
                    segment(*(0..4).map { pointIx -> Point(segmentIx * 4.0 + pointIx * 1.0, 0.0) }.toTypedArray())
                })
            )
        val newGeometry =
            trackGeometryOfSegments(
                oldGeometry.segments[0],
                segment(Point(4.0, 0.0), Point(5.0, 1.2), Point(6.0, 1.5), Point(7.0, 1.2), Point(8.0, 0.0)),
                oldGeometry.segments[2],
                segment(Point(12.0, 0.0), Point(13.0, 1.2), Point(15.0, 2.0)),
                segment(Point(15.0, 2.0), Point(16.0, 3.0), Point(17.0, 4.0), Point(18.0, 5.0)),
            )

        val result = summarizeAlignmentChanges(xAxisGeocodingContext(), oldGeometry, newGeometry)
        assertEquals(2, result.size)
        assertEquals(1.5, result[0].maxDistance, 0.1)
        assertEquals(2.0, result[0].changedLengthM)
        assertEquals(4.0, result[1].maxDistance, 0.1)
        assertEquals(4.0, result[1].changedLengthM)
    }

    @Test
    fun `summarizeAlignmentChanges detects multiple changes in a single segment`() {
        val oldGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(60.0, 0.0)))
        val newGeometry =
            trackGeometryOfSegments(
                singleSegmentWithInterpolatedPoints(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                    Point(10.0, 2.0),
                    Point(15.0, 0.0),
                    Point(30.0, 0.0),
                    Point(40.0, 3.0),
                    Point(50.0, 0.0),
                    Point(60.0, 0.0),
                )
            )

        val result =
            summarizeAlignmentChanges(
                xAxisGeocodingContext(),
                oldGeometry,
                trackGeometryOfSegments(segment(toSegmentPoints(to3DMPoints(newGeometry.allSegmentPoints.toList())))),
            )
        assertEquals(2, result.size)
        assertEquals(2.0, result[0].maxDistance, 0.1)
        assertEquals(4.0, result[0].changedLengthM)
        assertEquals(3.0, result[1].maxDistance, 0.1)
        assertEquals(12.0, result[1].changedLengthM)
    }
}
