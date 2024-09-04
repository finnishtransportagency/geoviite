package fi.fta.geoviite.infra.migration

import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.util.getPointOrNull
import fi.fta.geoviite.infra.util.getRowVersion
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource

@Suppress("unused", "ClassName")
class V84__convert_km_post_locations : BaseJavaMigration() {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private data class Row(val rowVersion: LayoutRowVersion<TrackLayoutKmPost>, val location: Point)

    private fun migrateTable(jdbcTemplate: NamedParameterJdbcTemplate, table: String) {
        val sql =
            """
            select id, version, postgis.st_x(location) as x, postgis.st_y(location) as y
              from $table
        """
                .trimIndent()
        val rows =
            jdbcTemplate
                .query(sql) { rs, _ ->
                    rs.getPointOrNull("x", "y")?.let { point ->
                        rs.getRowVersion<TrackLayoutKmPost>("id", "version") to point
                    }
                }
                .filterNotNull()

        val updateSql =
            """
            update $table
            set
              location = postgis.st_point(:layout_x, :layout_y, :layout_srid),
              gk_location = postgis.st_point(:gk_x, :gk_y, :gk_srid)
            where id = :id and version = :version
        """
                .trimIndent()
        jdbcTemplate.batchUpdate(
            updateSql,
            rows
                .map { (version, oldLayoutLocation) ->
                    try {
                        val gkLocation = transformFromLayoutToGKCoordinate(oldLayoutLocation)
                        val newLayoutLocation = transformNonKKJCoordinate(gkLocation.srid, LAYOUT_SRID, gkLocation)
                        mapOf(
                            "id" to version.id.intValue,
                            "version" to version.version,
                            "layout_x" to newLayoutLocation.x,
                            "layout_y" to newLayoutLocation.y,
                            "layout_srid" to LAYOUT_SRID.code,
                            "gk_x" to gkLocation.x,
                            "gk_y" to gkLocation.y,
                            "gk_srid" to gkLocation.srid.code,
                        )
                    } catch (e: CoordinateTransformationException) {
                        logger.error("Could not transform location for km post $version in $table", e)
                        throw e
                    }
                }
                .toTypedArray(),
        )
    }

    override fun migrate(context: Context?) {
        val connection = requireNotNull(context?.connection) { "Can't run migrations without DB connection" }
        val jdbcTemplate = NamedParameterJdbcTemplate(SingleConnectionDataSource(connection, true))
        migrateTable(jdbcTemplate, "layout.km_post")
        migrateTable(jdbcTemplate, "layout.km_post_version")
    }
}
