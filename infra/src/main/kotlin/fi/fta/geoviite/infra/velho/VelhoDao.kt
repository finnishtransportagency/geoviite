package fi.fta.geoviite.infra.velho

import VelhoAssignment
import VelhoCode
import VelhoDocument
import VelhoDocumentHeader
import VelhoEncoding
import VelhoName
import VelhoProject
import VelhoProjectGroup
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.velho.VelhoEncodingType.DOCUMENT_TYPE
import fi.fta.geoviite.infra.velho.VelhoEncodingType.MATERIAL_GROUP
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

enum class VelhoEncodingType {
    DOCUMENT_TYPE,
    MATERIAL_GROUP,
}

@Transactional
@Component
class VelhoDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    fun insertFileMetadata(
        username: UserName,
        oid: String,
        metadata: Metadata,
        latestVersion: LatestVersion,
        status: FileStatus
    ): IntId<Metadata> {
        val sql = """
            insert into integrations.projektivelho_file_metadata(
                oid,
                filename,
                file_version,
                file_change_time,
                description,
                file_state,
                category,
                doc_type,
                asset_group,
                status
            ) values (
                :oid,
                :filename,
                :file_version,
                :file_change_time,
                :description,
                :file_state,
                :category,
                :doc_type,
                :asset_group,
                :status::integrations.projektivelho_file_status
            ) returning id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(
            sql, mapOf<String, Any?>(
                "filename" to latestVersion.name,
                "oid" to oid,
                "file_version" to latestVersion.version,
                "description" to metadata.description,
                "file_state" to metadata.state?.let { fetchDictionaryEntry(username, it) },
                "category" to metadata.category?.let { fetchDictionaryEntry(username, it) },
                "doc_type" to metadata.documentType?.let { fetchDictionaryEntry(username, it) },
                "asset_group" to metadata.group?.let { fetchDictionaryEntry(username, it) },
                "file_change_time" to Timestamp.from(latestVersion.changeTime),
                "status" to status.name
            )
        ) { rs, _ ->
            rs.getIntId<Metadata>("id")
        }.single()
    }

    fun insertFileContent(username: UserName, content: String, metadataId: IntId<Metadata>): IntId<ProjektiVelhoFile> {
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
        return jdbcTemplate.query(
            sql,
            mapOf<String, Any>("metadata_id" to metadataId.intValue, "content" to content)
        ) { rs, _ ->
            rs.getIntId<ProjektiVelhoFile>("metadata_id")
        }.single()
    }

    fun insertFetchInfo(username: UserName, searchToken: String, validUntil: Instant): IntId<ProjektiVelhoSearch> {
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
        return jdbcTemplate.query(
            sql,
            mapOf<String, Any>(
                "token" to searchToken,
                "status" to FetchStatus.WAITING.name,
                "valid_until" to Timestamp.from(validUntil)
            )
        ) { rs, _ ->
            rs.getIntId<ProjektiVelhoSearch>("id")
        }.single()
    }

    fun updateFetchState(
        username: UserName,
        id: IntId<ProjektiVelhoSearch>,
        status: FetchStatus
    ): IntId<ProjektiVelhoSearch> {
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
            select id, token, status, valid_until from integrations.projektivelho_search order by valid_until desc limit 1
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

    fun updateFileStatus(id: IntId<VelhoDocument>, status: FileStatus): IntId<VelhoDocument> {
        logger.daoAccess(AccessType.UPDATE, VelhoDocument::class, id)
        val sql = """
            update integrations.projektivelho_file_metadata
            set status = :status
            where id = :id
            returning id
        """.trimIndent()
        val params = mapOf("id" to id.intValue, "status" to status.name)
        return getOne<VelhoDocument, IntId<VelhoDocument>?>(id, jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getIntId("id")
        })
    }

    fun getDocumentHeaders(status: FileStatus?): List<VelhoDocumentHeader> {
        logger.daoAccess(AccessType.FETCH, VelhoDocument::class)
        val sql = """
            select 
              id,
              oid,
              filename,
              version,
              description,
              file_state,
              category,
              doc_type,
              asset_group,
              change_time, 
              status
            from integrations.projektivelho_file_metadata
            where (:status is null or status = :status)
        """.trimIndent()
        val params = mapOf("status" to status?.name)
        return jdbcTemplate.query(sql, params) { rs, _ -> VelhoDocumentHeader(
            project = VelhoProject(
                oid = rs.getOid("project_oid"),
                name = rs.getVelhoName("project_name"),
                group = VelhoProjectGroup(
                    oid = rs.getOid("project_group_oid"),
                    name =  rs.getVelhoName("project_group_name"),
                ),
            ),
            assignment = VelhoAssignment(
                name = rs.getVelhoName("assignment_name"),
                oid = rs.getOid("assignment_oid"),
            ),
            materialGroup = getEncoded(MATERIAL_GROUP, rs.getVelhoCode("asset_group")),
            document = VelhoDocument(
                id = rs.getIntId("id"),
                oid = rs.getOid("oid"),
                name = rs.getFileName("filename"),
                description = rs.getFreeTextOrNull("description"),
                type = getEncoded(DOCUMENT_TYPE, rs.getVelhoCode("doc_type")),
                modified = rs.getInstant("change_time"),
                status = rs.getEnum("status"),
            ),
        )}
    }

    fun getFileContent(id: IntId<VelhoDocument>): InfraModelFile? {
        logger.daoAccess(AccessType.FETCH, InfraModelFile::class, id)
        val sql = """
            select 
              metadata.filename,
              xmlserialize(document content.content as varchar) as file_content
            from integrations.projektivelho_file_metadata metadata
              inner join integrations.projektivelho_file content on metadata.id = content.metadata_id
            where metadata.id = :id
        """.trimIndent()
        val params = mapOf("id" to id.intValue)
        return getOptional(id, jdbcTemplate.query(sql, params) { rs, _ -> InfraModelFile(
            name = rs.getFileName("filename"),
            content = rs.getString("file_content"),
        ) })
    }

    private fun getEncoded(_type: VelhoEncodingType, code: VelhoCode): VelhoEncoding {
        // TODO: GVT-1797
        return VelhoEncoding(code, VelhoName("TBD FETCH $code"))
    }

    fun upsertDictionary(user: UserName, type: String, code: String, name: String) {
        val sql = """
            insert into integrations.projektivelho_dictionary(
                type,
                code,
                name
            ) values (
                :type::integrations.projektivelho_dictionary_type,
                :code,
                :name
            ) on conflict do nothing
            returning id
        """.trimIndent()
        jdbcTemplate.setUser(user)
        jdbcTemplate.query(sql, mapOf<String, Any>("type" to type, "code" to code, "name" to name)) { rs, _ ->

        }
    }

    fun fetchDictionaryEntry(user: UserName, code: String): Int {
        val sql = """
            select id 
            from integrations.projektivelho_dictionary
            where code = :code
        """.trimIndent()
        jdbcTemplate.setUser(user)
        return jdbcTemplate.query(sql, mapOf("code" to code)) { rs, _ ->
            rs.getInt("id")
        }.single()
    }
}
