package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.DELETE
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.FetchType.MULTI
import fi.fta.geoviite.infra.util.FetchType.SINGLE
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Propagation
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

    fun deleteDraft(id: IntId<T>): DaoResponse<T>
    fun deleteDrafts(): List<DaoResponse<T>>
}

@Transactional(readOnly = true)
interface IDraftableObjectReader<T : Draftable<T>> {
    fun fetch(version: RowVersion<T>): T

    fun fetchChangeTime(): Instant
    fun fetchDraftableChangeInfo(id: IntId<T>): DraftableChangeInfo

    fun fetchAllVersions(): List<RowVersion<T>>
    fun fetchVersions(publicationState: PublishType, includeDeleted: Boolean): List<RowVersion<T>>

    fun fetchPublicationVersions(ids: List<IntId<T>>): List<ValidationVersion<T>>

    fun draftExists(id: IntId<T>): Boolean
    fun officialExists(id: IntId<T>): Boolean

    fun fetchVersionPair(id: IntId<T>): VersionPair<T>
    fun fetchDraftVersion(id: IntId<T>): RowVersion<T>?
    fun fetchDraftVersions(ids: List<IntId<T>>): List<RowVersion<T>>
    fun fetchDraftVersionOrThrow(id: IntId<T>): RowVersion<T>
    fun fetchOfficialVersion(id: IntId<T>): RowVersion<T>?
    fun fetchOfficialVersionOrThrow(id: IntId<T>): RowVersion<T>
    fun fetchOfficialVersions(ids: List<IntId<T>>): List<RowVersion<T>>
    fun fetchVersion(id: IntId<T>, publishType: PublishType): RowVersion<T>? = when (publishType) {
        OFFICIAL -> fetchOfficialVersion(id)
        DRAFT -> fetchDraftVersion(id)
    }

    fun fetchOfficialVersionAtMomentOrThrow(id: IntId<T>, moment: Instant): RowVersion<T>
    fun fetchOfficialVersionAtMoment(id: IntId<T>, moment: Instant): RowVersion<T>?

    fun fetchVersionOrThrow(id: IntId<T>, publishType: PublishType): RowVersion<T> = when (publishType) {
        OFFICIAL -> fetchOfficialVersionOrThrow(id)
        DRAFT -> fetchDraftVersionOrThrow(id)
    }

    fun get(publishType: PublishType, id: IntId<T>): T? = when (publishType) {
        DRAFT -> fetchDraftVersion(id)?.let(::fetch)
        OFFICIAL -> fetchOfficialVersion(id)?.let(::fetch)
    }

    fun getOrThrow(publishType: PublishType, id: IntId<T>): T = when (publishType) {
        DRAFT -> fetch(fetchDraftVersionOrThrow(id))
        OFFICIAL -> fetch(fetchOfficialVersionOrThrow(id))
    }

    fun getOfficialAtMoment(id: IntId<T>, moment: Instant): T? =
        fetchOfficialVersionAtMoment(id, moment)?.let(::fetch)

    fun getMany(publishType: PublishType, ids: List<IntId<T>>): List<T> = when (publishType) {
        DRAFT -> fetchDraftVersions(ids)
        OFFICIAL -> fetchOfficialVersions(ids)
    }.map(::fetch)

    fun list(publishType: PublishType, includeDeleted: Boolean): List<T> =
        fetchVersions(publishType, includeDeleted).map(::fetch)
}

interface IDraftableObjectDao<T : Draftable<T>> : IDraftableObjectReader<T>, IDraftableObjectWriter<T>

