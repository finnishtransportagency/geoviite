package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.tracklayout.LineM
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GeometryProfileTest {

    @Test
    fun shouldReturnPVITangentPoints() {
        val accuracy = 0.000000001

        val tangentPoints1 =
            tangentPointsOfPvi(
                leftPvi = Point(4.0, 2.0),
                middlePvi = Point(9.5, 12.0),
                rightPvi = Point(13.0, 3.0),
                radius = -3.0,
            )
        assertApproximatelyEquals(Point(6.403890888228156, 6.370710705869375), tangentPoints1.first, accuracy)
        assertApproximatelyEquals(Point(11.828552628881985, 6.012293240017755), tangentPoints1.second, accuracy)

        val tangentPoints2 =
            tangentPointsOfPvi(
                leftPvi = Point(60.0, 19.65),
                middlePvi = Point(304.394100, 18.052),
                rightPvi = Point(400.0002, 18.5302),
                radius = 3000.0,
            )
        assertApproximatelyEquals(Point(287.08389182585677, 18.165184862737195), tangentPoints2.first, accuracy)
        assertApproximatelyEquals(Point(321.7044616749155, 18.13858249790489), tangentPoints2.second, accuracy)
    }

    @Test
    fun shouldReturnCorrectTangentPointsAndCentersOnLeaningCurves() {
        val accuracy = 0.0001

        // Top-of-hill, centered
        val tangentPoints1 =
            tangentPointsOfPvi(
                leftPvi = Point(1.0, -1.0),
                middlePvi = Point(3.5, 2.0),
                rightPvi = Point(7.0, -1.0),
                radius = -2.0,
            )
        assertApproximatelyEquals(Point(2.2017, 0.4421), tangentPoints1.first, accuracy)
        assertApproximatelyEquals(Point(5.0397, 0.6802), tangentPoints1.second, accuracy)
        assertApproximatelyEquals(
            Point(3.7382, -0.8383),
            circularCurveCenterPoint(-2.0, tangentPoints1.first, Point(3.5, 2.0)),
            accuracy,
        )

        // Top-of-hill, left-tilted
        val tangentPoints2 =
            tangentPointsOfPvi(
                leftPvi = Point(1.0, -1.0),
                middlePvi = Point(2.5, 1.5),
                rightPvi = Point(6.0, 3.0),
                radius = -2.0,
            )
        assertApproximatelyEquals(Point(2.1673, 0.9455), tangentPoints2.first, accuracy)
        assertApproximatelyEquals(Point(3.0944, 1.7548), tangentPoints2.second, accuracy)
        assertApproximatelyEquals(
            Point(3.8823, -0.0835),
            circularCurveCenterPoint(-2.0, tangentPoints2.first, Point(2.5, 1.5)),
            accuracy,
        )

        // Top-of-hill, right-tilted
        val tangentPoints3 =
            tangentPointsOfPvi(
                leftPvi = Point(2.0, 2.5),
                middlePvi = Point(5.5, 2.0),
                rightPvi = Point(7.0, -1.0),
                radius = -2.0,
            )
        assertApproximatelyEquals(Point(4.4626, 2.1482), tangentPoints3.first, accuracy)
        assertApproximatelyEquals(Point(5.9686, 1.0627), tangentPoints3.second, accuracy)
        assertApproximatelyEquals(
            Point(4.1798, 0.1683),
            circularCurveCenterPoint(-2.0, tangentPoints3.first, Point(5.5, 2.0)),
            accuracy,
        )

        // Bottom-of-pit, centered
        val tangentPoints4 =
            tangentPointsOfPvi(
                leftPvi = Point(1.0, -1.0),
                middlePvi = Point(4.5, -2.5),
                rightPvi = Point(8.0, 0.0),
                radius = 2.0,
            )
        assertApproximatelyEquals(Point(3.4655, -2.0567), tangentPoints4.first, accuracy)
        assertApproximatelyEquals(Point(5.4158, -1.8458), tangentPoints4.second, accuracy)
        assertApproximatelyEquals(
            Point(4.2534, -0.2183),
            circularCurveCenterPoint(2.0, tangentPoints4.first, Point(4.5, -2.5)),
            accuracy,
        )

        // Bottom-of-pit, left-tilted
        val tangentPoints5 =
            tangentPointsOfPvi(
                leftPvi = Point(1.0, 1.0),
                middlePvi = Point(2.0, -2.0),
                rightPvi = Point(5.0, -3.0),
                radius = 2.0,
            )
        assertApproximatelyEquals(Point(1.6838, -1.0513), tangentPoints5.first, accuracy)
        assertApproximatelyEquals(Point(2.9487, -2.3162), tangentPoints5.second, accuracy)
        assertApproximatelyEquals(
            Point(3.5811, -0.4189),
            circularCurveCenterPoint(2.0, tangentPoints5.first, Point(2.0, -2.0)),
            accuracy,
        )

        // Bottom-of-pit, right-tilted
        val tangentPoints6 =
            tangentPointsOfPvi(
                leftPvi = Point(2.0, -2.0),
                middlePvi = Point(5.0, -1.5),
                rightPvi = Point(5.5, 1.0),
                radius = 2.0,
            )
        assertApproximatelyEquals(Point(3.6383, -1.7269), tangentPoints6.first, accuracy)
        assertApproximatelyEquals(Point(5.2707, -0.1464), tangentPoints6.second, accuracy)
        assertApproximatelyEquals(
            Point(3.3096, 0.2458),
            circularCurveCenterPoint(2.0, tangentPoints6.first, Point(5.0, -1.5)),
            accuracy,
        )
    }

    @Test
    fun shouldReturnProfilePointAtGivenDistance() {
        val point3 = VIPoint(PlanElementName("first"), Point(1.82, 19.09))
        val point4 =
            VICircularCurve(PlanElementName("mid1"), Point(60.0, 19.65), BigDecimal(-3000.0), BigDecimal(48.490589))
        val point5 =
            VICircularCurve(PlanElementName("mid2"), Point(304.3941, 18.052), BigDecimal(3500.0), BigDecimal(40.390901))
        val point6 =
            VICircularCurve(
                PlanElementName("mid3"),
                Point(400.0002, 18.5302),
                BigDecimal(-3000.0),
                BigDecimal(29.900281),
            )
        val point7 = VIPoint(PlanElementName("last"), Point(505.318, 17.92))
        val listOfVerticalIntersectionPoints2 = listOf(point3, point4, point5, point6, point7)
        val profile = GeometryProfile(PlanElementName("test profile 2"), listOfVerticalIntersectionPoints2)

        // Exact PVI points
        assertEquals(19.09, profile.getHeightAt(LineM(1.82)))
        assertEquals(17.92, profile.getHeightAt(LineM(505.318)))

        // Descending line
        assertEquals(17.99, profile.getHeightAt(LineM(492.19))!!, 0.1)
        // Ascending line
        assertEquals(19.22, profile.getHeightAt(LineM(15.81))!!, 0.1)

        // Negative descending curve
        assertEquals(18.11, profile.getHeightAt(LineM(301.19))!!, 0.1)
        // Negative ascending curve
        assertEquals(18.12, profile.getHeightAt(LineM(316.19))!!, 0.1)
        // Positivie ascending curve
        assertEquals(19.54, profile.getHeightAt(LineM(58.75))!!, 0.1)
        // Positive descending curve
        assertEquals(19.53, profile.getHeightAt(LineM(76.75))!!, 0.1)
    }

    @Test
    fun profileSegmentsAreGeneratedCorrectly() {
        val pointAccuracy = 0.000000001
        val angleAccuracy = 0.000000001

        val profileElements =
            listOf(
                VIPoint(description = PlanElementName("831_profAlign/2"), point = Point(x = 0.0, y = 106.800011)),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/3"),
                    point = Point(x = 26.641, y = 106.95),
                    radius = BigDecimal(-5000.000000),
                    length = BigDecimal(14.059082),
                ),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/6"),
                    point = Point(x = 136.641, y = 107.26),
                    radius = BigDecimal(-20000.000000),
                    length = BigDecimal(60.113591),
                ),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/9"),
                    point = Point(x = 296.641, y = 107.23),
                    radius = BigDecimal(-10000.000000),
                    length = BigDecimal(14.791664),
                ),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/12"),
                    point = Point(x = 416.641, y = 107.03),
                    radius = BigDecimal(10000.000000),
                    length = BigDecimal(20.784306),
                ),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/15"),
                    point = Point(x = 756.641, y = 107.17),
                    radius = BigDecimal(10000.000000),
                    length = BigDecimal(83.970546),
                ),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/18"),
                    point = Point(x = 925.788714, y = 108.66),
                    radius = BigDecimal(-10000.000000),
                    length = BigDecimal(0.720266),
                ),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/21"),
                    point = Point(x = 1020.788714, y = 109.49),
                    radius = BigDecimal(5000.000000),
                    length = BigDecimal(7.030074),
                ),
                VICircularCurve(
                    description = PlanElementName("831_profAlign/24"),
                    point = Point(x = 1090.788714, y = 110.2),
                    radius = BigDecimal(5000.000000),
                    length = BigDecimal(6.785713),
                ),
                VIPoint(
                    description = PlanElementName("831_profAlign/25"),
                    point = Point(x = 1102.624711, y = 110.336114),
                ),
            )

        val profile = GeometryProfile(PlanElementName("831_profAlign"), profileElements)
        profile.segments.forEachIndexed { index, segment ->
            assertFalse(segment is LinearProfileSegment && !segment.valid)
            val previous = profile.segments.getOrNull(index - 1)
            if (previous != null) {
                assertApproximatelyEquals(previous.end, segment.start, pointAccuracy)
                assertEquals(previous.endAngle, segment.startAngle, angleAccuracy)
            }
        }

        val curveElements = profileElements.count { e -> e is VICircularCurve }
        val curveSegments = profile.segments.count { e -> e is CurvedProfileSegment }
        val lineSegments = profile.segments.count { e -> e is LinearProfileSegment }
        assertEquals(curveElements, curveSegments)
        assertEquals(curveElements + 1, lineSegments)
    }

    // Enable to generate a CSV curve to inspect visually
    //    @Test
    fun printProfileCoordinatesInCsvForm() {
        val point1 = VIPoint(PlanElementName("first"), Point(1.82, 19.09))
        val point2 =
            VICircularCurve(PlanElementName("mid1"), Point(60.0, 19.65), BigDecimal(-3000.0), BigDecimal(48.490589))
        val point3 =
            VICircularCurve(PlanElementName("mid2"), Point(304.3941, 18.052), BigDecimal(3500.0), BigDecimal(40.390901))
        val point4 =
            VICircularCurve(
                PlanElementName("mid3"),
                Point(400.0002, 18.5302),
                BigDecimal(-3000.0),
                BigDecimal(29.900281),
            )
        val point5 = VIPoint(PlanElementName("last"), Point(505.318, 17.92))
        val listOfVerticalIntersectionPoints = listOf(point1, point2, point3, point4, point5)
        val profile = GeometryProfile(PlanElementName("test profile 2"), listOfVerticalIntersectionPoints)

        val distance = (point5.point.x - point1.point.x).toInt()

        for (i in 2..distance step 10) {
            val x = i.toDouble()
            val y = profile.getHeightAt(LineM(x))
            println("$x; $y;")
        }
    }
}
