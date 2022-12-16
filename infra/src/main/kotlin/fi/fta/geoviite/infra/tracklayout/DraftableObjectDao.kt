package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.linking.PublicationVersion
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.DELETE
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface IDraftableObjectWriter<T : Draftable<T>> {
    fun insert(newItem: T): RowVersion<T>
    fun update(updatedItem: T): RowVersion<T>
    fun updateAndVerifyVersion(previousVersion: RowVersion<T>, updatedItem: T): RowVersion<T>
}

interface IDraftableObjectReader<T : Draftable<T>> {
    fun fetch(version: RowVersion<T>): T

    fun fetchChangeTime(): Instant
    fun fetchChangeTimes(id: IntId<T>): ChangeTimes

    fun fetchAllVersions(): List<RowVersion<T>>
    fun fetchVersions(publicationState: PublishType, includeDeleted: Boolean): List<RowVersion<T>>

    fun fetchPublicationVersions(ids: List<IntId<T>>): List<PublicationVersion<T>>

    fun fetchVersionPair(id: IntId<T>): VersionPair<T>
    fun fetchDraftVersion(id: IntId<T>): RowVersion<T>?
    fun fetchDraftVersionOrThrow(id: IntId<T>): RowVersion<T>
    fun fetchOfficialVersion(id: IntId<T>): RowVersion<T>?
    fun fetchOfficialVersionOrThrow(id: IntId<T>): RowVersion<T>
    fun fetchVersion(id: IntId<T>, publishType: PublishType): RowVersion<T>? =
        when (publishType) {
            OFFICIAL -> fetchOfficialVersion(id)
            DRAFT -> fetchDraftVersion(id)
        }

    fun fetchVersionOrThrow(id: IntId<T>, publishType: PublishType): RowVersion<T> =
        when (publishType) {
            OFFICIAL -> fetchOfficialVersionOrThrow(id)
            DRAFT -> fetchDraftVersionOrThrow(id)
        }

    fun deleteUnpublishedDraft(id: IntId<T>): RowVersion<T>

    fun deleteDrafts(id: IntId<T>? = null): List<Pair<IntId<T>, IntId<T>?>>
}

data class VersionPair<T>(val official: RowVersion<T>?, val draft: RowVersion<T>?)

interface IDraftableObjectDao<T : Draftable<T>> : IDraftableObjectReader<T>, IDraftableObjectWriter<T>

