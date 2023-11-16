package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geography.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.model.RATKO_SRID
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class CoordinateTransformServiceIT @Autowired constructor(
    private val coordinateTransformationService: CoordinateTransformationService
) : DBTestBase() {
    @Test
    fun `Creates KKJ to TM35FIn transformation`() {
        assertDoesNotThrow { coordinateTransformationService.getTransformation(Srid(2392) /* KKJ2 */, LAYOUT_SRID) }
    }

    @Test
    fun `Creates TM35FIN to WGS84 transformation`() {
        assertDoesNotThrow { coordinateTransformationService.getTransformation(LAYOUT_SRID, RATKO_SRID) }
    }

    @Test
    fun `Caches transformations properly`() {
        val kkj2ToEtrsTransformation = coordinateTransformationService.getTransformation(Srid(2392), LAYOUT_SRID)
        val kkj2ToEtrsTransformation2 = coordinateTransformationService.getTransformation(Srid(2392), LAYOUT_SRID)

        assertEquals(kkj2ToEtrsTransformation, kkj2ToEtrsTransformation2)
    }

    @Test
    fun `Transforms YKJ (KKJ3) to TM35FIN accurately`() {
        // Point is in Hervanta, Tampere
        val point = Point(3332494.083, 6819936.144)
        val transformedPoint = coordinateTransformationService.transformCoordinate(KKJ3_YKJ, LAYOUT_SRID, point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(332391.7884, transformedPoint.x, 0.001)
        Assertions.assertEquals(6817075.2561, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms KKJ0 to TM35FIN accurately`() {
        val kkj0point = Point(585436.3916, 6679828.4162)
        val transformedPoint = coordinateTransformationService.transformCoordinate(KKJ0, LAYOUT_SRID, kkj0point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(87158.652, transformedPoint.x, 0.001)
        Assertions.assertEquals(6699388.278, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms KKJ1 to TM35FIN accurately`() {
        val kkj1point = Point(1541730.796, 6818539.1569)
        val transformedPoint = coordinateTransformationService.transformCoordinate(KKJ1, LAYOUT_SRID, kkj1point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(222053.674, transformedPoint.x, 0.001)
        Assertions.assertEquals(6826549.907, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms KKJ2 to TM35FIN accurately`() {
        val kkj2point = Point(2488027.6005, 6820948.9609)
        val transformedPoint = coordinateTransformationService.transformCoordinate(KKJ2, LAYOUT_SRID, kkj2point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(328183.073, transformedPoint.x, 0.001)
        Assertions.assertEquals(6822313.526, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms KKJ4 to TM35FIN accurately`() {
        val kkj4point = Point(4488552.946177, 6943595.611588)
        val transformedPoint = coordinateTransformationService.transformCoordinate(KKJ4, LAYOUT_SRID, kkj4point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(642412.7448, transformedPoint.x, 0.001)
        Assertions.assertEquals(6943735.9093, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms KKJ5 to TM35FIN accurately`() {
        val kkj5point = Point(5426728.7305, 6978302.5687)
        val transformedPoint = coordinateTransformationService.transformCoordinate(KKJ5, LAYOUT_SRID, kkj5point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(731400.669, transformedPoint.x, 0.001)
        Assertions.assertEquals(6982768.023, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms TM35FIN to YKJ (KKJ3) accurately`() {
        // Point is in Hervanta, Tampere
        val point = Point(332391.7884, 6817075.2561)
        val transformedPoint = coordinateTransformationService.transformCoordinate(LAYOUT_SRID, KKJ3_YKJ, point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(3332494.083, transformedPoint.x, 0.001)
        Assertions.assertEquals(6819936.144, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms TM35FIN to KKJ0 accurately`() {
        // Point is in western Åland, in case anybody decides to build a track there
        val point = Point(87158.652, 6699388.278)
        val transformedPoint = coordinateTransformationService.transformCoordinate(LAYOUT_SRID, KKJ0, point)
        // Expected values are from paikkatietoikkuna. The KKJ0 transform is less accurate than the rest,
        // hence the larger delta
        Assertions.assertEquals(585436.3916, transformedPoint.x, 0.01)
        Assertions.assertEquals(6679828.4162, transformedPoint.y, 0.01)
    }

    @Test
    fun `Transforms TM35FIN to KKJ1 accurately`() {
        // Point is at Pori railway station
        val point = Point(222053.674, 6826549.907)
        val transformedPoint = coordinateTransformationService.transformCoordinate(LAYOUT_SRID, KKJ1, point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(1541730.796, transformedPoint.x, 0.001)
        Assertions.assertEquals(6818539.1569, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms TM35FIN to KKJ2 accurately`() {
        val point = Point(328183.073, 6822313.526)
        val transformedPoint = coordinateTransformationService.transformCoordinate(LAYOUT_SRID, KKJ2, point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(2488027.6005, transformedPoint.x, 0.001)
        Assertions.assertEquals(6820948.9609, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms TM35FIN to KKJ4 accurately`() {
        val point = Point(642412.7448, 6943735.9093)
        val transformedPoint = coordinateTransformationService.transformCoordinate(LAYOUT_SRID, KKJ4, point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(4488552.946177, transformedPoint.x, 0.001)
        Assertions.assertEquals(6943595.611588, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms TM35FIN to KKJ5 accurately`() {
        val point = Point(731400.669, 6982768.023)
        val transformedPoint = coordinateTransformationService.transformCoordinate(LAYOUT_SRID, KKJ5, point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(5426728.7305, transformedPoint.x, 0.001)
        Assertions.assertEquals(6978302.5687, transformedPoint.x, 0.001)
    }
}
