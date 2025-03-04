package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.setPgroutingPath
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class RouteNode(val id: IntId<LayoutNode>, val location: Point)

data class RouteLeg(
    val edgeId: IntId<LayoutEdge>,
    val startNode: RouteNode,
    val endNode: RouteNode,
    val trackIds: List<IntId<LocationTrack>>,
)

data class Route(val start: Point, val end: Point, val legs: List<RouteLeg>) {
    val startNode = legs.first().startNode
    val endNode = legs.last().endNode
}

@Transactional(readOnly = true)
@Component
class RoutingDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun updateRoutes() {
        jdbcTemplate.query("select pgrouting.update_network()", emptyMap<String, Any>()) { _, _ -> }
    }

    fun getRoute(start: Point, end: Point): Route {
        val params =
            mapOf(
                "start_x" to start.x,
                "start_y" to start.y,
                "end_x" to end.x,
                "end_y" to end.y,
                "srid" to LAYOUT_SRID.code,
                "limit" to 100,
            )
        val sql =
            """
            with
              start_node as (
                select id, location
                  from pgrouting.node
                  where postgis.st_dwithin(postgis.st_setsrid(postgis.st_point(:start_x, :start_y), :srid), location, :limit)
                  order by postgis.st_distance(location, postgis.st_setsrid(postgis.st_point(:start_x, :start_y), :srid))
                  limit 1
              ),
              end_node as (
                select id, location
                  from pgrouting.node
                  where postgis.st_dwithin(postgis.st_setsrid(postgis.st_point(:end_x, :end_y), :srid), location, :limit)
                  order by postgis.st_distance(location, postgis.st_setsrid(postgis.st_point(:end_x, :end_y), :srid))
                  limit 1
              )
            select
              *
              from (
                select
                  path_node.id as start_node_id,
                  postgis.st_x(path_node.location) as start_node_x,
                  postgis.st_y(path_node.location) as start_node_y,
                  lead(path_node.id) over (order by path.seq) as end_node_id,
                  postgis.st_y(lead(path_node.location) over (order by path.seq)) as end_node_y,
                  postgis.st_y(lead(path_node.location) over (order by path.seq)) as end_node_x,
                  path_edge.id as edge_id,
                  path_edge.tracks as edge_tracks,
                  path_edge.length as edge_length
                  from start_node
                    cross join end_node
                    cross join pgrouting.pgr_dijkstra(
                      'select id, start_node_id::int as source, end_node_id::int as target, length::int as cost from pgrouting.edge',
                      start_node.id,
                      end_node.id,
                      false
                    ) as path
                    left join pgrouting.node path_node on path_node.id = path.node
                    left join pgrouting.edge path_edge on path_edge.id = path.edge
                  order by path.seq
              ) tmp
              where edge_id is not null;
        """
                .trimIndent()
        jdbcTemplate.setPgroutingPath()
        val legs =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val startNode = RouteNode(rs.getIntId("start_node_id"), rs.getPoint("start_node_x", "start_node_y"))
                val endNode = RouteNode(rs.getIntId("end_node_id"), rs.getPoint("end_node_x", "end_node_y"))
                val edgeId = rs.getIntId<LayoutEdge>("edge_id")
                val trackIds = rs.getIntIdArray<LocationTrack>("edge_tracks")
                RouteLeg(edgeId, startNode, endNode, trackIds)
            }

        return Route(start, end, legs)
    }
}