@Transactional(readOnly = true)
abstract class DraftableDaoBase<T : Draftable<T>>(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val table: DbTable,
    val cacheEnabled: Boolean,
    cacheSize: Long,
) : DaoBase(jdbcTemplateParam), IDraftableObjectDao<T> {

    protected val cache: Cache<RowVersion<T>, T> =
        Caffeine.newBuilder().maximumSize(cacheSize).expireAfterAccess(layoutCacheDuration).build()

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun fetch(version: RowVersion<T>): T =
        if (cacheEnabled) cache.get(version, ::fetchInternal)
        else fetchInternal(version)

    protected abstract fun fetchInternal(version: RowVersion<T>): T

    abstract fun preloadCache()

    private val publicationVersionsSql = """
        select
          coalesce(${table.draftLink}, id) as official_id,
          id as row_id,
          version as row_version
        from ${table.fullName}
        where coalesce(${table.draftLink}, id) in (:ids)
          and draft = true
    """.trimIndent()

    override fun fetchPublicationVersions(ids: List<IntId<T>>): List<ValidationVersion<T>> {
        // Empty lists don't play nice in the SQL, but the result would be empty anyhow
        if (ids.isEmpty()) return listOf()
        val distinctIds = ids.distinct()
        if (distinctIds.size != ids.size) logger.warn(
            "Requested publication versions with duplicate ids: duplicated=${ids.size - distinctIds.size} requested=$ids"
        )
        val params = mapOf("ids" to distinctIds.map { id -> id.intValue })
        return jdbcTemplate.query<ValidationVersion<T>>(publicationVersionsSql, params) { rs, _ ->
            ValidationVersion(
                rs.getIntId("official_id"),
                rs.getRowVersion("row_id", "row_version"),
            )
        }.also { found ->
            distinctIds.forEach { id ->
                if (found.none { f -> f.officialId == id }) throw NoSuchEntityException(table.name, id)
            }
        }
    }

    override fun fetchChangeTime(): Instant = fetchLatestChangeTime(table)

    private val draftableChangeInfoSql = """
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
    """.trimIndent()

    override fun fetchDraftableChangeInfo(id: IntId<T>): DraftableChangeInfo {
        return jdbcTemplate.queryForObject(draftableChangeInfoSql, mapOf("id" to id.intValue)) { rs, _ ->
            DraftableChangeInfo(
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

    private val versionPairSql = """
        with rs as
          (
            select
              direct.id as direct_id,
              direct.version as direct_version,
              direct.draft as direct_draft,
              lookup_official.id as lookup_id,
              lookup_official.version as lookup_version
              from (
                (select * from ${table.fullName} where id = :id)
                union all
                (select * from ${table.fullName} where ${table.draftLink} = :id)
              ) direct
                left join ${table.fullName} lookup_official on direct.${table.draftLink} = lookup_official.id
          )
        select direct_id as id, direct_version as version, direct_draft as draft
          from rs
        union
        select lookup_id, lookup_version, false
          from rs
          where lookup_id is not null;
    """.trimIndent()

    override fun fetchVersionPair(id: IntId<T>): VersionPair<T> {
        val params = mapOf("id" to id.intValue)
        val versions: List<Pair<Boolean, RowVersion<T>>> = jdbcTemplate.query(versionPairSql, params) { rs, _ ->
            rs.getBoolean("draft") to rs.getRowVersion("id", "version")
        }
        return VersionPair(
            draft = versions.find { (draft, _) -> draft }?.let { (_, version) -> version },
            official = versions.find { (draft, _) -> !draft }?.let { (_, version) -> version },
        )
    }

    private val singleDraftVersionSql = draftFetchSql(table, SINGLE)
    private val multiDraftVersionSql = draftFetchSql(table, MULTI)

    private val singleOfficialVersionSql = officialFetchSql(table, SINGLE)
    private val multiOfficialVersionSql = officialFetchSql(table, MULTI)

    override fun fetchDraftVersion(id: IntId<T>): RowVersion<T>? = fetchDraftRowVersion(id, table)

    override fun fetchOfficialVersion(id: IntId<T>): RowVersion<T>? = fetchOfficialRowVersion(id, table)

    override fun fetchOfficialVersionOrThrow(id: IntId<T>): RowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return queryRowVersion(singleOfficialVersionSql, id)
    }

    private fun fetchOfficialRowVersion(id: IntId<T>, table: DbTable): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return queryRowVersionOrNull(singleOfficialVersionSql, id)
    }

    override fun fetchOfficialVersions(ids: List<IntId<T>>): List<RowVersion<T>> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.versionTable, ids)
        val params = mapOf("ids" to ids.distinct().map { it.intValue })
        val versions: List<RowVersion<T>> =
            if (ids.isEmpty()) emptyList()
            else jdbcTemplate.query(multiOfficialVersionSql, params, ::toRowVersion)
        return versions
    }

    override fun fetchDraftVersionOrThrow(id: IntId<T>): RowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return queryRowVersion(singleDraftVersionSql, id)
    }

    private fun <T> fetchDraftRowVersion(id: IntId<T>, table: DbTable): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return queryRowVersionOrNull(singleDraftVersionSql, id)
    }

    override fun fetchDraftVersions(ids: List<IntId<T>>): List<RowVersion<T>> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, ids)
        val params = mapOf("ids" to ids.distinct().map { it.intValue })
        val versions: List<RowVersion<T>> = if (ids.isEmpty()) emptyList()
        else jdbcTemplate.query(multiDraftVersionSql, params, ::toRowVersion)
        return versions
    }

    override fun fetchOfficialVersionAtMomentOrThrow(id: IntId<T>, moment: Instant): RowVersion<T> =
        fetchOfficialVersionAtMoment(id, moment) ?: throw NoSuchEntityException(table.name, id)

    //language=SQL
    private val officialVersionAtMomentSql = """
        select
          case when v.deleted then null else v.id end as id,
          case when v.deleted then null else v.version end as version
        from ${table.versionTable} v
        where
          v.id = :id
          and v.change_time <= :moment
          and not v.draft
        order by v.change_time desc
        limit 1
    """.trimIndent()

    override fun fetchOfficialVersionAtMoment(id: IntId<T>, moment: Instant): RowVersion<T>? {
        val params = mapOf(
            "id" to id.intValue,
            "moment" to Timestamp.from(moment),
        )
        logger.daoAccess(AccessType.VERSION_FETCH, LocationTrack::class, id)
        return jdbcTemplate.queryOptional(officialVersionAtMomentSql, params, ::toRowVersion)
    }

    @Transactional
    override fun deleteDraft(id: IntId<T>): DaoResponse<T> = deleteDraftsInternal(id).let { r ->
        if (r.size > 1) error { "Multiple rows deleted with one ID: type=${table.name} id=$id" }
        else if (r.isEmpty()) throw DeletingFailureException("Trying to delete a non-existing draft object: type=${table.name} id=$id")
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

inline fun <reified T : Draftable<T>> draftOfId(item: T) = draftOfId(item.id, item.draft)

inline fun <reified T : Draftable<T>> draftOfId(id: DomainId<T>, draft: Draft<T>?): IntId<T>? =
    if (draft != null && draft.draftRowId != id) toDbId(T::class, id)
    else null

inline fun <reified T : Draftable<T>> verifyDraftableInsert(item: T) = verifyDraftableInsert(item.id, item.draft)

inline fun <reified T : Draftable<T>> verifyDraftableInsert(id: DomainId<T>, draft: Draft<T>?) {
    require(id !is IntId || draft != null) { "Cannot insert existing official ${T::class.simpleName} as new" }
    require(draft?.draftRowId !is IntId) { "Cannot insert existing draft ${T::class.simpleName} as new" }
}
