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
    fun insertFileMetadata(username: UserName, oid: String, metadata: Metadata, latestVersion: LatestVersion, status: FileStatus): IntId<ProjektiVelhoFile> {
        val sql = """
            insert into integrations.projektivelho_file_metadata(
                oid,
                filename,
                version,
                change_time,
                description,
                file_state,
                category,
                doc_type,
                asset_group,
                status
            ) values (
                :oid,
                :filename,
                :version,
                :change_time,
                :description,
                :file_state,
                :category,
                :doc_type,
                :asset_group,
                :status::integrations.projektivelho_file_status
            ) returning id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, mapOf<String, Any?>(
            "filename" to latestVersion.name,
            "oid" to oid,
            "version" to latestVersion.version,
            "description" to metadata.description,
            "file_state" to metadata.state,
            "category" to metadata.category,
            "doc_type" to metadata.documentType,
            "asset_group" to metadata.group,
            "change_time" to Timestamp.from(latestVersion.changeTime),
            "status" to status.name)
        ) { rs, _ ->
            rs.getIntId<ProjektiVelhoFile>("id")
        }.single()
    }

    fun insertFileContent(username: UserName, content: String, metadataId: IntId<ProjektiVelhoFile>): Int {
        val sql = """
            insert into integrations.projektivelho_file(
                content,
                metadata_id
            ) values (
                xmlparse(document :content),
                :metadata_id
            ) returning metadata_id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, mapOf<String, Any>("metadata_id" to metadataId.intValue, "content" to content)) { rs, _ ->
            rs.getInt("metadata_id")
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
            select change_time, oid from integrations.projektivelho_file_metadata order by change_time desc, oid desc limit 1
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
