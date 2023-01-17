package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.dataImport.RATKO_SRID
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.math.Point
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
) : ITTestBase() {
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
    fun `Transforms YKJ to TM35FIN accurately`() {
        // Point is in Hervanta, Tampere
        val point = Point(3332494.083, 6819936.144)
        val transformedPoint = coordinateTransformationService.transformCoordinate(Srid(2393) /* KKJ3 */, LAYOUT_SRID, point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(332391.7884, transformedPoint.x, 0.001)
        Assertions.assertEquals(6817075.2561, transformedPoint.y, 0.001)
    }

    @Test
    fun `Transforms KKJ4 to TM35FIN accurately`() {
        val kkj4point = Point(4488552.946177, 6943595.611588)
        val transformedPoint = coordinateTransformationService.transformCoordinate(Srid(2394) /* KKJ4 */, LAYOUT_SRID, kkj4point)
        // Expected values are from paikkatietoikkuna
        Assertions.assertEquals(642412.7448, transformedPoint.x, 0.001)
        Assertions.assertEquals(6943735.9093, transformedPoint.y, 0.001)
    }
}
