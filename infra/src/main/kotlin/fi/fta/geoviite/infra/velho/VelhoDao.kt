package fi.fta.geoviite.infra.velho

import PVAssignment
import PVCode
import PVDocument
import PVDocumentHeader
import PVDocumentStatus
import PVId
import PVName
import PVProject
import PVProjectGroup
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.logging.AccessType.*
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.velho.PVDictionaryType.*
import fi.fta.geoviite.infra.velho.PVFetchStatus.WAITING
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
        oid: Oid<PVDocument>,
        metadata: PVApiFileMetadata,
        latestVersion: PVApiLatestVersion,
        status: PVDocumentStatus
    ): IntId<PVApiFileMetadata> {
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
            ) 
            on conflict (oid) do 
              update set
                filename = :filename,
                file_version = :file_version,
                file_change_time = :file_change_time,
                description = :description,
                projektivelho_document_type_code = :document_type,
                projektivelho_material_state_code = :material_state,
                projektivelho_material_category_code = :material_category,
                projektivelho_material_group_code = :material_group,
                status = :status::integrations.projektivelho_file_status,
                projektivelho_assignment_id = :projektivelho_assignment_id,
                projektivelho_project_id = :projektivelho_project_id,
                projektivelho_project_group_id = :projektivelho_project_group_id
              where integrations.projektivelho_file_metadata.file_version <> :file_version
            returning id
        """.trimIndent()
        jdbcTemplate.setUser()
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
            "projektivelho_assignment_id" to null,
            "projektivelho_project_id" to  null,
            "projektivelho_project_group_id" to null,
        )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<PVApiFileMetadata>("id") }.single()
            .also { id -> logger.daoAccess(UPSERT, PVApiFileMetadata::class, id) }
    }

    @Transactional
    fun insertFileContent(content: String, metadataId: IntId<PVApiFileMetadata>): IntId<PVApiFileMetadata> {
        val sql = """
            insert into integrations.projektivelho_file(
                content,
                projektivelho_file_metadata_id
            ) values (
                xmlparse(document :content),
                :metadata_id
            ) returning projektivelho_file_metadata_id
        """.trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("metadata_id" to metadataId.intValue, "content" to content)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getIntId<PVApiFileMetadata>("projektivelho_file_metadata_id")
        }.single()
            .also { id -> logger.daoAccess(INSERT, "PVApiFileContent", id) }
    }

    @Transactional
    fun insertFetchInfo(searchToken: PVId, validUntil: Instant): IntId<PVSearch> {
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
        jdbcTemplate.setUser()
        val params = mapOf(
            "token" to searchToken,
            "status" to WAITING.name,
            "valid_until" to Timestamp.from(validUntil)
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getIntId<PVSearch>("id")
        }.single()
            .also { id -> logger.daoAccess(INSERT, PVSearch::class, id) }
    }

    @Transactional
    fun updateFetchState(id: IntId<PVSearch>, status: PVFetchStatus): IntId<PVSearch> {
        val sql = """
            update integrations.projektivelho_search 
            set status = :status::integrations.projektivelho_search_status
            where id = :id
            returning id
        """.trimIndent()
        jdbcTemplate.setUser()
        return jdbcTemplate.query(sql, mapOf<String, Any>("status" to status.name, "id" to id.intValue)) { rs, _ ->
            rs.getIntId<PVSearch>("id")
        }.single()
            .also { updatedId -> logger.daoAccess(UPDATE, PVSearch::class, updatedId) }
    }

    fun fetchLatestFile(): Pair<Oid<PVDocument>, Instant>? {
        val sql = """
            select change_time, oid 
            from integrations.projektivelho_file_metadata 
            order by change_time desc, oid desc 
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
            rs.getOid<PVDocument>("oid") to rs.getInstant("change_time")
        }.firstOrNull()
            .also { id -> logger.daoAccess(FETCH, "PVFileChangeTime", id?.first ?: "null") }
    }

    fun fetchLatestSearch(): PVSearch? {
        val sql = """
            select id, token, status, valid_until 
            from integrations.projektivelho_search 
            order by valid_until desc 
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
            PVSearch(
                rs.getIntId("id"),
                rs.getVelhoId("token"),
                rs.getEnum<PVFetchStatus>("status"),
                rs.getInstant("valid_until")
            )
        }.firstOrNull()
            .also { search -> logger.daoAccess(FETCH, PVSearch::class, search?.id ?: "null") }
    }

    @Transactional
    fun updateFileStatus(id: IntId<PVDocument>, status: PVDocumentStatus): IntId<PVDocument> {
        logger.daoAccess(UPDATE, PVDocument::class, id)
        val sql = """
            update integrations.projektivelho_file_metadata
            set status = :status::integrations.projektivelho_file_status
            where id = :id
            returning id
        """.trimIndent()
        val params = mapOf("id" to id.intValue, "status" to status.name)
        jdbcTemplate.setUser()
        return getOne<PVDocument, IntId<PVDocument>?>(id, jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getIntId("id")
        })
    }

    fun getDocumentHeaders(status: PVDocumentStatus? = null): List<PVDocumentHeader> {
        logger.daoAccess(FETCH, PVDocument::class)
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
            where (:status::integrations.projektivelho_file_status is null or status = :status::integrations.projektivelho_file_status)
        """.trimIndent()
        val params = mapOf("status" to status?.name)
        return jdbcTemplate.query(sql, params) { rs, _ -> PVDocumentHeader(
            // TODO: GVT-1860 These should be non-null
            project = rs.getOidOrNull<PVProject>("project_oid")?.let{ oid ->
                PVProject(
                    oid = oid,
                    name = rs.getVelhoName("project_name"),
                    group = PVProjectGroup(
                        oid = rs.getOid("project_group_oid"),
                        name =  rs.getVelhoName("project_group_name"),
                    ),
                )
            },
            // TODO: GVT-1860 These should be non-null
            assignment = rs.getOidOrNull<PVAssignment>("assignment_oid")?.let { oid ->
                PVAssignment(
                    oid = oid,
                    name = rs.getVelhoName("assignment_name"),
                )
            },
            document = PVDocument(
                id = rs.getIntId("id"),
                oid = rs.getOid("oid"),
                name = rs.getFileName("filename"),
                description = rs.getFreeTextOrNull("description"),
                type = rs.getVelhoName("document_type"),
                state = rs.getVelhoName("material_state"),
                group = rs.getVelhoName("material_group"),
                category = rs.getVelhoName("material_category"),
                modified = rs.getInstant("change_time"),
                status = rs.getEnum("status"),
            ),
        )}
    }

    fun getFileContent(id: IntId<PVDocument>): InfraModelFile? {
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

    @Transactional
    fun upsertDictionary(type: PVDictionaryType, entries: List<PVDictionaryEntry>) {
        val tableName = tableName(type)
        val sql = """
            insert into $tableName(code, name) 
              values (:code, :name) 
              on conflict (code) do update set name = :name where $tableName.name <> :name
        """.trimIndent()
        val params = entries.map { entry -> mapOf(
            "code" to entry.code,
            "name" to entry.name,
        ) }.toTypedArray()
        jdbcTemplate.setUser()
        logger.daoAccess(UPSERT, PVDictionaryEntry::class, entries.map(PVDictionaryEntry::code))
        jdbcTemplate.batchUpdate(sql, params)
    }

    fun fetchDictionary(type: PVDictionaryType): Map<PVCode, PVName> {
        val sql = "select code, name from ${tableName(type)}"
        return jdbcTemplate.query(sql, mapOf<String,Any>()) { rs, _ ->
            rs.getVelhoCode("code") to rs.getVelhoName("name")
        }.associate { it }.also { _ -> logger.daoAccess(FETCH, PVDictionaryType::class) }
    }

    private fun tableName(type: PVDictionaryType) = "integrations.${when(type) {
        DOCUMENT_TYPE -> "projektivelho_document_type"
        MATERIAL_STATE -> "projektivelho_material_state"
        MATERIAL_CATEGORY -> "projektivelho_material_category"
        MATERIAL_GROUP -> "projektivelho_material_group"
        TECHNICS_FIELD -> "projektivelho_technics_field"
    }}"
}
