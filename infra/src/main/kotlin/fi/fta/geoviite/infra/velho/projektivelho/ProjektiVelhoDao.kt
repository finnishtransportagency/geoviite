package fi.fta.geoviite.infra.velho.projektivelho

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Transactional
@Component
class ProjektiVelhoDao(jdbcTemplateParam: NamedParameterJdbcTemplate?): DaoBase(jdbcTemplateParam) {
    fun insertFile(username: UserName, oid: String, fileId: Int?, timestamp: Instant) {
        val sql = """
            insert into integrations.projektivelho_file(
                oid,
                file_id,
                change_time,
                status
            ) values (
                :oid,
                :file_id,
                :change_time,
                :status::integrations.projektivelho_file_status
            ) returning id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        jdbcTemplate.query(sql, mapOf<String, Any?>(
            "file_id" to fileId,
            "oid" to oid,
            "change_time" to Timestamp.from(timestamp),
            "status" to if (fileId != null) "IMPORTED" else "NOT_IM")
        ) { rs, _ ->
            rs.getInt("id")
        }
    }

    fun insertFileContent(username: UserName, content: String, filename: String): Int {
        val sql = """
            insert into integrations.projektivelho_file_content(
                content,
                filename
            ) values (
                xmlparse(document :content),
                :filename
            ) returning id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, mapOf<String, Any>("filename" to filename, "content" to content)) { rs, _ ->
            rs.getInt("id")
        }.single()
    }

    fun insertFetchInfo(username: UserName, searchToken: String, validUntil: Instant): Int {
        val sql = """
            insert into integrations.projektivelho_search(
                status,
                token,
                valid_until
            ) values (
                :status::integrations.projektivelho_search_status,
                :token,
                :valid_until
            ) returning id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, mapOf<String, Any>("token" to searchToken, "status" to FetchStatus.WAITING.name, "valid_until" to Timestamp.from(validUntil))) { rs, _ ->
            rs.getInt("id")
        }.single()
    }

    fun updateFetchState(username: UserName, id: IntId<ProjektiVelhoSearch>, status: FetchStatus): IntId<ProjektiVelhoSearch> {
        val sql = """
            update integrations.projektivelho_search set 
            status = :status::integrations.projektivelho_search_status
            where id = :id
            returning id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, mapOf<String, Any>("status" to status.name, "id" to id.intValue)) { rs, _ ->
            rs.getIntId<ProjektiVelhoSearch>("id")
        }.single()
    }

    fun fetchLatestFile(username: UserName): Pair<String, Instant>? {
        val sql = """
            select change_time, oid from integrations.projektivelho_file order by change_time desc limit 1
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
            rs.getString("oid") to rs.getInstant("change_time")
        }.firstOrNull()
    }

    fun fetchLatestSearch(username: UserName): ProjektiVelhoSearch? {
        val sql = """
            select id, token, status, valid_until from integrations.projektivelho_search order by id desc limit 1
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
            ProjektiVelhoSearch(
                rs.getIntId("id"),
                rs.getString("token"),
                rs.getEnum<FetchStatus>("status"),
                rs.getInstant("valid_until")
            )
        }.firstOrNull()
    }
}
