package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteDao
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class ElementListingFile(val name: FileName, val content: String) : Loggable {
    override fun toLog() = name.toString()
}

@GeoviiteDao(readOnly = true)
class ElementListingFileDao @Autowired constructor(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun upsertElementListingFile(file: ElementListingFile) {
        val sql = """
            insert into layout.element_listing_file(
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

    fun getElementListingFile(): ElementListingFile? {
        val sql = """
            select name, content
            from layout.element_listing_file
            where id = 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf<String,Any>()) { rs, _ -> ElementListingFile(
            name = rs.getFileName("name"),
            content = rs.getString("content"),
        ) }
            .firstOrNull()
    }

    fun getLastFileListingTime(): Instant {
        val sql = """
            select max(change_time) change_time 
            from layout.element_listing_file
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf<String,Any>()) { rs, _ -> rs.getInstantOrNull("change_time") }
            .firstOrNull() ?: Instant.EPOCH
    }
}
