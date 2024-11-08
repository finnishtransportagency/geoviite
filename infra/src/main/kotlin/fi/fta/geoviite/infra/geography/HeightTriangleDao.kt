package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.queryOne
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class HeightTriangleDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun fetchTriangles(boundingPolygon: List<Point>): List<HeightTriangle> {
        val sql =
            """
            select tn.coord1_id, 
                   t1.n2000 - t1.n60  as corner1_difference, 
                   postgis.st_x(t1.point_transformed) as x1, 
                   postgis.st_y(t1.point_transformed) as y1,
                   tn.coord2_id, 
                   t2.n2000 - t2.n60 as corner2_difference, 
                   postgis.st_x(t2.point_transformed) as x2, 
                   postgis.st_y(t2.point_transformed) as y2,
                   tn.coord3_id, 
                   t3.n2000 - t3.n60 as corner3_difference, 
                   postgis.st_x(t3.point_transformed) as x3, 
                   postgis.st_y(t3.point_transformed) as y3,
                   tn.polygon_transformed
            from common.n60_n2000_triangulation_network tn
            join common.n60_n2000_triangle_corner_point t1 on t1.coord_id = tn.coord1_id
            join common.n60_n2000_triangle_corner_point t2 on t2.coord_id = tn.coord2_id
            join common.n60_n2000_triangle_corner_point t3 on t3.coord_id = tn.coord3_id
            where postgis.st_intersects(
              tn.polygon_transformed,
              postgis.st_polygonfromtext(:bounding_polygon, :srid)
            )  
        """
                .trimIndent()
        val params = mapOf("bounding_polygon" to create2DPolygonString(boundingPolygon), "srid" to LAYOUT_SRID.code)

        val triangles =
            jdbcTemplate.query(sql, params) { rs, _ ->
                HeightTriangle(
                    corner1 = rs.getPoint("x1", "y1"),
                    corner2 = rs.getPoint("x2", "y2"),
                    corner3 = rs.getPoint("x3", "y3"),
                    corner1Diff = rs.getDouble("corner1_difference"),
                    corner2Diff = rs.getDouble("corner2_difference"),
                    corner3Diff = rs.getDouble("corner3_difference"),
                )
            }
        logger.daoAccess(AccessType.FETCH, HeightTriangle::class)
        return triangles
    }

    fun fetchTriangulationNetworkBounds(): BoundingBox {
        logger.daoAccess(AccessType.FETCH, BoundingBox::class)
        // language=SQL
        val sql =
            """
            select postgis.st_astext(postgis.st_extent(polygon_transformed)) bounds
            from common.n60_n2000_triangulation_network
        """
                .trimIndent()
        return jdbcTemplate.queryOne(sql, mapOf<String, Any>()) { rs, _ ->
            boundingBoxAroundPoints(parse2DPolygon(rs.getString("bounds")))
        }
    }
}
