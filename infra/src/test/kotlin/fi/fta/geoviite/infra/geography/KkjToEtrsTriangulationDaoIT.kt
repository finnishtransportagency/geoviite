package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class KkjToEtrsTriangulationDaoIT @Autowired constructor(val kkjTm35FinTriangulationDao: KkjTm35finTriangulationDao) :
    DBTestBase() {

    @Test
    fun fetchesTriangleInsideTriangulationNetwork() {
        // Point is in Hervanta, Tampere
        val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.KKJ_TO_TM35FIN)
        val point = toJtsGeoPoint(Point(3332494.083, 6819936.144), KKJ3_YKJ_SRID)
        val triangle = network.findTriangle(point)
        assertTrue(triangle.intersects(point))
    }

    @Test
    fun fetchesTriangleAtCornerPoint() {
        // Point is in a triangulation network corner point
        val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.KKJ_TO_TM35FIN)
        val point = toJtsGeoPoint(Point(3199159.097, 6747800.979), KKJ3_YKJ_SRID)
        val triangle = network.findTriangle(point)
        assertTrue(triangle.intersects(point))
    }

    @Test
    fun doesntFetchTriangleOutsideTriangulationNetwork() {
        // Point is in Norway
        val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.KKJ_TO_TM35FIN)
        val point = toJtsGeoPoint(Point(2916839.212, 7227390.743), KKJ3_YKJ_SRID)
        assertThrows<IllegalArgumentException> { network.findTriangle(point) }
    }

    @Test
    fun `Fetches ETRS to KKJ triangulation triangle correctly`() {
        // Point is in Hervanta, Tampere
        val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.TM35FIN_TO_KKJ)
        val point = toJtsGeoPoint(Point(332391.7884, 6817075.2561), LAYOUT_SRID)
        val triangle = network.findTriangle(point)
        assertTrue(triangle.intersects(point))
    }

    @Test
    fun `Fetches ETRS to KKJ triangulation triangle at network corner point correctly`() {
        // Point is in a triangulation network corner point
        val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.TM35FIN_TO_KKJ)
        val point = toJtsGeoPoint(Point(538905.047, 6707957.789), LAYOUT_SRID)
        val triangle = network.findTriangle(point)
        assertTrue(triangle.intersects(point))
    }

    @Test
    fun `Doesn't fetch a triangle outside of the triangulation network`() {
        // Point is in Norway
        val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.TM35FIN_TO_KKJ)
        val point = toJtsGeoPoint(Point(-33121.0, 7455239.0), LAYOUT_SRID)
        assertThrows<IllegalArgumentException> { network.findTriangle(point) }
    }
}
