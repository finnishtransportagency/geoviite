package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles("dev", "test")
@SpringBootTest
class KkjToEtrsTriangulationDaoIT @Autowired constructor(
    val kkJtoETRSTriangulationDao: KKJtoETRSTriangulationDao,
): ITTestBase() {

    @Test
    fun fetchesTriangleInsideTriangulationNetwork() {
        // Point is in Hervanta, Tampere
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        val point = toJtsPoint(Point(3332494.083, 6819936.144), YKJ_CRS)
        val triangle = triangles.find { it.intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun fetchesTriangleAtCornerPoint() {
        // Point is in a triangulation network corner point
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        val point = toJtsPoint(Point(3199159.097, 6747800.979), YKJ_CRS)
        val triangle = triangles.find { it.intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun doesntFetchTriangleOutsideTriangulationNetwork() {
        // Point is in Norway
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        val point = toJtsPoint(Point(2916839.212, 7227390.743), YKJ_CRS)
        val triangle = triangles.find { it.intersects(point) }
        assertNull(triangle)
    }
}
