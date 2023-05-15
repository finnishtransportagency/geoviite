package fi.fta.geoviite.infra.velho

import VelhoAssignment
import VelhoCode
import VelhoDocument
import VelhoDocumentHeader
import VelhoName
import VelhoProject
import VelhoProjectGroup
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.UPSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.velho.VelhoDictionaryType.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Transactional(readOnly = true)
@Component
class VelhoDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    @Transactional
    fun insertFileMetadata(
        username: UserName,
        oid: Oid<ProjektiVelhoFile>,
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
                projektivelho_document_type_code,
                projektivelho_material_state_code,
                projektivelho_material_category_code,
                projektivelho_material_group_code,
                status,
                projektivelho_assignment_id,
                projektivelho_project_id,
                projektivelho_project_group_id
            ) values (
                :oid,
                :filename,
                :file_version,
                :file_change_time,
                :description,
                :document_type,
                :material_state,
                :material_category,
                :material_group,
                :status::integrations.projektivelho_file_status,
                :projektivelho_assignment_id,
                :projektivelho_project_id,
                :projektivelho_project_group_id
            ) returning id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        val params = mapOf(
            "filename" to latestVersion.name,
            "oid" to oid,
            "file_version" to latestVersion.version,
            "description" to metadata.description,
            "document_type" to metadata.documentType,
            "material_state" to metadata.materialState,
            "material_category" to metadata.materialCategory,
            "material_group" to metadata.materialGroup,
            "file_change_time" to Timestamp.from(latestVersion.changeTime),
            "status" to status.name,
//            "projektivelho_assignment_id" to,
//            "projektivelho_project_id" to ,
//            "projektivelho_project_group_id" to ,
        )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<Metadata>("id") }.single()
    }

    @Transactional
    fun insertFileContent(username: UserName, content: String, metadataId: IntId<Metadata>): IntId<Metadata> {
        val sql = """
            insert into integrations.projektivelho_file(
                content,
                projektivelho_file_metadata_id
            ) values (
                xmlparse(document :content),
                :metadata_id
            ) returning projektivelho_file_metadata_id
        """.trimIndent()
        jdbcTemplate.setUser(username)
        val params = mapOf("metadata_id" to metadataId.intValue, "content" to content)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getIntId<Metadata>("metadata_id")
        }.single()
    }

    @Transactional
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

    @Transactional
    fun updateFetchState(
        username: UserName,
        id: IntId<ProjektiVelhoSearch>,
        status: FetchStatus
    ): IntId<ProjektiVelhoSearch> {
        val sql = """
            update integrations.projektivelho_search 
            set status = :status::integrations.projektivelho_search_status
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
            select change_time, oid 
            from integrations.projektivelho_file_metadata 
            order by change_time desc, oid desc 
            limit 1
        """.trimIndent()
        jdbcTemplate.setUser(username)
        return jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
            rs.getString("oid") to rs.getInstant("change_time")
        }.firstOrNull()
    }

    fun fetchLatestSearch(username: UserName): ProjektiVelhoSearch? {
        val sql = """
            select id, token, status, valid_until 
            from integrations.projektivelho_search 
            order by valid_until desc 
            limit 1
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

    @Transactional
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
        logger.daoAccess(FETCH, VelhoDocument::class)
        val sql = """
            select 
              metadata.id,
              metadata.oid,
              metadata.filename,
              metadata.version,
              metadata.description,
              metadata.change_time, 
              metadata.status,
              project.oid project_oid,
              project.name project_name,
              project_group.oid project_group_oid,
              project_group.name project_group_name,
              assignment.oid assignment_oid,
              assignment.name assignment_name,
              document_type.name document_type,
              material_state.name material_state,
              material_group.name material_group,
              material_category.name material_category
            from integrations.projektivelho_file_metadata metadata
              left join integrations.projektivelho_project project 
                on project.id = metadata.projektivelho_project_id
              left join integrations.projektivelho_project_group project_group 
                on project_group.id = metadata.projektivelho_project_group_id
              left join integrations.projektivelho_assignment assignment 
                on assignment.id = metadata.projektivelho_assignment_id
              left join integrations.projektivelho_document_type document_type 
                on document_type.code = metadata.projektivelho_document_type_code
              left join integrations.projektivelho_material_state material_state 
                on material_state.code = metadata.projektivelho_material_state_code
              left join integrations.projektivelho_material_group material_group 
                on material_group.code = metadata.projektivelho_material_group_code
              left join integrations.projektivelho_material_category material_category 
                on material_category.code = metadata.projektivelho_material_category_code
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
                oid = rs.getOid("assignment_oid"),
                name = rs.getVelhoName("assignment_name"),
            ),
            materialGroup = rs.getVelhoName("asset_group"),
            document = VelhoDocument(
                id = rs.getIntId("id"),
                oid = rs.getOid("oid"),
                name = rs.getFileName("filename"),
                description = rs.getFreeTextOrNull("description"),
                type = rs.getVelhoName("doc_type_name"),
                modified = rs.getInstant("change_time"),
                status = rs.getEnum("status"),
            ),
        )}
    }

    fun getFileContent(id: IntId<VelhoDocument>): InfraModelFile? {
        logger.daoAccess(FETCH, InfraModelFile::class, id)
        val sql = """
            select 
              metadata.filename,
              xmlserialize(document content.content as varchar) as file_content
            from integrations.projektivelho_file_metadata metadata
              inner join integrations.projektivelho_file content on metadata.id = content.projektivelho_file_metadata_id
            where metadata.id = :id
        """.trimIndent()
        val params = mapOf("id" to id.intValue)
        return getOptional(id, jdbcTemplate.query(sql, params) { rs, _ -> InfraModelFile(
            name = rs.getFileName("filename"),
            content = rs.getString("file_content"),
        ) })
    }

