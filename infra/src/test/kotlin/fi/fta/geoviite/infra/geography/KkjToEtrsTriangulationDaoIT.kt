package fi.fta.geoviite.infra.geography

import com.github.davidmoten.rtree2.geometry.Geometries
import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_CRS
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles("dev", "test")
@SpringBootTest
class KkjToEtrsTriangulationDaoIT @Autowired constructor(
    val kkjEtrsTriangulationDao: KkjEtrsTriangulationDao,
): ITTestBase() {

    @Test
    fun fetchesTriangleInsideTriangulationNetwork() {
        // Point is in Hervanta, Tampere
        val triangles = kkjEtrsTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.KKJ_TO_ETRS)
        val point = toJtsPoint(Point(3332494.083, 6819936.144), YKJ_CRS)
        val triangle = triangles.search(Geometries.point(point.x, point.y)).find { it.value().intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun fetchesTriangleAtCornerPoint() {
        // Point is in a triangulation network corner point
        val triangles = kkjEtrsTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.KKJ_TO_ETRS)
        val point = toJtsPoint(Point(3199159.097, 6747800.979), YKJ_CRS)
        val triangle = triangles.search(Geometries.point(point.x, point.y)).find { it.value().intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun doesntFetchTriangleOutsideTriangulationNetwork() {
        // Point is in Norway
        val triangles = kkjEtrsTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.KKJ_TO_ETRS)
        val point = toJtsPoint(Point(2916839.212, 7227390.743), YKJ_CRS)
        val triangle = triangles.search(Geometries.point(point.x, point.y)).find { it.value().intersects(point) }
        assertNull(triangle)
    }

    @Test
    fun `Fetches ETRS to KKJ triangulation triangle correctly`() {
        // Point is in Hervanta, Tampere
        val triangles = kkjEtrsTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.ETRS_TO_KKJ)
        val point = toJtsPoint( Point(332391.7884, 6817075.2561), LAYOUT_CRS)
        val triangle = triangles.search(Geometries.point(point.x, point.y)).find { it.value().intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun `Fetches ETRS to KKJ triangulation triangle at network corner point correctly`() {
        // Point is in a triangulation network corner point
        val triangles = kkjEtrsTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.ETRS_TO_KKJ)
        val point = toJtsPoint(Point(538905.047, 6707957.789), LAYOUT_CRS)
        val triangle = triangles.search(Geometries.point(point.x, point.y)).find { it.value().intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun `Doesn't fetch a triangle outside of the triangulation network`() {
        // Point is in Norway
        val triangles = kkjEtrsTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.ETRS_TO_KKJ)
        val point = toJtsPoint(Point(-33121.0, 7455239.0), LAYOUT_CRS)
        val triangle = triangles.search(Geometries.point(point.x, point.y)).find { it.value().intersects(point) }
        assertNull(triangle)
    }
}
