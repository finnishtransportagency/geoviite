package fi.fta.geoviite.infra.math

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geography.crs
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GeographyTest {

    @Test
    fun boundingPolygonEpsg3067To3067Works() {
        val points3067 = listOf(
            Point(410380.415197, 6695143.038955),
            Point(413592.399002, 6696145.121685),
            Point(418533.823337, 6697740.232228),
            Point(425552.596286, 6696334.091957),
            Point(424930.649942, 6696750.252024),
            Point(424912.100006, 6696759.418478),
            Point(416258.105283, 6697012.526639),
        )

        val bounds = boundingPolygonPointsByConvexHull(points3067, crs(Srid(3067)))

        val expectedBounds = listOf(
            Point(410380.415197, 6695143.038955),
            Point(418533.823337, 6697740.232228),
            Point(424912.100006, 6696759.418478),
            Point(424930.649942, 6696750.252024),
            Point(425552.596286, 6696334.091957),
            Point(410380.415197, 6695143.038955),
        )

        assertEquals(expectedBounds, bounds)
    }

    @Test
    fun pointEpsg3067To3857Works() {
        val point3067 = Point(410380.415197, 6695143.038955)
        val point3857 = Point(2824659.40, 8485448.86)

        val transformed3857 = transformNonKKJCoordinate(Srid(3067), Srid(3857), point3067)
        assertApproximatelyEquals(point3857, transformed3857, 0.01)
    }

    @Test
    fun pointEpsg2392To3857Works() {
        val point2392 = Point(2545821.01, 6679696.72)
        val point3857 = Point(2763351.44, 8450266.36)

        val transformed3857 = transformNonKKJCoordinate(Srid(2392), Srid(3857), point2392)
        assertApproximatelyEquals(point3857, transformed3857, 0.01)
    }

    @Test
    fun pointEpsg2392To3857Works2() {
        val point2392 = Point(2493105.32, 7472870.07)
        val point3857 = Point(2653350.403813375, 10254765.780128963)

        val transformed3857 = transformNonKKJCoordinate(Srid(2392), Srid(3857), point2392)
        assertApproximatelyEquals(point3857, transformed3857, 0.01)
    }

    @Test
    fun pointEpsg3857To2392Works() {
        val point3857 = Point(2763351.44, 8450266.36)
        val point2392 = Point(2545821.01, 6679696.72)

        val transformed2392 = transformNonKKJCoordinate(Srid(3857), Srid(2392), point3857)
        assertApproximatelyEquals(point2392, transformed2392, 0.01)
    }

    @Test
    fun pointEpsg3857To3879Works() {
        val point3857 = Point(2778638.852223, 8546271.258509)
        val point3879 = Point(25497863.32, 6726678.57)

        val transformed3879 = transformNonKKJCoordinate(Srid(3857), Srid(3879), point3857)
        assertApproximatelyEquals(point3879, transformed3879, 0.01)
    }

    @Test
    fun pointEpsg3879To3857Works() {
        val point3857 = Point(2778638.852223, 8546271.258509)
        val point3879 = Point(25497863.32, 6726678.57)

        val transformed3857 = transformNonKKJCoordinate(Srid(3879), Srid(3857), point3879)
        assertApproximatelyEquals(point3857, transformed3857, 0.01)
    }

    @Test
    fun shouldReturnDistanceBetweenTwoPoints() {
        Assertions.assertEquals(
            525811.478,
            calculateDistance(
                listOf(
                    Point(24.92842, 60.29733),
                    Point(25.47091, 65.00844),
                ), Srid(4326)),
            0.001,
        )
    }

    @Test
    fun shouldReturnDistanceBetweenMultiplePoints() {
        Assertions.assertEquals(
            558.088,
            calculateDistance(listOf(
                Point(25.41464, 65.01486),
                Point(25.42244, 65.01473),
                Point(25.42499, 65.01605),
            ), Srid(4326)),
            0.001,
        )
    }

    @Test
    fun pointConversion3067to4326AndBackIsAccurateEnough() {
        val p3067 = Point(509099.11577, 6711823.23316)
        val p4326 = transformNonKKJCoordinate(Srid(3067), Srid(4326), p3067)
        val p3067v2 = transformNonKKJCoordinate(Srid(4326), Srid(3067), p4326)
        assertApproximatelyEquals(Point(x=27.165853988, y=60.542330292), p4326, 0.000000001)
        assertApproximatelyEquals(p3067, p3067v2, 0.01)
    }

}
