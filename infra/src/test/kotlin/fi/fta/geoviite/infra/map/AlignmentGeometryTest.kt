package fi.fta.geoviite.infra.map

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlignmentGeometryTest {
    @Test
    fun `filtering by bounding box includes one extra point before and after the bbox`() {
        val alignment = edge(listOf(segment(Point(0.0, 5.0), Point(15.0, 5.0))))
        val expected = alignment.allAlignmentPoints.toList().filter { it.x >= 5.0 && it.x <= 10.0 }

        assertEquals(
            expected,
            simplify(
                alignment,
                includeSegmentEndPoints = true,
                bbox = boundingBoxAroundPoints(Point(6.0, 0.0), Point(9.0, 10.0)),
            ),
        )
    }

    @Test
    fun `simplify without resolution or bbox returns all points`() {
        val alignment = edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
        assertEquals(alignment.allAlignmentPoints.toList(), simplify(alignment, includeSegmentEndPoints = true))
    }

    @Test
    fun `resolution thins points at the requested interval`() {
        val alignment = edge(listOf(segment(Point(0.0, 0.0), Point(20.0, 0.0))))
        val result = simplify(alignment, resolution = 5, includeSegmentEndPoints = true)
        // Track start (x=0), then every 5 units (x=5, 10, 15), and track end (x=20)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0), result.map { it.x })
    }

    @Test
    fun `resolution larger than alignment still includes track endpoints and second point`() {
        val alignment = edge(listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))))
        val result = simplify(alignment, resolution = 1000, includeSegmentEndPoints = true)
        // Track start and track end only
        assertEquals(listOf(0.0, 5.0), result.map { it.x })
    }

    @Test
    fun `skipped middle segments include their endpoints when includeSegmentEndPoints is true`() {
        val alignment =
            edge(
                listOf(
                    segment(Point(0.0, 0.0), Point(3.0, 0.0)),
                    segment(Point(3.0, 0.0), Point(6.0, 0.0)),
                    segment(Point(6.0, 0.0), Point(9.0, 0.0)),
                )
            )
        val result = simplify(alignment, resolution = 100, includeSegmentEndPoints = true)
        val xValues = result.map { it.x }
        assertTrue(3.0 in xValues, "Middle segment start point should be included")
        assertTrue(6.0 in xValues, "Middle segment end point should be included")
    }

    @Test
    fun `skipped middle segments produce no points when includeSegmentEndPoints is false`() {
        val alignment =
            edge(
                listOf(
                    segment(Point(0.0, 0.0), Point(3.0, 0.0)),
                    segment(Point(3.0, 0.0), Point(6.0, 0.0)),
                    segment(Point(6.0, 0.0), Point(9.0, 0.0)),
                )
            )
        val result = simplify(alignment, resolution = 100, includeSegmentEndPoints = false)
        // First segment: track start (x=0)
        // Middle segment: skipped entirely
        // Last segment: only track end (x=9) passes endpoint check
        assertEquals(listOf(0.0, 9.0), result.map { it.x })
    }

    @Test
    fun `segment start points are included while otherwise keeping the resolution steady`() {
        val alignment =
            edge(listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0)), segment(Point(5.0, 0.0), Point(10.0, 0.0))))
        val result = simplify(alignment, resolution = 3, includeSegmentEndPoints = true)
        // s0: x=0 (track start), x=3 (3-0=3>=3, previousM=3)
        // s1: x=5 (segment start, included but previousM stays at 3),
        //     x=6 (6-3=3>=3, previousM=6), x=9 (9-6=3>=3, previousM=9), x=10 (track end)
        assertEquals(listOf(0.0, 3.0, 5.0, 6.0, 9.0, 10.0), result.map { it.x })
    }

    @Test
    fun `bbox not intersecting the alignment returns empty list`() {
        val alignment = edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
        val result =
            simplify(
                alignment,
                includeSegmentEndPoints = true,
                bbox = boundingBoxAroundPoints(Point(100.0, 100.0), Point(200.0, 200.0)),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `bbox combined with resolution filters both spatially and by density`() {
        val alignment = edge(listOf(segment(Point(0.0, 0.0), Point(30.0, 0.0))))
        val bboxOnly =
            simplify(
                alignment,
                includeSegmentEndPoints = true,
                bbox = boundingBoxAroundPoints(Point(10.0, -1.0), Point(20.0, 1.0)),
            )
        val bboxAndResolution =
            simplify(
                alignment,
                resolution = 5,
                includeSegmentEndPoints = true,
                bbox = boundingBoxAroundPoints(Point(10.0, -1.0), Point(20.0, 1.0)),
            )
        assertTrue(
            bboxAndResolution.size < bboxOnly.size,
            "Adding resolution should produce fewer points than bbox alone",
        )
    }

    @Test
    fun `a single point outside of the bbox between two ranges within it is only returned once`() {
        val alignment =
            edge(
                listOf(
                    segment(
                        Point(0.0, 0.0),
                        Point(1.0, 1.0),
                        Point(2.0, 2.0),
                        Point(3.0, 3.0),
                        Point(4.0, 2.0),
                        Point(5.0, 1.0),
                        Point(6.0, 0.0),
                    )
                )
            )
        val simplified =
            simplify(
                alignment,
                includeSegmentEndPoints = true,
                bbox = boundingBoxAroundPoints(Point(0.0, 0.0), Point(6.0, 2.5)),
            )
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0), simplified.map { it.x })
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 2.0, 1.0, 0.0), simplified.map { it.y })
    }
}
