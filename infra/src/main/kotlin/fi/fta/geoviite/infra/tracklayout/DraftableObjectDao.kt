package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.DELETE
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant


data class VersionPair<T>(val official: RowVersion<T>?, val draft: RowVersion<T>?) {
    fun getOfficialId() = official?.id ?: draft?.id
}

data class DaoResponse<T>(val id: IntId<T>, val rowVersion: RowVersion<T>)

interface IDraftableObjectWriter<T : Draftable<T>> {
    fun insert(newItem: T): DaoResponse<T>

    fun update(updatedItem: T): DaoResponse<T>

    fun deleteUnpublishedDraft(id: IntId<T>): DaoResponse<T>
    fun deleteDraft(id: IntId<T>): DaoResponse<T>
    fun deleteDrafts(): List<DaoResponse<T>>
}

interface IDraftableObjectReader<T : Draftable<T>> {
    fun fetch(version: RowVersion<T>): T

    fun fetchChangeTime(): Instant
    fun fetchChangeTimes(id: IntId<T>): ChangeTimes

    fun fetchAllVersions(): List<RowVersion<T>>
    fun fetchVersions(publicationState: PublishType, includeDeleted: Boolean): List<RowVersion<T>>

    fun fetchPublicationVersions(ids: List<IntId<T>>): List<ValidationVersion<T>>

    fun draftExists(id: IntId<T>): Boolean
    fun officialExists(id: IntId<T>): Boolean

    fun fetchVersionPair(id: IntId<T>): VersionPair<T>
    fun fetchDraftVersion(id: IntId<T>): RowVersion<T>?
    fun fetchDraftVersionsOrThrow(ids: List<IntId<T>>): List<RowVersion<T>>
    fun fetchDraftVersionOrThrow(id: IntId<T>): RowVersion<T>
    fun fetchOfficialVersion(id: IntId<T>): RowVersion<T>?
    fun fetchOfficialVersionOrThrow(id: IntId<T>): RowVersion<T>
    fun fetchOfficialVersionsOrThrow(ids: List<IntId<T>>): List<RowVersion<T>>
    fun fetchVersion(id: IntId<T>, publishType: PublishType): RowVersion<T>? =
        when (publishType) {
            OFFICIAL -> fetchOfficialVersion(id)
            DRAFT -> fetchDraftVersion(id)
        }
    fun fetchOfficialVersionAtMomentOrThrow(id: IntId<T>, moment: Instant): RowVersion<T>
    fun fetchOfficialVersionAtMoment(id: IntId<T>, moment: Instant): RowVersion<T>?

    fun fetchVersionOrThrow(id: IntId<T>, publishType: PublishType): RowVersion<T> =
        when (publishType) {
            OFFICIAL -> fetchOfficialVersionOrThrow(id)
            DRAFT -> fetchDraftVersionOrThrow(id)
        }
}

interface IDraftableObjectDao<T : Draftable<T>> : IDraftableObjectReader<T>, IDraftableObjectWriter<T>

