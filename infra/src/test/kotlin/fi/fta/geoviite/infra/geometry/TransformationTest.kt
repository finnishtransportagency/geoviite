package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.RotationDirection.CCW
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.HeightTriangle
import fi.fta.geoviite.infra.geography.KKJ2_SRID
import fi.fta.geoviite.infra.geography.geotoolsTransformation
import fi.fta.geoviite.infra.geography.transformHeightValue
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.ratko.model.RATKO_SRID
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.lengthPoints
import fi.fta.geoviite.infra.tracklayout.toPointList
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class TransformationTest {

    @Test
    fun heightIsTransformedFromN60toN2000() {
        val originalHeight = 3.2267
        val expectedHeight = 3.48
        val pointToTransform = Point(2776456.160, 8437996.456)
        val triangle =
            HeightTriangle(
                corner1 = Point(2736945.03516908, 8441109.719913667),
                corner2 = Point(2775850.2241459107, 8434428.120915797),
                corner3 = Point(2793936.116042787, 8504341.156241765),
                corner1Diff = 0.253170,
                corner2Diff = 0.253090,
                corner3Diff = 0.256790,
            )
        val listOfHeightTriangles = listOf(triangle)
        val transformedHeight =
            transformHeightValue(originalHeight, pointToTransform, listOfHeightTriangles, VerticalCoordinateSystem.N60)
        assertHeightEquals(expectedHeight, transformedHeight)
    }

    @Test
    fun lengthPointCalculationWorks() {
        assertEquals(listOf(0.0, 1.0, 2.0, 2.5), lengthPoints(2.5, 1))
        assertEquals(listOf(0.0, 2.0, 4.001), lengthPoints(4.001, 2))
        assertEquals(listOf(0.0, 2.0, 4.0, 4.1), lengthPoints(4.1, 2))
        assertEquals(listOf(0.0, 0.001), lengthPoints(0.001, 1))
    }

    @Test
    fun transformingShortElementSucceeds() {
        val curve =
            curve(
                rotation = CCW,
                length = 0.003,
                radius = 300.0,
                chord = 0.00364,
                start = Point(4436238.519000, 6789010.552000),
                end = Point(4436238.520000, 6789010.555500),
                center = Point(4435942.847000, 6789061.329000),
            )
        assertEquals(listOf(0.0, 0.003640000000022328), lengthPoints(curve.calculatedLength, 1))
        assertEquals(
            listOf(
                Point3DM(x = 4436238.519, y = 6789010.552, m = 0.0),
                Point3DM(x = 4436238.52, y = 6789010.5555, m = 0.003640000000022328),
            ),
            toPointList(curve, 1),
        )
    }

    @Test
    fun `Creating KKJ to TM35 transformation using non-KKJ transform throws`() {
        assertThrows<IllegalArgumentException> { geotoolsTransformation(KKJ2_SRID, LAYOUT_SRID) }
    }

    @Test
    fun `Creating LAYOUT_SRID to RATKO_SRID transformation works without triangulation network`() {
        assertDoesNotThrow { geotoolsTransformation(LAYOUT_SRID, RATKO_SRID) }
    }
}

fun assertHeightEquals(expectedHeight: Double, actualHeight: Double) {
    val actualHeightTo2Digits = String.format("%.2f", actualHeight).replace(",", ".").toDouble()
    assertEquals(expectedHeight, actualHeightTo2Digits)
}