@Transactional(readOnly = true)
abstract class DraftableDaoBase<T : Draftable<T>>(
jdbcTemplateParam: NamedParameterJdbcTemplate?,
    private val table: DbTable,
): DaoBase(jdbcTemplateParam), IDraftableObjectDao<T> {

    @Transactional
    override fun updateAndVerifyVersion(previousVersion: RowVersion<T>, updatedItem: T): RowVersion<T> =
        update(updatedItem).also { updatedVersion ->
            require (updatedVersion.id == previousVersion.id) {
                "Updated the wrong object: previous=$previousVersion updated=$updatedVersion"
            }
            if (updatedVersion.version != previousVersion.version+1) {
                // We could do optimistic locking here by throwing
                logger.warn("Updated version isn't the next one: a concurrent change may have been overwritten: " +
                        "previous=$previousVersion updated=$updatedVersion")
            }
        }

    override fun fetchPublicationVersions(ids: List<IntId<T>>): List<PublicationVersion<T>> {
        // Empty lists don't play nice in the SQL, but the result would be empty anyhow
        if (ids.isEmpty()) return listOf()
        val sql = """
            select 
              coalesce(${table.draftLink}, id) as official_id,
              id as row_id, 
              version as row_version
            from ${table.fullName} 
            where official_id in (:ids)
              and draft = true
        """.trimIndent()
        val params = mapOf(
            "ids" to ids.map { id -> id.intValue },
        )
        return jdbcTemplate.query<PublicationVersion<T>>(sql, params) { rs, _ -> PublicationVersion(
            rs.getIntId("official_id"),
            rs.getRowVersion("row_id", "row_version"),
        ) }.also { found -> ids.forEach { id ->
            if (found.none { f -> f.officialId == id }) throw NoSuchEntityException(table.name, id)
        } }
    }
    override fun fetchChangeTime(): Instant = fetchLatestChangeTime(table)

    override fun fetchChangeTimes(id: IntId<T>): ChangeTimes {
        val sql = """
            select
              greatest(main_row.change_time, draft_row.change_time) as change_time,
              case when main_row.draft then null else main_row.change_time end as official_change_time,
              case when main_row.draft then main_row.change_time else draft_row.change_time end as draft_change_time,
              version1.change_time as creation_time
            from ${table.fullName} main_row
              left join ${table.fullName} draft_row on draft_row.${table.draftLink} = main_row.id
              inner join ${table.versionTable} version1 on main_row.id = version1.id and version1.version = 1
            where (main_row.${table.draftLink} is null)
              and (main_row.id = :id or draft_row.id = :id)
        """.trimMargin()
        return jdbcTemplate.queryForObject(sql, mapOf("id" to id.intValue)) { rs, _ ->
            ChangeTimes(
                created = rs.getInstant("creation_time"),
                changed = rs.getInstant("change_time"),
                officialChanged = rs.getInstantOrNull("official_change_time"),
                draftChanged = rs.getInstantOrNull("draft_change_time"),
            )
        } ?: throw NoSuchEntityException(table.name, id)
    }

    override fun fetchAllVersions(): List<RowVersion<T>> = fetchRowVersions(table)

    override fun fetchVersionPair(id: IntId<T>): VersionPair<T> {
        val sql = """
            select o.id, o.version, o.draft
            from ${table.fullName} o left join ${table.fullName} d
              on o.${table.draftLink} = d.id or d.${table.draftLink} = o.id
            where o.id = :id or d.id = :id
        """
        val params = mapOf("id" to id.intValue)
        val versions: List<Pair<Boolean, RowVersion<T>>> = jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getBoolean("draft") to rs.getRowVersion("id", "version")
        }
        return VersionPair(
            draft = versions.find { (draft, _) -> draft }?.let { (_, version) -> version },
            official = versions.find { (draft, _) -> !draft }?.let { (_, version) -> version },
        )
    }

    override fun fetchDraftVersion(id: IntId<T>): RowVersion<T>? = fetchDraftRowVersion(id, table)

    override fun fetchOfficialVersion(id: IntId<T>): RowVersion<T>? {
        val pair = fetchVersionPair(id)
        return pair.official
            ?: if (pair.draft != null) null else throw NoSuchEntityException(table.name, id)
    }

    override fun fetchOfficialVersionOrThrow(id: IntId<T>): RowVersion<T> = fetchOfficialRowVersionOrThrow(id, table)

    override fun fetchDraftVersionOrThrow(id: IntId<T>): RowVersion<T> = fetchDraftRowVersionOrThrow(id, table)

    @Transactional
    override fun deleteUnpublishedDraft(id: IntId<T>): RowVersion<T> {
        val sql = """
            delete from ${table.fullName}
            where draft = true
              and id = :id 
              and ${table.draftLink} is null
            returning id, version
        """.trimIndent()
        val params = mapOf("id" to id.intValue)
        logger.daoAccess(DELETE, table.fullName, id)
        jdbcTemplate.setUser()
        return getOne(table.name, id, jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        })
    }

    @Transactional
    override fun deleteDrafts(id: IntId<T>?): List<Pair<IntId<T>, IntId<T>?>> {
        val sql = """
            delete from ${table.fullName}
            where draft = true 
              and (:id::int is null or :id = id or :id = ${table.draftLink})
            returning id, ${table.draftLink} draft_of
        """.trimIndent()
        jdbcTemplate.setUser()
        val deletedRowIds = jdbcTemplate.query(sql, mapOf("id" to id?.intValue)) { rs, _ ->
            rs.getIntId<T>("id") to rs.getIntIdOrNull<T>("draft_of")
        }
        logger.daoAccess(DELETE, table.fullName, deletedRowIds)
        return deletedRowIds
    }

    private fun officialFetchSql(table: DbTable) = """
            select o.id, o.version
            from ${table.fullName} o left join ${table.fullName} d on d.${table.draftLink} = o.id
            where (o.id = :id or d.id = :id) and o.draft = false
        """

    private fun draftFetchSql(table: DbTable) = """
            select o.id, o.version 
            from ${table.fullName} o
            where o.${table.draftLink} = :id 
               or (o.id = :id and not exists(select 1 from ${table.fullName} d where d.${table.draftLink} = :id))
        """

    private fun <T> fetchOfficialRowVersionOrThrow(id: IntId<T>, table: DbTable): RowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH, "fetchOfficialRowVersionOrThrow", "id" to id, "table" to table.fullName)
        return queryRowVersion(officialFetchSql(table), id)
    }

    private fun <T> fetchDraftRowVersionOrThrow(id: IntId<T>, table: DbTable): RowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH, "fetchDraftRowVersion", "id" to id, "table" to table.fullName)
        return queryRowVersion(draftFetchSql(table), id)
    }

    private fun <T> fetchOfficialRowVersion(id: IntId<T>, table: DbTable): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, "fetchOfficialRowVersion", "id" to id, "table" to table.fullName)
        return queryRowVersionOrNull(officialFetchSql(table), id)
    }

    private fun <T> fetchDraftRowVersion(id: IntId<T>, table: DbTable): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, "fetchDraftRowVersion", "id" to id, "table" to table.fullName)
        return queryRowVersionOrNull(draftFetchSql(table), id)
    }
}

inline fun <reified T: Draftable<T>> draftOfId(item: T) = draftOfId(item.id, item.draft)

inline fun <reified T: Draftable<T>> draftOfId(id: DomainId<T>, draft: Draft<T>?): IntId<T>? =
    if (draft != null && draft.draftRowId != id) toDbId(T::class, id)
    else null

inline fun <reified T: Draftable<T>> verifyDraftableInsert(item: T) = verifyDraftableInsert(item.id, item.draft)

inline fun <reified T: Draftable<T>> verifyDraftableInsert(id: DomainId<T>, draft: Draft<T>?) {
    require(id !is IntId || draft != null) { "Cannot insert existing official ${T::class.simpleName} as new" }
    require(draft?.draftRowId !is IntId) { "Cannot insert existing draft ${T::class.simpleName} as new" }
}