@Transactional(readOnly = true)
abstract class DraftableDaoBase<T : Draftable<T>>(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    private val table: DbTable,
): DaoBase(jdbcTemplateParam), IDraftableObjectDao<T> {

    override fun fetchPublicationVersions(ids: List<IntId<T>>): List<ValidationVersion<T>> {
        // Empty lists don't play nice in the SQL, but the result would be empty anyhow
        if (ids.isEmpty()) return listOf()
        val distinctIds = ids.distinct()
        if (distinctIds.size != ids.size) logger.warn(
            "Requested publication versions with duplicate ids: duplicated=${ids.size-distinctIds.size} requested=$ids"
        )
        val sql = """
            select
              coalesce(${table.draftLink}, id) as official_id,
              id as row_id,
              version as row_version
            from ${table.fullName}
            where coalesce(${table.draftLink}, id) in (:ids)
              and draft = true
        """.trimIndent()
        val params = mapOf("ids" to distinctIds.map { id -> id.intValue })
        return jdbcTemplate.query<ValidationVersion<T>>(sql, params) { rs, _ -> ValidationVersion(
            rs.getIntId("official_id"),
            rs.getRowVersion("row_id", "row_version"),
        ) }.also { found -> distinctIds.forEach { id ->
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

    override fun draftExists(id: IntId<T>): Boolean = fetchVersionPair(id).draft != null
    override fun officialExists(id: IntId<T>): Boolean = fetchVersionPair(id).official != null

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

    override fun fetchOfficialVersionOrThrow(id: IntId<T>): RowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return queryRowVersion(officialFetchSql(table, FetchType.SINGLE), id)
    }

    override fun fetchDraftVersionOrThrow(id: IntId<T>): RowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH,  table.name, id)
        return queryRowVersion(draftFetchSql(table, FetchType.SINGLE), id)
    }

    private fun <T> fetchOfficialRowVersion(id: IntId<T>, table: DbTable): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return queryRowVersionOrNull(officialFetchSql(table, FetchType.SINGLE), id)
    }

    override fun fetchOfficialVersionsOrThrow(ids: List<IntId<T>>): List<RowVersion<T>> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.versionTable, ids)
        val distinctIds = ids.distinct()
        if (distinctIds.size != ids.size) logger.warn(
            "Requested official versions with duplicate ids: duplicated=${ids.size-distinctIds.size} requested=$ids"
        )
        val params = mapOf("ids" to distinctIds.map { it.intValue })
        val versions: List<RowVersion<T>> =
            if (ids.isEmpty()) emptyList()
            else jdbcTemplate.query(officialFetchSql(table, FetchType.MULTI), params) { rs, _ ->
                rs.getRowVersion("id", "version")
            }
        require(versions.size == distinctIds.size) { "RowVersions not found for some ids" }
        return versions
    }

    private fun <T> fetchDraftRowVersion(id: IntId<T>, table: DbTable): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return queryRowVersionOrNull(draftFetchSql(table, FetchType.SINGLE), id)
    }

    override fun fetchDraftVersionsOrThrow(ids: List<IntId<T>>): List<RowVersion<T>> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, ids)
        val distinctIds = ids.distinct()
        if (distinctIds.size != ids.size) logger.warn(
            "Requested draft versions with duplicate ids: duplicated=${ids.size-distinctIds.size} requested=$ids"
        )
        val params = mapOf("ids" to ids.map { it.intValue })
        val versions: List<RowVersion<T>> =
            if (ids.isEmpty()) emptyList()
            else jdbcTemplate.query(draftFetchSql(table, FetchType.MULTI), params) { rs, _ ->
                rs.getRowVersion("id", "version")
            }
        require(versions.size == distinctIds.size) { "RowVersions not found for some ids: ids=$ids versions=$versions" }
        return versions
    }

    override fun fetchOfficialVersionAtMomentOrThrow(id: IntId<T>, moment: Instant): RowVersion<T> =
        fetchOfficialVersionAtMoment(id, moment) ?: throw NoSuchEntityException(table.name, id)

    override fun fetchOfficialVersionAtMoment(id: IntId<T>, moment: Instant): RowVersion<T>? {
        //language=SQL
        val sql = """
            select
              case when version.deleted then null else version.id end as id,
              case when version.deleted then null else version.version end as version
            from ${table.versionTable} version
            where
              version.id = :id
              and version.change_time <= :moment
              and not version.draft
            order by version.change_time desc
            limit 1
        """.trimIndent()
        val params = mapOf(
            "id" to id.intValue,
            "moment" to Timestamp.from(moment),
        )
        logger.daoAccess(AccessType.VERSION_FETCH, LocationTrack::class, id)
        return jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        }
    }

    @Transactional
    override fun deleteUnpublishedDraft(id: IntId<T>): DaoResponse<T> {
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
        val response: List<DaoResponse<T>> = jdbcTemplate.query(sql, params) { rs, _ ->
            // Draft-only (there is no official row) -> id is also the official id
            rs.getDaoResponse("id", "id", "version")
         }
        return getOne(table.name, id, response)
    }

    @Transactional
    override fun deleteDraft(id: IntId<T>): DaoResponse<T> = deleteDraftsInternal(id).let { r ->
        if (r.size > 1) throw IllegalStateException("Multiple rows deleted with one ID: $id")
        else if (r.isEmpty()) throw NoSuchEntityException(table.name, id)
        else r.first()
    }

    @Transactional
    override fun deleteDrafts(): List<DaoResponse<T>> = deleteDraftsInternal()

    private fun deleteDraftsInternal(id: IntId<T>? = null): List<DaoResponse<T>> {
        val sql = """
            delete from ${table.fullName}
            where draft = true 
              and (:id::int is null or :id = id or :id = ${table.draftLink})
            returning 
              coalesce(${table.draftLink}, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        jdbcTemplate.setUser()
        return jdbcTemplate.query<DaoResponse<T>>(sql, mapOf("id" to id?.intValue)) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        }.also { deleted -> logger.daoAccess(DELETE, table.fullName, deleted) }
    }

    private fun officialFetchSql(table: DbTable, fetchType: FetchType) = """
            select o.id, o.version
            from ${table.fullName} o left join ${table.fullName} d on d.${table.draftLink} = o.id
            where (o.id ${idOrIdsEqualSqlFragment(fetchType)} or d.id ${idOrIdsEqualSqlFragment(fetchType)}) and o.draft = false
        """

    private fun draftFetchSql(table: DbTable, fetchType: FetchType) = """
            select o.id, o.version 
            from ${table.fullName} o
            where o.${table.draftLink} ${idOrIdsEqualSqlFragment(fetchType)} 
               or (o.id ${idOrIdsEqualSqlFragment(fetchType)} 
                  and not exists(select 1 from ${table.fullName} d where d.${table.draftLink} = o.id))
        """

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