//    private fun getEncoded(_type: VelhoEncodingType, code: VelhoCode): VelhoEncoding {
//        // TODO: GVT-1797
//        return VelhoEncoding(code, VelhoName("TBD FETCH $code"))
//    }

    @Transactional
    fun upsertDictionary(user: UserName, type: VelhoDictionaryType, entries: List<DictionaryEntry>) {
        val sql = """
            insert into ${tableName(type)}(code, name) 
              values (:code, :name) 
              on conflict (code) do update set name = :name where name <> :name
        """.trimIndent()
        jdbcTemplate.setUser(user)
        val params = entries.map { entry -> mapOf(
            "code" to entry.code,
            "name" to entry.name,
        ) }.toTypedArray()
//        val sql = """
//            insert into integrations.projektivelho_dictionary(
//                type,
//                code,
//                name
//            ) values (
//                :type::integrations.projektivelho_dictionary_type,
//                :code,
//                :name
//            ) on conflict (code) do update set name = :name where name <> :name
//            returning id
//        """.trimIndent()
//        val params = mapOf(
//            "type" to encoding.type.name,
//            "code" to encoding.code,
//            "name" to encoding.name,
//        )
        jdbcTemplate.setUser(user)
        logger.daoAccess(UPSERT, DictionaryEntry::class, entries.map(DictionaryEntry::code))
        jdbcTemplate.batchUpdate(sql, params)
//        return requireNotNull(
//            jdbcTemplate.batchUpdate(sql, params) { rs, _ -> rs.getIntId<VelhoEncoding>("id") }
//        ).also { id -> logger.daoAccess(UPSERT, VelhoEncoding::class, id) }
    }

    fun fetchDictionary(type: VelhoDictionaryType): Map<VelhoCode, VelhoName> {
        val sql = "select code, name from ${tableName(type)}"
        return jdbcTemplate.query(sql, mapOf<String,Any>()) { rs, _ ->
            rs.getVelhoCode("code") to rs.getVelhoName("name")
        }.associate { it }.also { _ -> logger.daoAccess(FETCH, VelhoDictionaryType::class) }
    }
//    fun fetchDictionaryEntry(type: VelhoEncodingType, code: VelhoCode): IntId<VelhoEncoding> {
//        val sql = """
//            select id
//            from integrations.projektivelho_dictionary
//            where code = :code
//        """.trimIndent()
//        return jdbcTemplate.query(sql, mapOf("code" to code)) { rs, _ ->
//            rs.getIntId<VelhoEncoding>("id")
//        }.single().also { id -> logger.daoAccess(FETCH, VelhoEncoding::class, id) }
//    }

    private fun tableName(type: VelhoDictionaryType) = "integrations.${when(type) {
        DOCUMENT_TYPE -> "projektivelho_doc_type"
        MATERIAL_STATE -> "projektivelho_file_state"
        MATERIAL_CATEGORY -> "projektivelho_category"
        ASSET_GROUP -> "projektivelho_asset_group"
    }}"
}
