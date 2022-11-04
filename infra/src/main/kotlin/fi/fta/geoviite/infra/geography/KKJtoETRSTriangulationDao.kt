package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.configuration.CACHE_KKJ_ETRS_TRIANGULATION_NETWORK
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getPoint
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class KKJtoETRSTriangulationDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    @Cacheable(CACHE_KKJ_ETRS_TRIANGULATION_NETWORK, sync = true)
    fun fetchTriangulationNetwork(): List<KKJtoETRSTriangle> {
        val sql = """
          select
            postgis.st_x(t1.coord_kkj) as x1,
            postgis.st_y(t1.coord_kkj) as y1,
            postgis.st_x(t2.coord_kkj) as x2,
            postgis.st_y(t2.coord_kkj) as y2,
            postgis.st_x(t3.coord_kkj) as x3,
            postgis.st_y(t3.coord_kkj) as y3,
              a1, a2, delta_e, delta_n, b1, b2 from common.kkj_etrs_triangulation_network
            inner join common.kkj_etrs_triangle_corner_point t1 on kkj_etrs_triangulation_network.coord1_id = t1.id
            inner join common.kkj_etrs_triangle_corner_point t2 on kkj_etrs_triangulation_network.coord2_id = t2.id
            inner join common.kkj_etrs_triangle_corner_point t3 on kkj_etrs_triangulation_network.coord3_id = t3.id
        """.trimIndent()
        logger.daoAccess(AccessType.FETCH, HeightTriangle::class)
        return jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, i ->
            val heightTriangle = KKJtoETRSTriangle(
                corner1 = rs.getPoint("x1", "y1"),
                corner2 = rs.getPoint("x2", "y2"),
                corner3 = rs.getPoint("x3", "y3"),
                a1 = rs.getDouble("a1"),
                a2 = rs.getDouble("a2"),
                deltaE = rs.getDouble("delta_e"),
                b1 = rs.getDouble("b1"),
                b2 = rs.getDouble("b2"),
                deltaN = rs.getDouble("delta_n")
            )
            heightTriangle
        }
    }
}
