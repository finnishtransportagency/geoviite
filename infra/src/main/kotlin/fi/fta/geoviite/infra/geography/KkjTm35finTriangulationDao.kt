package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.configuration.CACHE_KKJ_TM35FIN_TRIANGULATION_NETWORK
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getPoint
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

enum class TriangulationDirection(val direction: String, val source: Srid, val target: Srid) {
    KKJ_TO_TM35FIN("KKJ_TO_TM35FIN", KKJ3_YKJ_SRID, ETRS89_TM35FIN_SRID),
    TM35FIN_TO_KKJ("TM35FIN_TO_KKJ", ETRS89_TM35FIN_SRID, KKJ3_YKJ_SRID),
}

// language=SQL
val KKJ_TO_TM35FIN_SQL =
    """
  select
    postgis.st_x(t1.coord_kkj) as x1,
    postgis.st_y(t1.coord_kkj) as y1,
    postgis.st_x(t2.coord_kkj) as x2,
    postgis.st_y(t2.coord_kkj) as y2,
    postgis.st_x(t3.coord_kkj) as x3,
    postgis.st_y(t3.coord_kkj) as y3,
    a1, a2, delta_e, delta_n, b1, b2 
  from common.kkj_etrs_triangulation_network
    inner join common.kkj_etrs_triangle_corner_point t1 on kkj_etrs_triangulation_network.coord1_id = t1.id
    inner join common.kkj_etrs_triangle_corner_point t2 on kkj_etrs_triangulation_network.coord2_id = t2.id
    inner join common.kkj_etrs_triangle_corner_point t3 on kkj_etrs_triangulation_network.coord3_id = t3.id
  where kkj_etrs_triangulation_network.direction = 'KKJ_TO_TM35FIN'
"""
        .trimIndent()

// language=SQL
val TM35FIN_TO_KKJ_SQL =
    """
  select
    postgis.st_x(t1.coord_etrs) as x1,
    postgis.st_y(t1.coord_etrs) as y1,
    postgis.st_x(t2.coord_etrs) as x2,
    postgis.st_y(t2.coord_etrs) as y2,
    postgis.st_x(t3.coord_etrs) as x3,
    postgis.st_y(t3.coord_etrs) as y3,
    a1, a2, delta_e, delta_n, b1, b2
  from common.kkj_etrs_triangulation_network
    inner join common.kkj_etrs_triangle_corner_point t1 on kkj_etrs_triangulation_network.coord1_id = t1.id
    inner join common.kkj_etrs_triangle_corner_point t2 on kkj_etrs_triangulation_network.coord2_id = t2.id
    inner join common.kkj_etrs_triangle_corner_point t3 on kkj_etrs_triangulation_network.coord3_id = t3.id
  where kkj_etrs_triangulation_network.direction = 'TM35FIN_TO_KKJ'
"""
        .trimIndent()

@Component
class KkjTm35finTriangulationDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    @Cacheable(CACHE_KKJ_TM35FIN_TRIANGULATION_NETWORK, sync = true)
    fun fetchTriangulationNetwork(direction: TriangulationDirection): KkjTm35FinTriangulationNetwork {
        logger.daoAccess(AccessType.FETCH, HeightTriangle::class)
        val sql =
            when (direction) {
                TriangulationDirection.KKJ_TO_TM35FIN -> KKJ_TO_TM35FIN_SQL
                TriangulationDirection.TM35FIN_TO_KKJ -> TM35FIN_TO_KKJ_SQL
            }

        val triangles =
            jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, i ->
                KkjTm35finTriangle(
                    corner1 = rs.getPoint("x1", "y1"),
                    corner2 = rs.getPoint("x2", "y2"),
                    corner3 = rs.getPoint("x3", "y3"),
                    a1 = rs.getDouble("a1"),
                    a2 = rs.getDouble("a2"),
                    deltaE = rs.getDouble("delta_e"),
                    b1 = rs.getDouble("b1"),
                    b2 = rs.getDouble("b2"),
                    deltaN = rs.getDouble("delta_n"),
                    sourceSrid = direction.source,
                )
            }
        return KkjTm35FinTriangulationNetwork(triangles, direction.source, direction.target)
    }
}
