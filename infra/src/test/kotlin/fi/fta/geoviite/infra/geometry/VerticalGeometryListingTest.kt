package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VerticalGeometryListingTest() {
    @Test
    fun `Station point is calculated correctly when other line goes straight up`() {
        val curve = CurvedProfileSegment(
            PlanElementName(""),
            Point(0.0, 0.0),
            Point(2.0, 2.0),
            Point(0.0, 2.0),
            2.0
        )

        val tangentPoint = circCurveStationPoint(curve)
        assertNotNull(tangentPoint)
        assertEquals(tangentPoint.x, 2.0, 0.0001)
        assertEquals(tangentPoint.y, 0.0, 0.0001)
    }

    @Test
    fun `Station point is calculated correctly when centerpoint is below`() {
        val curve = CurvedProfileSegment(
            PlanElementName(""),
            Point(6.0, 0.0),
            Point(8.0, 0.0),
            Point(7.0, 1.0),
            sqrt(2.0)
        )

        val tangentPoint = circCurveStationPoint(curve)
        assertNotNull(tangentPoint)
        assertEquals(tangentPoint.x, 7.0, 0.0001)
        assertEquals(tangentPoint.y, -1.0, 0.0001)
    }

    @Test
    fun `Station point is calculated correctly when centerpoint is above`() {
        val curve = CurvedProfileSegment(
            PlanElementName(""),
            Point(0.0, 0.0),
            Point(2.0, 0.0),
            Point(1.0, -1.0),
            sqrt(2.0)
        )

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
        assertEquals(angleFraction, 1.0, 0.0001)
    }

    @Test
    fun `Angle fraction is calculated correctly for negative fractions`() {
        val point1 = Point(0.0, 0.0)
        val point2 = Point(10.0, -1.0)

        val angleFraction = angleFractionBetweenPoints(point1, point2)
        assertNotNull(angleFraction)
        assertEquals(angleFraction, -0.1, 0.0001)
    }

    @Test
    fun `Angle fraction is calculated correctly when point2 is before point1`() {
        val point1 = Point(20.0, 0.0)
        val point2 = Point(10.0, -1.0)

        val angleFraction = angleFractionBetweenPoints(point1, point2)
        assertNotNull(angleFraction)
        assertEquals(angleFraction, 0.1, 0.0001)
    }

    @Test
    fun `Angle fraction is null for same X vales`() {
        val point1 = Point(10.0, 0.0)
        val point2 = Point(10.0, -1.0)

        val angleFraction = angleFractionBetweenPoints(point1, point2)
        assertNull(angleFraction)
    }
}
