package fi.fta.geoviite.infra.projektivelho

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.INSERT
import fi.fta.geoviite.infra.logging.AccessType.UPDATE
import fi.fta.geoviite.infra.logging.AccessType.UPSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.DOCUMENT_TYPE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_CATEGORY
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_GROUP
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_STATE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.PROJECT_STATE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.TECHNICS_FIELD
import fi.fta.geoviite.infra.projektivelho.PVFetchStatus.WAITING
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.UnsafeString
import fi.fta.geoviite.infra.util.formatForLog
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getFileName
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getOid
import fi.fta.geoviite.infra.util.getOidOrNull
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.getPVDictionaryCode
import fi.fta.geoviite.infra.util.getPVDictionaryName
import fi.fta.geoviite.infra.util.getPVId
import fi.fta.geoviite.infra.util.getPVProjectName
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.getUnsafeString
import fi.fta.geoviite.infra.util.getUnsafeStringOrNull
import fi.fta.geoviite.infra.util.setUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import kotlin.reflect.KClass

data class PVDocumentCounts(val suggested: Int, val rejected: Int)

@Transactional(readOnly = true)
@Component
class PVDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun insertDocumentMetadata(
        oid: Oid<PVDocument>,
        metadata: PVApiDocumentMetadata,
        latestVersion: PVApiLatestVersion,
        status: PVDocumentStatus,
        assignmentOid: Oid<PVAssignment>?,
        projectOid: Oid<PVProject>?,
        projectGroupOid: Oid<PVProjectGroup>?,
    ): RowVersion<PVDocument> {
        val name = clipToLength(PVApiLatestVersion::class, "name", 100, latestVersion.name)
        val description =
            metadata.description?.let { desc -> clipToLength(PVApiDocumentMetadata::class, "description", 500, desc) }
        val sql =
            """
                insert into projektivelho.document(
                    oid,
                    status,
                    filename,
                    description,
                    document_version,
                    document_change_time,
                    document_type_code,
                    material_state_code,
                    material_category_code,
                    material_group_code,
                    assignment_oid,
                    project_oid,
                    project_group_oid
                ) values (
                    :oid,
                    :status::projektivelho.document_status,
                    :filename,
                    :description,
                    :document_version,
                    :document_change_time,
                    :document_type,
                    :material_state,
                    :material_category,
                    :material_group,
                    :assignment_oid,
                    :project_oid,
                    :project_group_oid
                ) 
                on conflict (oid) do 
                  update set
                    status = :status::projektivelho.document_status,
                    filename = :filename,
                    description = :description,
                    document_version = :document_version,
                    document_change_time = :document_change_time,
                    document_type_code = :document_type,
                    material_state_code = :material_state,
                    material_category_code = :material_category,
                    material_group_code = :material_group,
                    assignment_oid = :assignment_oid,
                    project_oid = :project_oid,
                    project_group_oid = :project_group_oid
                  where projektivelho.document.document_version <> :document_version
            """
                .trimIndent()
        val params =
            mapOf(
                "oid" to oid,
                "status" to status.name,
                "filename" to name,
                "description" to description,
                "document_version" to latestVersion.version,
                "document_change_time" to Timestamp.from(latestVersion.changeTime),
                "document_type" to metadata.documentType,
                "material_state" to metadata.materialState,
                "material_category" to metadata.materialCategory,
                "material_group" to metadata.materialGroup,
                "assignment_oid" to assignmentOid,
                "project_oid" to projectOid,
                "project_group_oid" to projectGroupOid,
            )
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params)

        // We can't get the id via a returning clause since it won't see the row id if it's not
        // updated
        val selectSql = "select id, version from projektivelho.document where oid = :oid"
        val selectParams = mapOf("oid" to oid)
        return jdbcTemplate
            .query(selectSql, selectParams) { rs, _ -> rs.getRowVersion<PVDocument>("id", "version") }
            .single()
            .also { id -> logger.daoAccess(UPSERT, PVApiDocumentMetadata::class, id) }
    }

    @Transactional
    fun upsertProject(project: PVApiProject) {
        val name = clipToLength(PVApiProject::class, "name", PVProjectName.length.last, project.properties.name)
        val sql =
            """
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
            """
                .trimIndent()
        val params =
            mapOf(
                "oid" to project.oid,
                "name" to name,
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
        val name =
            clipToLength(PVApiProjectGroup::class, "name", PVProjectName.length.last, projectGroup.properties.name)
        val sql =
            """
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
            """
                .trimIndent()
        val params =
            mapOf(
                "oid" to projectGroup.oid,
                "name" to name,
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
        val name = clipToLength(PVApiAssignment::class, "name", PVProjectName.length.last, assignment.properties.name)
        val sql =
            """
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
            """
                .trimIndent()
        val params =
            mapOf(
                "oid" to assignment.oid,
                "name" to name,
                "state_code" to assignment.properties.state,
                "created_at" to Timestamp.from(assignment.createdAt),
                "modified" to Timestamp.from(assignment.modified),
            )
        logger.daoAccess(UPSERT, PVAssignment::class, assignment.oid)
        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params)
    }

    @Transactional
    fun insertDocumentContent(content: String, documentId: IntId<PVDocument>) {
        val sql =
            """
                insert into projektivelho.document_content(
                    content,
                    document_id
                ) values (
                    xmlparse(document :content),
                    :document_id
                )
            """
                .trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("document_id" to documentId.intValue, "content" to content)
        jdbcTemplate.update(sql, params)
        logger.daoAccess(INSERT, "fi.fta.geoviite.infra.projektivelho.PVDocument.content", documentId)
    }

    @Transactional
    fun insertFetchInfo(searchToken: PVId, validUntil: Instant): IntId<PVSearch> {
        val sql =
            """
                insert into projektivelho.search(
                    status,
                    token,
                    valid_until
                ) values (
                    :status::projektivelho.search_status,
                    :token,
                    :valid_until
                ) returning id
            """
                .trimIndent()
        jdbcTemplate.setUser()
        val params =
            mapOf("token" to searchToken, "status" to WAITING.name, "valid_until" to Timestamp.from(validUntil))
        return jdbcTemplate
            .query(sql, params) { rs, _ -> rs.getIntId<PVSearch>("id") }
            .single()
            .also { id -> logger.daoAccess(INSERT, PVSearch::class, id) }
    }

    @Transactional
    fun updateFetchState(id: IntId<PVSearch>, status: PVFetchStatus): IntId<PVSearch> {
        val sql =
            """
                update projektivelho.search 
                set status = :status::projektivelho.search_status
                where id = :id
                returning id
            """
                .trimIndent()
        jdbcTemplate.setUser()
        return jdbcTemplate
            .query(sql, mapOf<String, Any>("status" to status.name, "id" to id.intValue)) { rs, _ ->
                rs.getIntId<PVSearch>("id")
            }
            .single()
            .also { updatedId -> logger.daoAccess(UPDATE, PVSearch::class, updatedId) }
    }

    fun fetchLatestDocument(): Pair<Oid<PVDocument>, Instant>? {
        val sql =
            """
                select document_change_time, oid 
                from projektivelho.document 
                order by document_change_time desc, oid desc 
                limit 1
            """
                .trimIndent()
        return jdbcTemplate
            .query(sql, emptyMap<String, Any>()) { rs, _ ->
                rs.getOid<PVDocument>("oid") to rs.getInstant("document_change_time")
            }
            .firstOrNull()
            .also { v -> logger.daoAccess(FETCH, "${PVDocument::class.simpleName}.changeTime", v ?: "null") }
    }

    fun fetchLatestActiveSearch(): PVSearch? {
        val sql =
            """
                select id, token, status, valid_until 
                from projektivelho.search 
                where status not in ('ERROR', 'FINISHED')
                  and valid_until >= now()
                order by valid_until desc 
                limit 1
            """
                .trimIndent()
        return jdbcTemplate
            .query(sql, emptyMap<String, Any>()) { rs, _ ->
                PVSearch(
                    rs.getIntId("id"),
                    rs.getPVId("token"),
                    rs.getEnum<PVFetchStatus>("status"),
                    rs.getInstant("valid_until"),
                )
            }
            .firstOrNull()
            .also { search -> logger.daoAccess(FETCH, PVSearch::class, search?.id ?: "null") }
    }

    @Transactional
    fun updateDocumentsStatuses(ids: List<IntId<PVDocument>>, status: PVDocumentStatus): List<IntId<PVDocument>> {
        if (ids.isEmpty()) return emptyList()
        val sql =
            """
                update projektivelho.document
                set status = :status::projektivelho.document_status
                where id in (:ids)
                returning id
            """
                .trimIndent()
        val params = mapOf("ids" to ids.map { it.intValue }, "status" to status.name)
        jdbcTemplate.setUser()
        return jdbcTemplate
            .query<IntId<PVDocument>>(sql, params) { rs, _ -> rs.getIntId("id") }
            .also { _ -> logger.daoAccess(UPDATE, PVDocument::class, ids) }
    }

    @Transactional
    fun insertRejection(documentRowVersion: RowVersion<PVDocument>, reason: String): IntId<PVDocumentRejection> {
        val sql =
            """
                insert into projektivelho.document_rejection(document_id, document_version, reason)
                values (:id, :version, :reason)
                returning id
            """
                .trimIndent()
        val params =
            mapOf("id" to documentRowVersion.id.intValue, "version" to documentRowVersion.version, "reason" to reason)
        jdbcTemplate.setUser()
        return getOne<PVDocument, IntId<PVDocumentRejection>>(
                documentRowVersion.id,
                jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId("id") },
            )
            .also { id -> logger.daoAccess(INSERT, PVDocumentRejection::class, id) }
    }

    fun getRejection(documentRowVersion: RowVersion<PVDocument>): PVDocumentRejection {
        logger.daoAccess(FETCH, PVDocumentRejection::class)
        val sql =
            """
                select id, document_id, document_version, reason 
                from projektivelho.document_rejection
                where document_id = :document_id and document_version = :document_version
            """
                .trimIndent()
        val params =
            mapOf("document_id" to documentRowVersion.id.intValue, "document_version" to documentRowVersion.version)
        jdbcTemplate.setUser()
        return getOne<PVDocument, PVDocumentRejection>(
            documentRowVersion.id,
            jdbcTemplate.query(sql, params) { rs, _ ->
                PVDocumentRejection(
                    id = rs.getIntId("id"),
                    documentVersion = rs.getRowVersion("document_id", "document_version"),
                    reason = LocalizationKey.of(rs.getString("reason")),
                )
            },
        )
    }

    fun getDocumentHeader(id: IntId<PVDocument>) = getDocumentHeaders(id = id).single()

    fun getDocumentHeaders(status: PVDocumentStatus? = null, id: IntId<PVDocument>? = null): List<PVDocumentHeader> {
        val sql =
            """
                select 
                  document.id,
                  document.oid,
                  document.filename,
                  document.version,
                  document.description,
                  document.change_time, 
                  document.status,
                  document.project_oid,
                  project.name as project_name,
                  project_state.name as project_state,
                  document.project_group_oid,
                  project_group.name as project_group_name,
                  project_group_state.name as project_group_state,
                  document.assignment_oid,
                  assignment.name as assignment_name,
                  assignment_state.name as assignment_state,
                  document_type.name as document_type,
                  material_state.name as material_state,
                  material_group.name as material_group,
                  material_category.name as material_category
                from projektivelho.document
                  left join projektivelho.project on project.oid = document.project_oid
                  left join projektivelho.project_state on project.state_code = project_state.code
                  left join projektivelho.project_group on project_group.oid = document.project_group_oid
                  left join projektivelho.project_state project_group_state on project_group.state_code = project_group_state.code
                  left join projektivelho.assignment on assignment.oid = document.assignment_oid
                  left join projektivelho.project_state assignment_state on assignment.state_code = assignment_state.code
                  left join projektivelho.document_type on document_type.code = document.document_type_code
                  left join projektivelho.material_state on material_state.code = document.material_state_code
                  left join projektivelho.material_group on material_group.code = document.material_group_code
                  left join projektivelho.material_category on material_category.code = document.material_category_code
                where (:status::projektivelho.document_status is null or status = :status::projektivelho.document_status)
                  and (:id::int is null or id = :id)
            """
                .trimIndent()
        val params = mapOf("id" to id?.intValue, "status" to status?.name)
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                PVDocumentHeader(
                    project =
                        rs.getOidOrNull<PVProject>("project_oid")?.let { oid ->
                            PVProject(
                                oid = oid,
                                name = rs.getPVProjectName("project_name"),
                                state = rs.getPVDictionaryName("project_state"),
                            )
                        },
                    projectGroup =
                        rs.getOidOrNull<PVProjectGroup>("project_group_oid")?.let { oid ->
                            PVProjectGroup(
                                oid = oid,
                                name = rs.getPVProjectName("project_group_name"),
                                state = rs.getPVDictionaryName("project_state"),
                            )
                        },
                    assignment =
                        rs.getOidOrNull<PVAssignment>("assignment_oid")?.let { oid ->
                            PVAssignment(
                                oid = oid,
                                name = rs.getPVProjectName("assignment_name"),
                                state = rs.getPVDictionaryName("project_state"),
                            )
                        },
                    document =
                        PVDocument(
                            id = rs.getIntId("id"),
                            oid = rs.getOid("oid"),
                            name = rs.getFileName("filename"),
                            description = rs.getUnsafeStringOrNull("description")?.let(::FreeText),
                            type = rs.getPVDictionaryName("document_type"),
                            state = rs.getPVDictionaryName("material_state"),
                            group = rs.getPVDictionaryName("material_group"),
                            category = rs.getPVDictionaryName("material_category"),
                            modified = rs.getInstant("change_time"),
                            status = rs.getEnum("status"),
                        ),
                )
            }
            .also { results -> logger.daoAccess(FETCH, PVDocument::class, results.map { r -> r.document.id }) }
    }

    fun getDocumentCounts(): PVDocumentCounts {
        val sql =
            """
                select 
                  count(*) filter (where status = 'SUGGESTED') as suggested_count, 
                  count(*) filter (where status = 'REJECTED') as rejected_count
                from projektivelho.document
            """
                .trimIndent()
        return jdbcTemplate
            .query(sql, emptyMap<String, Any>()) { rs, _ ->
                PVDocumentCounts(suggested = rs.getInt("suggested_count"), rejected = rs.getInt("rejected_count"))
            }
            .single()
    }

    fun fetchDocumentChangeTime(): Instant = fetchLatestChangeTime(DbTable.PROJEKTIVELHO_DOCUMENT)

    fun getFileContent(id: IntId<PVDocument>): InfraModelFile? {
        logger.daoAccess(FETCH, InfraModelFile::class, id)
        val sql =
            """
                select 
                  document.filename,
                  xmlserialize(document document_content.content as varchar) as file_content
                from projektivelho.document
                  inner join projektivelho.document_content on document.id = document_content.document_id
                where document.id = :id
            """
                .trimIndent()
        val params = mapOf("id" to id.intValue)
        return getOptional(
            id,
            jdbcTemplate.query(sql, params) { rs, _ ->
                InfraModelFile(name = rs.getFileName("filename"), content = rs.getString("file_content"))
            },
        )
    }

    @Transactional
    fun upsertDictionary(type: PVDictionaryType, entries: List<PVApiDictionaryEntry>) {
        val tableName = tableName(type)
        val sql =
            """
                insert into $tableName(code, name) 
                  values (:code, :name) 
                  on conflict (code) do update set name = :name where $tableName.name <> :name
            """
                .trimIndent()
        val params = entries.map { entry -> mapOf("code" to entry.code, "name" to entry.name) }.toTypedArray()
        jdbcTemplate.setUser()
        logger.daoAccess(UPSERT, PVApiDictionaryEntry::class, entries.map(PVApiDictionaryEntry::code))
        jdbcTemplate.batchUpdate(sql, params)
    }

    fun fetchDictionary(type: PVDictionaryType): Map<PVDictionaryCode, PVDictionaryName> {
        val sql = "select code, name from ${tableName(type)}"
        return jdbcTemplate
            .query(sql, mapOf<String, Any>()) { rs, _ ->
                rs.getPVDictionaryCode("code") to PVDictionaryName(rs.getUnsafeString("name"))
            }
            .associate { it }
            .also { _ -> logger.daoAccess(FETCH, PVDictionaryType::class, type) }
    }

    private fun tableName(type: PVDictionaryType) =
        "projektivelho.${
        when (type) {
            DOCUMENT_TYPE -> "document_type"
            MATERIAL_STATE -> "material_state"
            MATERIAL_CATEGORY -> "material_category"
            MATERIAL_GROUP -> "material_group"
            TECHNICS_FIELD -> "technics_field"
            PROJECT_STATE -> "project_state"
        }
    }"

    private fun clipToLength(clazz: KClass<*>, field: String, maxLength: Int, value: UnsafeString): String =
        value.unsafeValue.let { str ->
            if (str.length > maxLength) {
                logger.warn(
                    "Received unsafe string exceeds max length. It must be clipped to fit in the DB column: " +
                        "class=${clazz.simpleName} field=$field length=${str.length} maxLength=$maxLength value=${formatForLog(str)})"
                )
                str.take(maxLength)
            } else {
                str
            }
        }
}
