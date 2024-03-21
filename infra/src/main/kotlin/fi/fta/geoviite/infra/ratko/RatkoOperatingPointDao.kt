package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.integration.RatkoPush
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointParse
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.batchUpdate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Component
class RatkoOperatingPointDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    @Transactional
    fun writeOperatingPoints(points: List<RatkoOperatingPointParse>) {
        logger.info("Writing ${points.size} operating points")
        jdbcTemplate.execute("delete from layout.operating_point") { it.execute() }
        val sql = """
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
                 from layout.track_number tn
                 where tn.external_id = :trackNumberExternalId
              )
              on conflict(external_id) do nothing
            """.trimIndent()

        val updateCounts = jdbcTemplate.batchUpdate(sql, points.map { point ->
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
        }.toTypedArray())
        updateCounts.forEachIndexed { index, numUpdates ->
            val point = points[index]
            if (numUpdates == 0) {
                logger.info("unable to save operating point with id ${point.externalId}: Maybe track number ${point.trackNumberExternalId} was not found, or OID conflicted?")
            }
        }
    }

    fun getChangeTime(): Instant {
        val sql = """
            select coalesce(max(update_time), now()) as update_time from layout.operating_point
        """.trimIndent()

        return jdbcTemplate.queryOne(sql) { rs, _ -> rs.getInstant("update_time") }
    }

    @Transactional(readOnly=true)
    fun getOperatingPoints(bbox: BoundingBox): List<RatkoOperatingPoint> {
        val sql = """
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
        """.trimIndent()

        return jdbcTemplate.query(
            sql, mapOf(
                "x_min" to bbox.x.min,
                "x_max" to bbox.x.max,
                "y_min" to bbox.y.min,
                "y_max" to bbox.y.max,
                "layout_srid" to LAYOUT_SRID.code
            )
        ) { rs, _ ->
            RatkoOperatingPoint(
                rs.getOid("external_id"),
                rs.getString("name"),
                rs.getString("abbreviation"),
                rs.getString("uic_code"),
                rs.getEnum("type"),
                rs.getPoint("x", "y"),
                rs.getIntId("track_number_id"),
            )
        }
    }
}
