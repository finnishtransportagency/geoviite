package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal
import kotlin.math.sqrt
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VerticalGeometryListingTest {
    @Test
    fun `Station point is calculated correctly when other line goes straight up`() {
        val curve = CurvedProfileSegment(PlanElementName(""), Point(0.0, 0.0), Point(2.0, 2.0), Point(0.0, 2.0), 2.0)

        val tangentPoint = circCurveStationPoint(curve)
        assertNotNull(tangentPoint)
        assertEquals(tangentPoint.x, 2.0, 0.0001)
        assertEquals(tangentPoint.y, 0.0, 0.0001)
    }

    @Test
    fun `Station point is calculated correctly when centerpoint is below`() {
        val curve =
            CurvedProfileSegment(PlanElementName(""), Point(6.0, 0.0), Point(8.0, 0.0), Point(7.0, 1.0), sqrt(2.0))

        val tangentPoint = circCurveStationPoint(curve)
        assertNotNull(tangentPoint)
        assertEquals(tangentPoint.x, 7.0, 0.0001)
        assertEquals(tangentPoint.y, -1.0, 0.0001)
    }

    @Test
    fun `Station point is calculated correctly when centerpoint is above`() {
        val curve =
            CurvedProfileSegment(PlanElementName(""), Point(0.0, 0.0), Point(2.0, 0.0), Point(1.0, -1.0), sqrt(2.0))

        val tangentPoint = circCurveStationPoint(curve)
        assertNotNull(tangentPoint)
        assertEquals(tangentPoint.x, 1.0, 0.0001)
        assertEquals(tangentPoint.y, 1.0, 0.0001)
    }

    @Test
    fun `Angle fraction is calculated correctly for positive fractions`() {
        val point1 = Point(0.0, 0.0)
        val point2 = Point(1.0, 1.0)

        val angleFraction = angleFractionBetweenPoints(point1, point2)
        assertNotNull(angleFraction)
        assertEquals(angleFraction, 1.0, 0.000001)
    }

    @Test
    fun `Angle fraction is calculated correctly for negative fractions`() {
        val point1 = Point(0.0, 0.0)
        val point2 = Point(10.0, -1.0)

        val angleFraction = angleFractionBetweenPoints(point1, point2)
        assertNotNull(angleFraction)
        assertEquals(angleFraction, -0.1, 0.000001)
    }

    @Test
    fun `Angle fraction is calculated correctly when point2 is before point1`() {
        val point1 = Point(20.0, 0.0)
        val point2 = Point(10.0, -1.0)

        val angleFraction = angleFractionBetweenPoints(point1, point2)
        assertNotNull(angleFraction)
        assertEquals(angleFraction, 0.1, 0.000001)
    }

    @Test
    fun `Angle fraction is null for same X vales`() {
        val point1 = Point(10.0, 0.0)
        val point2 = Point(10.0, -1.0)

        val angleFraction = angleFractionBetweenPoints(point1, point2)
        assertNull(angleFraction)
    }

    @Test
    fun `Backwards linear section to previous curve is calculated correctly`() {
        val segment = curvedSegment(Point(4.0, 0.0), Point(6.0, 0.0), Point(5.0, -1.0))
        val entireProfile =
            listOf(
                curvedSegment(Point(0.0, 0.0), Point(2.0, 0.0), Point(1.0, -1.0)),
                linearSegment(Point(2.0, 0.0), Point(4.0, 0.0)),
                segment,
            )
        val (curved, linear) = partitionByType(entireProfile)
        val previous = previousLinearSection(segment, curved, linear)

        assertEquals(previous.linearSegmentLength!!.toDouble(), 2.0, 0.001)
        assertEquals(previous.stationValueDistance!!.toDouble(), 4.0, 0.001)
    }

    @Test
    fun `Backwards linear section is calculated correctly if no previous curved section`() {
        val segment = curvedSegment(Point(4.0, 0.0), Point(6.0, 0.0), Point(5.0, -1.0))
        val entireProfile = listOf(linearSegment(Point(2.0, 0.0), Point(4.0, 0.0)), segment)
        val (curved, linear) = partitionByType(entireProfile)
        val previous = previousLinearSection(segment, curved, linear)

        assertEquals(previous.linearSegmentLength!!.toDouble(), 2.0, 0.001)
        assertEquals(previous.stationValueDistance!!.toDouble(), 3.0, 0.001)
    }

    @Test
    fun `Backwards linear section has no values if there is no linear section`() {
        val segment = curvedSegment(Point(4.0, 0.0), Point(6.0, 0.0), Point(5.0, -1.0))
        val entireProfile = listOf(curvedSegment(Point(0.0, 0.0), Point(2.0, 0.0), Point(1.0, -1.0)), segment)
        val previous = previousLinearSection(segment, entireProfile, emptyList())

        assertNull(previous.linearSegmentLength)
        assertNull(previous.stationValueDistance)
    }

    @Test
    fun `Forwards linear section to next curve is calculated correctly`() {
        val segment = curvedSegment(Point(4.0, 0.0), Point(6.0, 0.0), Point(5.0, -1.0))
        val entireProfile =
            listOf(
                segment,
                linearSegment(Point(6.0, 0.0), Point(8.0, 0.0)),
                curvedSegment(Point(8.0, 0.0), Point(10.0, 0.0), Point(9.0, -1.0)),
            )
        val (curved, linear) = partitionByType(entireProfile)
        val next = nextLinearSection(segment, curved, linear)

        assertEquals(next.linearSegmentLength!!.toDouble(), 2.0, 0.001)
        assertEquals(next.stationValueDistance!!.toDouble(), 4.0, 0.001)
    }

    @Test
    fun `Forwards linear section is calculated correctly if no next curved section`() {
        val segment = curvedSegment(Point(4.0, 0.0), Point(6.0, 0.0), Point(5.0, -1.0))
        val entireProfile = listOf(segment, linearSegment(Point(6.0, 0.0), Point(8.0, 0.0)))
        val (curved, linear) = partitionByType(entireProfile)
        val next = nextLinearSection(segment, curved, linear)

        assertEquals(next.linearSegmentLength!!.toDouble(), 2.0, 0.001)
        assertEquals(next.stationValueDistance!!.toDouble(), 3.0, 0.001)
    }

    @Test
    fun `Forwards linear section is null if no next linear section`() {
        val segment = curvedSegment(Point(4.0, 0.0), Point(6.0, 0.0), Point(5.0, -1.0))
        val entireProfile = listOf(segment, curvedSegment(Point(8.0, 0.0), Point(10.0, 0.0), Point(9.0, -1.0)))
        val next = nextLinearSection(segment, entireProfile, emptyList())

        assertNull(next.linearSegmentLength)
        assertNull(next.stationValueDistance)
    }

    @Test
    fun `Simpler value calculations work`() {
        val segment = curvedSegment(Point(4.0, 0.0), Point(6.0, 0.0), Point(5.0, -1.0), 1.0)
        val entireProfile =
            listOf(
                curvedSegment(Point(0.0, 0.0), Point(2.0, 0.0), Point(1.0, -1.0), 1.0),
                linearSegment(Point(2.0, 0.0), Point(4.0, 0.0)),
                segment,
                linearSegment(Point(6.0, 0.0), Point(8.0, 0.0)),
                curvedSegment(Point(8.0, 0.0), Point(10.0, 0.0), Point(9.0, -1.0), 1.0),
            )
        val (curved, linear) = partitionByType(entireProfile)

        val verticalGeometryEntry =
            toVerticalGeometryListing(
                segment,
                GeometryAlignment(
                    AlignmentName("test-alignment"),
                    description = FreeText(""),
                    staStart = BigDecimal(0.0),
                    elements = emptyList(),
                    featureTypeCode = FeatureTypeCode("111"),
                    oidPart = FreeText("123"),
                    state = PlanState.EXISTING,
                ),
                null,
                null,
                planHeader(fileName = FileName("test")),
                null,
                curved,
                linear,
                TrackMeter.ZERO,
                TrackMeter.ZERO,
            )

        assertEquals(verticalGeometryEntry.start.station.toDouble(), 4.0, 0.001)
        assertEquals(verticalGeometryEntry.start.angle!!.toDouble(), 1.0, 0.000001)
        assertEquals(verticalGeometryEntry.end.station.toDouble(), 6.0, 0.001)
        assertEquals(verticalGeometryEntry.end.angle!!.toDouble(), -1.0, 0.0000001)
        assertEquals(verticalGeometryEntry.tangent!!.toDouble(), sqrt(2.0), 0.001)
        assertEquals(verticalGeometryEntry.point.station.toDouble(), 5.0, 0.001)
        assertEquals(verticalGeometryEntry.start.height.toDouble(), 0.0, 0.001)
        assertEquals(verticalGeometryEntry.end.height.toDouble(), 0.0, 0.001)
        assertEquals(verticalGeometryEntry.point.height.toDouble(), 1.0, 0.001)
        assertEquals(verticalGeometryEntry.radius.toDouble(), 1.0, 0.001)
        assertEquals(verticalGeometryEntry.fileName, FileName("test"))
        assertEquals(verticalGeometryEntry.planId, IntId<GeometryPlan>(1))
        assertEquals(verticalGeometryEntry.alignmentName, AlignmentName("test-alignment"))
    }

    @Test
    fun `Returns right coordinate at the intersection of elements`() {
        val alignment = createAlignment(GeometryElementType.LINE, GeometryElementType.LINE)
        val element = alignment.elements[0]
        val coordinate = alignment.getCoordinateAt(element.calculatedLength)!!
        assertEquals(coordinate.x, element.end.x, 0.0001)
        assertEquals(coordinate.y, element.end.y, 0.0001)
    }

    @Test
    fun `Returns right coordinate at the beginning of alignment`() {
        val alignment = createAlignment(GeometryElementType.LINE)
        val element = alignment.elements[0]
        val coordinate = alignment.getCoordinateAt(0.0)!!
        assertEquals(coordinate.x, element.start.x, 0.0001)
        assertEquals(coordinate.y, element.start.y, 0.0001)
    }

    @Test
    fun `Returns right coordinate mid-element`() {
        val alignment = createAlignment(GeometryElementType.LINE, GeometryElementType.LINE)
        val element = alignment.elements[1]
        val coordinate =
            alignment.getCoordinateAt(alignment.elements.first().calculatedLength + element.calculatedLength * 0.5)!!
        assertEquals(coordinate.x, 1.5, 0.0001)
        assertEquals(coordinate.y, 1.5, 0.0001)
    }

    @Test
    fun `Returns right coordinate at the end of alignment`() {
        val alignment = createAlignment(GeometryElementType.LINE)
        val coordinate = alignment.getCoordinateAt(alignment.elements.first().calculatedLength)!!
        assertEquals(coordinate.x, 1.0, 0.0001)
        assertEquals(coordinate.y, 1.0, 0.0001)
    }

    @Test
    fun `Throws if distance is past the end of alignment`() {
        val alignment = createAlignment(GeometryElementType.LINE)
        assertNull(alignment.getCoordinateAt(alignment.elements.first().calculatedLength + 0.5))
    }

    private fun curvedSegment(start: Point, end: Point, center: Point, radius: Double = start.x - end.x / 2.0) =
        CurvedProfileSegment(PlanElementName(""), start, end, center, radius)

    private fun linearSegment(start: Point, end: Point) = LinearProfileSegment(PlanElementName(""), start, end, true)

    private fun partitionByType(
        segments: List<ProfileSegment>
    ): Pair<List<CurvedProfileSegment>, List<LinearProfileSegment>> {
        val linear = segments.mapNotNull { s -> s as? LinearProfileSegment }
        val curved = segments.mapNotNull { s -> s as? CurvedProfileSegment }
        return curved to linear
    }
}
