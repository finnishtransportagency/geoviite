package fi.fta.geoviite.infra.util

import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class VersionLogger(val jdbcTemplate: NamedParameterJdbcTemplate?) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun logVersion() {
        val sql =
            """
           select 
             version() as postgres_version,
             postgis.postgis_full_version() as postgis_version;
       """
                .trimIndent()
        jdbcTemplate?.let { jdbc ->
            jdbc.queryForObject(sql, mapOf<String, Any>()) { rs, _ ->
                logger.info("PostgreSQL Version: ${rs.getString("postgres_version")}")
                logger.info("PostGIS Version: ${rs.getString("postgis_version")}")
            }
        }
    }
}
