package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.SegmentM
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.mapAlignment
import fi.fta.geoviite.infra.tracklayout.mapSegment
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LinkingTest {

    @Test
    fun `ReferenceLine geometry can be replace with GeometryPlan alignment`() {
        val geometry =
            referenceLineGeometry(segment(Point(1.0, 1.0), Point(2.0, 2.0)), segment(Point(2.0, 2.0), Point(3.0, 3.0)))
        val geometryAlignment =
            mapAlignment(
                mapSegment(Point3DM(10.0, 10.0, 0.0), Point3DM(13.0, 10.0, 3.0), Point3DM(16.0, 10.0, 6.0)),
                mapSegment(Point3DM(16.0, 10.0, 0.0), Point3DM(19.0, 10.0, 3.0), Point3DM(22.0, 10.0, 6.0)),
            )
        // Take the full range of geometry -> all points match
        assertGeometryChange(
            geometry,
            replaceReferenceLineGeometry(geometry, geometryAlignment, Range(LineM(0.0), geometryAlignment.length)),
            geometryAlignment.segments.map { s -> s.segmentPoints },
        )
        // Split so that we skip the first and last points
        assertGeometryChange(
            geometry,
            replaceReferenceLineGeometry(geometry, geometryAlignment, Range(3.0, 9.0).map(::LineM)),
            withPointsStartingFrom0(
                listOf(
                    geometryAlignment.segments[0].segmentPoints.takeLast(2),
                    geometryAlignment.segments[1].segmentPoints.take(2),
                )
            ),
        )
        // Split both segments between points
        assertGeometryChange(
            geometry,
            replaceReferenceLineGeometry(geometry, geometryAlignment, Range(2.5, 11.0).map(::LineM)),
            listOf(
                toSegmentPoints(Point3DM(12.5, 10.0, 0.0), Point3DM(13.0, 10.0, 0.5), Point3DM(16.0, 10.0, 3.5)),
                toSegmentPoints(Point3DM(16.0, 10.0, 0.0), Point3DM(19.0, 10.0, 3.0), Point3DM(21.0, 10.0, 5.0)),
            ),
        )
    }

    @Test
    fun `ReferenceLine geometry can be shortened`() {
        val geometry =
            referenceLineGeometry(
                // First segment m values: 0, 1, 2
                segment(Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0)),
                // Second segment m values: 2, 3, 4
                segment(Point(3.0, 0.0), Point(4.0, 0.0), Point(5.0, 0.0)),
            )
        // Cut nothing -> geometry remains the same
        assertGeometryChange(
            geometry,
            cutReferenceLineGeometry(geometry, Range(0.0, 4.0).map(::LineM)),
            geometry.segments.map { s -> s.segmentPoints },
        )
        // Cut 1m from start
        assertGeometryChange(
            geometry,
            cutReferenceLineGeometry(geometry, Range(1.0, 4.0).map(::LineM)),
            withPointsStartingFrom0(
                listOf(geometry.segments[0].segmentPoints.takeLast(2), geometry.segments[1].segmentPoints)
            ),
        )
        // Cut 1m from end
        assertGeometryChange(
            geometry,
            cutReferenceLineGeometry(geometry, Range(0.0, 3.0).map(::LineM)),
            withPointsStartingFrom0(
                listOf(geometry.segments[0].segmentPoints, geometry.segments[1].segmentPoints.take(2))
            ),
        )
        // Cut to just 1m in the middle, splitting only a piece of the first segment
        assertGeometryChange(
            geometry,
            cutReferenceLineGeometry(geometry, Range(1.0, 2.0).map(::LineM)),
            withPointsStartingFrom0(listOf(geometry.segments.first().segmentPoints.takeLast(2))),
        )
        // Cut to just 1m in the middle, splitting only a piece of the second segment
        assertGeometryChange(
            geometry,
            cutReferenceLineGeometry(geometry, Range(2.0, 3.0).map(::LineM)),
            withPointsStartingFrom0(listOf(geometry.segments.last().segmentPoints.take(2))),
        )
        // Cut to just 2m in the middle, splitting a piece of each segment
        assertGeometryChange(
            geometry,
            cutReferenceLineGeometry(geometry, Range(1.0, 3.0).map(::LineM)),
            withPointsStartingFrom0(
                listOf(
                    geometry.segments.first().segmentPoints.takeLast(2),
                    geometry.segments.last().segmentPoints.take(2),
                )
            ),
        )
        // Cut to just 2m in the middle, splitting each segment between-points
        assertGeometryChange(
            geometry,
            cutReferenceLineGeometry(geometry, Range(0.5, 3.5).map(::LineM)),
            listOf(
                toSegmentPoints(Point3DM(1.5, 0.0, 0.0), Point3DM(2.0, 0.0, 0.5), Point3DM(3.0, 0.0, 1.5)),
                toSegmentPoints(Point3DM(3.0, 0.0, 0.0), Point3DM(4.0, 0.0, 1.0), Point3DM(4.5, 0.0, 1.5)),
            ),
        )
    }

    @Test
    fun `Portion of ReferenceLine geometry can be linked from GeometryPlan alignment`() {
        val geometry =
            referenceLineGeometry(
                // First segment m values, matching y: 0, 1, 2, 3
                segment(Point(0.0, 0.0), Point(0.0, 1.0), Point(0.0, 2.0), Point(0.0, 3.0)),
                // Second segment m values, matching y: 3, 4, 5, 6
                segment(Point(0.0, 3.0), Point(0.0, 4.0), Point(0.0, 5.0), Point(0.0, 6.0)),
            )
        // Geometry alignment, offset 0.1m in x axis
        val geometryAlignment =
            mapAlignment(
                mapSegment(
                    Point3DM(0.1, 0.0, 0.0),
                    Point3DM(0.1, 1.0, 1.0),
                    Point3DM(0.1, 2.0, 2.0),
                    Point3DM(0.1, 3.0, 3.0),
                ),
                mapSegment(
                    Point3DM(0.1, 3.0, 0.0),
                    Point3DM(0.1, 4.0, 1.0),
                    Point3DM(0.1, 5.0, 2.0),
                    Point3DM(0.1, 6.0, 3.0),
                ),
            )
        // Replace entire geometry
        assertGeometryChange(
            geometry,
            linkLayoutGeometrySection(
                geometry,
                Range(0.0, 6.0).map(::LineM),
                geometryAlignment,
                Range(0.0, 6.0).map(::LineM),
            ),
            geometryAlignment.segments.map { s -> s.segmentPoints },
        )
        // Keep start and take the rest from geometry
        assertGeometryChange(
            geometry,
            linkLayoutGeometrySection(
                geometry,
                Range(2.0, 6.0).map(::LineM),
                geometryAlignment,
                Range(3.0, 6.0).map(::LineM),
            ),
            withPointsStartingFrom0(
                listOf(
                    geometry.segments[0].segmentPoints.take(3),
                    // Connection segment
                    toSegmentPoints(
                        Point3DM(0.0, 2.0, 0.0),
                        Point3DM(0.1, 3.0, calculateDistance(LAYOUT_SRID, Point(0.0, 2.0), Point(0.1, 3.0))),
                    ),
                    geometryAlignment.segments[1].segmentPoints,
                )
            ),
        )
        // Keep end and take the start from geometry
        assertGeometryChange(
            geometry,
            linkLayoutGeometrySection(
                geometry,
                Range(0.0, 3.0).map(::LineM),
                geometryAlignment,
                Range(0.0, 2.0).map(::LineM),
            ),
            withPointsStartingFrom0(
                listOf(
                    geometryAlignment.segments[0].segmentPoints.take(3),
                    // Connection segment
                    toSegmentPoints(
                        Point3DM(0.1, 2.0, 0.0),
                        Point3DM(0.0, 3.0, calculateDistance(LAYOUT_SRID, Point(0.1, 2.0), Point(0.0, 3.0))),
                    ),
                    geometry.segments[1].segmentPoints,
                )
            ),
        )
        // Keep start and end, taking the middle from geometry
        assertGeometryChange(
            geometry,
            linkLayoutGeometrySection(
                geometry,
                Range(1.0, 5.0).map(::LineM),
                geometryAlignment,
                Range(2.0, 4.0).map(::LineM),
            ),
            withPointsStartingFrom0(
                listOf(
                    geometry.segments[0].segmentPoints.take(2),
                    // Connection segment
                    toSegmentPoints(
                        Point3DM(0.0, 1.0, 0.0),
                        Point3DM(0.1, 2.0, calculateDistance(LAYOUT_SRID, Point(0.0, 1.0), Point(0.1, 2.0))),
                    ),
                    geometryAlignment.segments[0].segmentPoints.takeLast(2),
                    geometryAlignment.segments[1].segmentPoints.take(2),
                    // Connection segment
                    toSegmentPoints(
                        Point3DM(0.1, 4.0, 0.0),
                        Point3DM(0.0, 5.0, calculateDistance(LAYOUT_SRID, Point(0.1, 4.0), Point(0.0, 5.0))),
                    ),
                    geometry.segments[1].segmentPoints.takeLast(2),
                )
            ),
        )
        // Keep start and end, taking the middle from geometry but splitting between points
        assertGeometryChange(
            geometry,
            linkLayoutGeometrySection(
                geometry,
                Range(1.5, 4.5).map(::LineM),
                geometryAlignment,
                Range(2.5, 3.5).map(::LineM),
            ),
            withPointsStartingFrom0(
                listOf(
                    // First part from layout, last point is interpolated
                    toSegmentPoints(Point(0.0, 0.0), Point(0.0, 1.0), Point(0.0, 1.5)),
                    // Connection segment
                    toSegmentPoints(
                        Point3DM(0.0, 1.5, 0.0),
                        Point3DM(0.1, 2.5, calculateDistance(LAYOUT_SRID, Point(0.0, 1.5), Point(0.1, 2.5))),
                    ),
                    // Middle 2 segments from geometry, ends both interpolated
                    toSegmentPoints(Point(0.1, 2.5), Point(0.1, 3.0)),
                    toSegmentPoints(Point(0.1, 3.0), Point(0.1, 3.5)),
                    // Connection segment
                    toSegmentPoints(
                        Point3DM(0.1, 3.5, 0.0),
                        Point3DM(0.0, 4.5, calculateDistance(LAYOUT_SRID, Point(0.1, 3.5), Point(0.0, 4.5))),
                    ),
                    // Last part from layout, first point is interpolated
                    toSegmentPoints(Point(0.0, 4.5), Point(0.0, 5.0), Point(0.0, 6.0)),
                )
            ),
        )
    }

    @Test
    fun `ReferenceLineGeometry can be extended with portion of GeometryPlan geometry`() {
        val geometry =
            referenceLineGeometry(
                // First segment m values, matching y: 0, 1, 2
                segment(Point(0.0, 0.0), Point(0.0, 1.0), Point(0.0, 2.0)),
                // Second segment m values, matching y: 2, 3, 4
                segment(Point(0.0, 2.0), Point(0.0, 3.0), Point(0.0, 4.0)),
            )
        // Geometry alignment, offset 0.1m in x-axis and long enough to be linked in both ends
        val geometryAlignment =
            mapAlignment(
                // First segment before layout alignment: m 0-3
                mapSegment(
                    Point3DM(0.1, -3.0, 0.0),
                    Point3DM(0.1, -2.0, 1.0),
                    Point3DM(0.1, -1.0, 2.0),
                    Point3DM(0.1, 0.0, 3.0),
                ),
                // Mid-segment next to the layout segments: m 3-7
                mapSegment(Point3DM(0.1, 0.0, 3.0), Point3DM(0.1, 4.0, 7.0)),
                // Last segment after layout alignment: m 7-10
                mapSegment(
                    Point3DM(0.1, 4.0, 7.0),
                    Point3DM(0.1, 5.0, 8.0),
                    Point3DM(0.1, 6.0, 9.0),
                    Point3DM(0.1, 7.0, 10.0),
                ),
            )
        // Extend start
        assertGeometryChange(
            geometry,
            linkLayoutGeometrySection(
                geometry,
                Range(0.0, 0.0).map(::LineM),
                geometryAlignment,
                Range(0.0, 2.0).map(::LineM),
            ),
            withPointsStartingFrom0(
                listOf(
                    // Extension from geometry
                    geometryAlignment.segments[0].segmentPoints.take(3),
                    // Connection segment
                    toSegmentPoints(
                        Point3DM(0.1, -1.0, 0.0),
                        Point3DM(0.0, 0.0, calculateDistance(LAYOUT_SRID, Point(0.1, -1.0), Point(0.0, 0.0))),
                    ),
                    // Rest from the layout alignment as-is
                ) + geometry.segments.map { s -> s.segmentPoints }
            ),
        )
        // Extend end
        assertGeometryChange(
            geometry,
            linkLayoutGeometrySection(
                geometry,
                Range(4.0, 4.0).map(::LineM),
                geometryAlignment,
                Range(8.0, 10.0).map(::LineM),
            ),
            withPointsStartingFrom0(
                geometry.segments.map { s -> s.segmentPoints } +
                    listOf(
                        // Connection segment
                        toSegmentPoints(
                            Point3DM(0.0, 4.0, 0.0),
                            Point3DM(0.1, 5.0, calculateDistance(LAYOUT_SRID, Point(0.0, 4.0), Point(0.1, 5.0))),
                        ),
                        // Extension from geometry
                        geometryAlignment.segments[2].segmentPoints.takeLast(3),
                    )
            ),
        )
    }

    @Test
    fun `Source length values are correct after splitting`() {
        val sourceId = IndexedId<GeometryElement>(1, 2)
        val geometry =
            referenceLineGeometry(
                segment(Point(0.0, 0.0), Point(0.0, 1.0)),
                segment(Point(0.0, 1.0), Point(0.0, 2.0), sourceId = sourceId, sourceStartM = 10.0),
                segment(Point(0.0, 2.0), Point(0.0, 3.0)),
            )
        // Cutting other segments doesn't affect source start
        assertEquals(LayoutSegment.sourceStartM(10.0), slice(geometry, Range(0.5, 2.5).map(::LineM))[1].sourceStartM)
        // Cutting the sourced segment from beginning adds to the source start
        assertEquals(LayoutSegment.sourceStartM(10.5), slice(geometry, Range(1.5, 3.0).map(::LineM))[0].sourceStartM)
        // Cutting the sourced segment from end doesn't affect source start
        assertEquals(LayoutSegment.sourceStartM(10.0), slice(geometry, Range(0.0, 1.5).map(::LineM))[1].sourceStartM)
    }
}

private fun withPointsStartingFrom0(pointLists: List<List<SegmentPoint>>): List<List<SegmentPoint>> {
    return pointLists.map { pointList ->
        var totalM = LineM<SegmentM>(0.0)
        var lastM = pointList.first().m
        pointList.map { point ->
            totalM += point.m - lastM
            lastM = point.m
            point.copy(m = totalM)
        }
    }
}

private fun assertGeometryChange(
    originalGeometry: ReferenceLineGeometry,
    newGeometry: ReferenceLineGeometry,
    segmentPointLists: List<List<SegmentPoint>>,
) {
    assertEquals(originalGeometry.id, newGeometry.id)
    assertEquals(segmentPointLists.size, newGeometry.segments.size)
    segmentPointLists.forEachIndexed { index, expectedPoints ->
        val segment = newGeometry.segments[index]
        assertEquals(expectedPoints, segment.segmentPoints)
    }
}
