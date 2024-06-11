package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.aspects.GeoviiteDao
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class VerticalGeometryListingFile(val name: FileName, val content: String) : Loggable {
    override fun toLog(): String = logFormat("name" to name)
}

@GeoviiteDao(readOnly = true)
class VerticalGeometryListingFileDao @Autowired constructor(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun upsertVerticalGeometryListingFile(file: VerticalGeometryListingFile) {
        val sql = """
            insert into layout.vertical_geometry_listing_file(
              name,
              content,
              change_time,
              change_user
            )
            values (
              :name,
              :content,
              now(),
              current_setting('geoviite.edit_user')
            )
            on conflict (id) do update set 
              name = :name,
              content = :content,
              change_time = now(),
              change_user = current_setting('geoviite.edit_user')
        """.trimIndent()
        val params = mapOf(
            "name" to file.name,
            "content" to file.content,
        )
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params)
    }

    fun getVerticalGeometryListingFile(): VerticalGeometryListingFile? {
        val sql = """
            select name, content
            from layout.vertical_geometry_listing_file
            where id = 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf<String,Any>()) { rs, _ -> VerticalGeometryListingFile(
            name = rs.getFileName("name"),
            content = rs.getString("content"),
        ) }
            .firstOrNull()
    }

    fun getLastFileListingTime(): Instant {
        val sql = """
            select max(change_time) change_time 
            from layout.vertical_geometry_listing_file
        """.trimIndent()

        return jdbcTemplate.queryOne(sql) { rs , _ -> rs.getInstantOrNull("change_time") ?: Instant.EPOCH }
    }
}
