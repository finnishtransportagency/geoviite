package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.getFileName
import fi.fta.geoviite.infra.util.setUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class ElementListingFile(val name: FileName, val content: String)

@Transactional(readOnly = true)
@Component
class ElementListingFileDao @Autowired constructor(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun upsertElementListingFile(file: ElementListingFile) {
        val sql = """
            insert into layout.element_listing_file(name, content)
            values (:name, :content)
            on conflict (id) do update set name = :name, content = :content
        """.trimIndent()
        val params = mapOf(
            "name" to file.name,
            "content" to file.content,
        )
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params).also {
            logger.daoAccess(AccessType.UPSERT, ElementListingFile::class, file.name)
        }
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
            .also { file -> logger.daoAccess(AccessType.FETCH, ElementListingFile::class, "${file?.name}") }
    }
}
