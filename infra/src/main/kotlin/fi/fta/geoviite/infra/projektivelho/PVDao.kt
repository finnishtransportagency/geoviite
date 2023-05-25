package fi.fta.geoviite.infra.projektivelho

import PVAssignment
import PVDictionaryCode
import PVDictionaryEntry
import PVDictionaryType
import PVDictionaryType.*
import PVDocument
import PVDocumentHeader
import PVDocumentStatus
import PVDictionaryName
import PVProject
import PVProjectGroup
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.logging.AccessType.*
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.projektivelho.PVFetchStatus.WAITING
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Transactional(readOnly = true)
@Component
class PVDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    @Transactional
    fun insertFileMetadata(
        oid: Oid<PVDocument>,
        metadata: PVApiFileMetadata,
        latestVersion: PVApiLatestVersion,
        status: PVDocumentStatus,
        assignmentOid: Oid<PVAssignment>?,
        projectOid: Oid<PVProject>?,
        projectGroupOid: Oid<PVProjectGroup>?,
    ): IntId<PVApiFileMetadata> {
        val sql = """
            insert into projektivelho.file_metadata(
                oid,
                filename,
                file_version,
                file_change_time,
                description,
                document_type_code,
                material_state_code,
                material_category_code,
                material_group_code,
                status,
                assignment_oid,
                project_oid,
                project_group_oid
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
                :status::projektivelho.file_status,
                :assignment_oid,
                :project_oid,
                :project_group_oid
            ) 
            on conflict (oid) do 
              update set
                filename = :filename,
                file_version = :file_version,
                file_change_time = :file_change_time,
                description = :description,
                document_type_code = :document_type,
                material_state_code = :material_state,
                material_category_code = :material_category,
                material_group_code = :material_group,
                status = :status::projektivelho.file_status,
                assignment_oid = :assignment_oid,
                project_oid = :project_oid,
                project_group_oid = :project_group_oid
              where projektivelho.file_metadata.file_version <> :file_version
        """.trimIndent()
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
            "assignment_oid" to assignmentOid,
            "project_oid" to  projectOid,
            "project_group_oid" to projectGroupOid,
        )
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params)

        // We can't get the id via a returning clause since it won't see the row id if it's not updated
        val selectSql = "select id from projektivelho.file_metadata where oid = :oid"
        val selectParams = mapOf("oid" to oid)
        return jdbcTemplate.query(selectSql, selectParams) { rs, _ ->
            rs.getIntId<PVApiFileMetadata>("id")
        }.single().also { id -> logger.daoAccess(UPSERT, PVApiFileMetadata::class, id) }
    }

    @Transactional
    fun upsertProject(project: PVApiProject) {
        val sql = """
            insert into projektivelho.project (oid, name, state_code, created_at, modified)
            values (:oid, :name, :state_code, :created_at, :modified)
            on conflict (oid) do update 
              set name = :name, 
                  state_code = :state_code,
                  created_at = :created_at,
                  modified = :modified
              where projektivelho.project.name <> :name
                 or projektivelho.project.state_code <> :state_code
                 or projektivelho.project.created_at <> :created_at
                 or projektivelho.project.modified <> :modified;
        """.trimIndent()
        val params = mapOf(
            "oid" to project.oid,
            "name" to project.properties.name,
            "state_code" to project.properties.state,
            "created_at" to Timestamp.from(project.createdAt),
            "modified" to Timestamp.from(project.modified),
        )
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params)
        logger.daoAccess(UPSERT, PVProject::class, project.oid)
    }

    @Transactional
    fun upsertProjectGroup(projectGroup: PVApiProjectGroup) {
        val sql = """
            insert into projektivelho.project_group (oid, name, state_code, created_at, modified)
            values (:oid, :name, :state_code, :created_at, :modified)
            on conflict (oid) do update 
              set name = :name,
                  state_code = :state_code,
                  created_at = :created_at,
                  modified = :modified
              where projektivelho.project_group.name <> :name
                 or projektivelho.project_group.state_code <> :state_code
                 or projektivelho.project_group.created_at <> :created_at
                 or projektivelho.project_group.modified <> :modified
        """.trimIndent()
        val params = mapOf(
            "oid" to projectGroup.oid,
            "name" to projectGroup.properties.name,
            "state_code" to projectGroup.properties.state,
            "created_at" to Timestamp.from(projectGroup.createdAt),
            "modified" to Timestamp.from(projectGroup.modified),
        )
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params)
        logger.daoAccess(UPSERT, PVProjectGroup::class, projectGroup.oid)
    }

    @Transactional
    fun upsertAssignment(assignment: PVApiAssignment) {
        val sql = """
            insert into projektivelho.assignment (oid, name, state_code, created_at, modified)
            values (:oid, :name, :state_code, :created_at, :modified)
            on conflict (oid) do update 
              set name = :name,
                  state_code = :state_code,
                  created_at = :created_at,
                  modified = :modified
              where projektivelho.assignment.name <> :name
                 or projektivelho.assignment.state_code <> :state_code
                 or projektivelho.assignment.created_at <> :created_at
                 or projektivelho.assignment.modified <> :modified
        """.trimIndent()
        val params = mapOf(
            "oid" to assignment.oid,
            "name" to assignment.properties.name,
            "state_code" to assignment.properties.state,
            "created_at" to Timestamp.from(assignment.createdAt),
            "modified" to Timestamp.from(assignment.modified),
        )
        logger.daoAccess(UPSERT, PVAssignment::class, assignment.oid)
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params)
    }

    @Transactional
    fun insertFileContent(content: String, metadataId: IntId<PVApiFileMetadata>): IntId<PVApiFileMetadata> {
        val sql = """
            insert into projektivelho.file(
                content,
                file_metadata_id
            ) values (
                xmlparse(document :content),
                :metadata_id
            ) returning file_metadata_id
        """.trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("metadata_id" to metadataId.intValue, "content" to content)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getIntId<PVApiFileMetadata>("file_metadata_id")
        }.single()
            .also { id -> logger.daoAccess(INSERT, "PVApiFileContent", id) }
    }

    @Transactional
    fun insertFetchInfo(searchToken: PVId, validUntil: Instant): IntId<PVSearch> {
        val sql = """
            insert into projektivelho.search(
                status,
                token,
                valid_until
            ) values (
                :status::projektivelho.search_status,
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
            update projektivelho.search 
            set status = :status::projektivelho.search_status
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
            from projektivelho.file_metadata 
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
            from projektivelho.search 
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
            update projektivelho.file_metadata
            set status = :status::projektivelho.file_status
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
              metadata.project_oid,
              project.name project_name,
              project_state.name project_state,
              metadata.project_group_oid,
              project_group.name project_group_name,
              project_group_state.name project_group_state,
              metadata.assignment_oid,
              assignment.name assignment_name,
              assignment_state.name assignment_state,
              document_type.name document_type,
              material_state.name material_state,
              material_group.name material_group,
              material_category.name material_category
            from projektivelho.file_metadata metadata
              left join projektivelho.project on project.oid = metadata.project_oid
              left join projektivelho.project_state on project.state_code = project_state.code
              left join projektivelho.project_group on project_group.oid = metadata.project_group_oid
              left join projektivelho.project_state project_group_state on project_group.state_code = project_group_state.code
              left join projektivelho.assignment on assignment.oid = metadata.assignment_oid
              left join projektivelho.project_state assignment_state on assignment.state_code = assignment_state.code
              left join projektivelho.document_type on document_type.code = metadata.document_type_code
              left join projektivelho.material_state on material_state.code = metadata.material_state_code
              left join projektivelho.material_group on material_group.code = metadata.material_group_code
              left join projektivelho.material_category on material_category.code = metadata.material_category_code
            where (:status::projektivelho.file_status is null or status = :status::projektivelho.file_status)
        """.trimIndent()
        val params = mapOf("status" to status?.name)
        return jdbcTemplate.query(sql, params) { rs, _ -> PVDocumentHeader(
            project = rs.getOidOrNull<PVProject>("project_oid")?.let{ oid ->
                PVProject(oid, rs.getVelhoName("project_name"), rs.getVelhoName("project_state"))
            },
            projectGroup = rs.getOidOrNull<PVProjectGroup>("project_group_oid")?.let { oid ->
                PVProjectGroup(oid, rs.getVelhoName("project_group_name"), rs.getVelhoName("project_state"))
            },
            assignment = rs.getOidOrNull<PVAssignment>("assignment_oid")?.let { oid ->
                PVAssignment(oid, rs.getVelhoName("assignment_name"), rs.getVelhoName("project_state"))
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
            from projektivelho.file_metadata metadata
              inner join projektivelho.file content on metadata.id = content.file_metadata_id
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

    fun fetchDictionary(type: PVDictionaryType): Map<PVDictionaryCode, PVDictionaryName> {
        val sql = "select code, name from ${tableName(type)}"
        return jdbcTemplate.query(sql, mapOf<String,Any>()) { rs, _ ->
            rs.getVelhoCode("code") to rs.getVelhoName("name")
        }.associate { it }.also { _ -> logger.daoAccess(FETCH, PVDictionaryType::class) }
    }

    private fun tableName(type: PVDictionaryType) = "projektivelho.${when(type) {
        DOCUMENT_TYPE -> "document_type"
        MATERIAL_STATE -> "material_state"
        MATERIAL_CATEGORY -> "material_category"
        MATERIAL_GROUP -> "material_group"
        TECHNICS_FIELD -> "technics_field"
        PROJECT_STATE -> "project_state"
    }}"
}
