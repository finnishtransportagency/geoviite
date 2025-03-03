package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointParse
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.util.*
import java.sql.ResultSet
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

fun toRatkoOperatingPoint(rs: ResultSet): RatkoOperatingPoint {
    return RatkoOperatingPoint(
        externalId = rs.getOid("external_id"),
        name = rs.getString("name"),
        abbreviation = rs.getString("abbreviation"),
        uicCode = rs.getString("uic_code"),
        type = rs.getEnum("type"),
        location = rs.getPoint("x", "y"),
        trackNumberId = rs.getIntId("track_number_id"),
    )
}

@Service
@Component
class RatkoOperatingPointDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    @Transactional
    fun updateOperatingPoints(newPoints: List<RatkoOperatingPointParse>) {
        logger.info("Writing ${newPoints.size} operating points")
        jdbcTemplate.setUser()

        deleteRemovedPoints(newPoints)
        upsertPoints(newPoints)
    }

    private fun deleteRemovedPoints(newPoints: List<RatkoOperatingPointParse>) {
        val oldPointsIds =
            jdbcTemplate.query("""select external_id from layout.operating_point""") { rs, _ ->
                rs.getOid<RatkoOperatingPoint>("external_id")
            }
        val newPointsIds = newPoints.map { point -> point.externalId }.toSet()
        jdbcTemplate.batchUpdate(
            """delete from layout.operating_point where external_id = :id""",
            oldPointsIds
                .filter { id -> !newPointsIds.contains(id) }
                .map { id -> mapOf("id" to id.toString()) }
                .toTypedArray(),
        )
    }

    private fun upsertPoints(newPoints: List<RatkoOperatingPointParse>) {
        val sql =
            """
                insert into layout.operating_point
                  (external_id, name, abbreviation, uic_code, type, location, track_number_id)
                  (select
                  :externalId,
                     :name,
                     :abbreviation,
                     :uicCode,
                     :type::layout.operating_point_type,
                     postgis.st_setsrid(postgis.st_point(:x, :y), :srid),
                     tn.id
                     from layout.track_number_external_id tn
                     where tn.external_id = :trackNumberExternalId and tn.layout_context_id = 'main_official'
                  )
                  on conflict(external_id) do update set
                   external_id = excluded.external_id,
                   name = excluded.name,
                   abbreviation = excluded.abbreviation,
                   uic_code = excluded.uic_code,
                   type = excluded.type,
                   location = excluded.location,
                   track_number_id = excluded.track_number_id
                  where operating_point.name != excluded.name
                    or operating_point.abbreviation != excluded.abbreviation
                    or operating_point.uic_code != excluded.uic_code
                    or operating_point.type != excluded.type
                    or not postgis.st_equals(operating_point.location, excluded.location)
                    or operating_point.track_number_id != excluded.track_number_id
                """
                .trimIndent()

        jdbcTemplate.batchUpdate(
            sql,
            newPoints
                .map { point ->
                    mapOf(
                        "externalId" to point.externalId.toString(),
                        "name" to point.name,
                        "abbreviation" to point.abbreviation,
                        "uicCode" to point.uicCode,
                        "type" to point.type.name,
                        "x" to point.location.x,
                        "y" to point.location.y,
                        "srid" to LAYOUT_SRID.code,
                        "trackNumberExternalId" to point.trackNumberExternalId.toString(),
                    )
                }
                .toTypedArray(),
        )
    }

    fun getChangeTime(): Instant {
        return fetchLatestChangeTime(DbTable.OPERATING_POINT)
    }

    @Transactional(readOnly = true)
    fun getOperatingPoints(bbox: BoundingBox): List<RatkoOperatingPoint> {
        val sql =
            """
            select
              external_id,
              name,
              abbreviation,
              uic_code,
              type,
              postgis.st_x(location) as x,
              postgis.st_y(location) as y,
              track_number_id
              from layout.operating_point
              where postgis.st_contains(postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid), location)
        """
                .trimIndent()

        return jdbcTemplate.query(
            sql,
            mapOf(
                "x_min" to bbox.x.min,
                "x_max" to bbox.x.max,
                "y_min" to bbox.y.min,
                "y_max" to bbox.y.max,
                "layout_srid" to LAYOUT_SRID.code,
            ),
        ) { rs, _ ->
            toRatkoOperatingPoint(rs)
        }
    }

    @Transactional(readOnly = true)
    fun searchOperatingPoints(searchTerm: FreeText, resultLimit: Int): List<RatkoOperatingPoint> {
        val sql =
            """
            select
              external_id,
              name,
              abbreviation,
              uic_code,
              type,
              postgis.st_x(location) as x,
              postgis.st_y(location) as y,
              track_number_id
            from layout.operating_point
              where name ilike concat('%', regexp_replace(:searchTerm, '%|_', '\\\&'), '%') 
              or abbreviation ilike concat('%', regexp_replace(:searchTerm, '%|_', '\\\&'), '%')
              or external_id = :searchTerm
            order by name
            limit :resultLimit
        """
                .trimIndent()

        return jdbcTemplate.query(sql, mapOf("searchTerm" to searchTerm, "resultLimit" to resultLimit)) { rs, _ ->
            toRatkoOperatingPoint(rs)
        }
    }
}
