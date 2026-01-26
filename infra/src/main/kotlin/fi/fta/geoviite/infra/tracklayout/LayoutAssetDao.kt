package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.DELETE
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.FetchType
import fi.fta.geoviite.infra.util.FetchType.MULTI
import fi.fta.geoviite.infra.util.FetchType.SINGLE
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.idOrIdsEqualSqlFragment
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.requireOne
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional

// The Kotlin thing to do would be to just use Unit, but for some reason, the overriden functions returning Unit end up
// giving null to the caller. Not sure why, but perhaps due to reflection shenanigans done by Spring.
class NoParams private constructor() {
    companion object {
        val instance = NoParams()
    }
}

interface LayoutAssetWriter<T : LayoutAsset<T>, SaveParams> {
    fun createId(): IntId<T>

    fun save(item: T, params: SaveParams): LayoutRowVersion<T>

    fun getBaseSaveParams(rowVersion: LayoutRowVersion<T>): SaveParams

    fun deleteRow(rowId: LayoutRowId<T>): LayoutRowVersion<T>

    fun deleteDraft(branch: LayoutBranch, id: IntId<T>): LayoutRowVersion<T>

    fun deleteDrafts(branch: LayoutBranch): List<LayoutRowVersion<T>>
}

interface LayoutAssetReader<T : LayoutAsset<T>> {
    fun fetch(version: LayoutRowVersion<T>): T

    fun fetchMany(versions: Collection<LayoutRowVersion<T>>): List<T>

    fun fetchManyByVersion(versions: Collection<LayoutRowVersion<T>>): Map<LayoutRowVersion<T>, T>

    fun fetchChangeTime(): Instant

    fun fetchLayoutAssetChangeInfo(layoutContext: LayoutContext, id: IntId<T>): LayoutAssetChangeInfo?

    fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean): List<LayoutRowVersion<T>>

    fun fetchCandidateVersions(candidateContext: LayoutContext): List<LayoutRowVersion<T>>

    fun fetchCandidateVersions(candidateContext: LayoutContext, ids: List<IntId<T>>): List<LayoutRowVersion<T>>

    fun fetchVersion(layoutContext: LayoutContext, id: IntId<T>): LayoutRowVersion<T>?

    fun fetchVersionOrThrow(layoutContext: LayoutContext, id: IntId<T>): LayoutRowVersion<T>

    fun fetchVersions(layoutContext: LayoutContext, ids: List<IntId<T>>): List<LayoutRowVersion<T>>

    fun fetchOfficialVersionAtMomentOrThrow(branch: LayoutBranch, id: IntId<T>, moment: Instant): LayoutRowVersion<T>

    fun fetchOfficialVersionAtMoment(branch: LayoutBranch, id: IntId<T>, moment: Instant): LayoutRowVersion<T>? =
        fetchManyOfficialVersionsAtMoment(branch, listOf(id), moment).firstOrNull()

    fun fetchOfficialVersionComparison(
        branch: LayoutBranch,
        id: IntId<T>,
        from: Instant,
        to: Instant,
    ): VersionComparison<T> {
        return VersionComparison(
            fromVersion = fetchOfficialVersionAtMoment(branch, id, from),
            toVersion = fetchOfficialVersionAtMoment(branch, id, to),
        )
    }

    fun fetchManyOfficialVersionsAtMoment(
        branch: LayoutBranch,
        ids: List<IntId<T>>?,
        moment: Instant,
    ): List<LayoutRowVersion<T>>

    @Transactional(readOnly = true)
    fun get(context: LayoutContext, id: IntId<T>): T? = fetchVersion(context, id)?.let(::fetch)

    @Transactional(readOnly = true)
    fun getOrThrow(context: LayoutContext, id: IntId<T>): T = fetch(fetchVersionOrThrow(context, id))

    @Transactional(readOnly = true)
    fun getOfficialAtMoment(branch: LayoutBranch, id: IntId<T>, moment: Instant): T? =
        fetchOfficialVersionAtMoment(branch, id, moment)?.let(::fetch)

    @Transactional(readOnly = true)
    fun getManyOfficialAtMoment(branch: LayoutBranch, ids: List<IntId<T>>, moment: Instant): List<T> =
        fetchMany(fetchManyOfficialVersionsAtMoment(branch, ids, moment))

    @Transactional(readOnly = true)
    fun getMany(context: LayoutContext, ids: List<IntId<T>>): List<T> = fetchMany(fetchVersions(context, ids))

    @Transactional(readOnly = true)
    fun list(context: LayoutContext, includeDeleted: Boolean): List<T> =
        fetchMany(fetchVersions(context, includeDeleted))

    @Transactional(readOnly = true)
    fun listOfficialAtMoment(branch: LayoutBranch, moment: Instant): List<T> =
        fetchMany(fetchManyOfficialVersionsAtMoment(branch, ids = null, moment))
}

interface ILayoutAssetDao<T : LayoutAsset<T>, SaveParams> : LayoutAssetReader<T>, LayoutAssetWriter<T, SaveParams>

abstract class LayoutAssetDao<T : LayoutAsset<T>, SaveParams>(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    open val table: LayoutAssetTable,
    val cacheEnabled: Boolean,
    cacheSize: Long,
) : DaoBase(jdbcTemplateParam), ILayoutAssetDao<T, SaveParams> {

    protected val cache: Cache<LayoutRowVersion<T>, T> =
        Caffeine.newBuilder().maximumSize(cacheSize).expireAfterAccess(layoutCacheDuration).build()

    override fun fetch(version: LayoutRowVersion<T>): T =
        if (cacheEnabled) {
            cache.get(version, ::fetchInternal)
        } else {
            fetchInternal(version)
        }

    override fun fetchMany(versions: Collection<LayoutRowVersion<T>>): List<T> =
        fetchManyByVersion(versions).let { fetched -> versions.mapNotNull(fetched::get) }

    override fun fetchManyByVersion(versions: Collection<LayoutRowVersion<T>>): Map<LayoutRowVersion<T>, T> =
        if (cacheEnabled) {
            cache.getAll(versions) { nonCached -> fetchManyInternal(nonCached) }
        } else {
            fetchManyInternal(versions)
        }

    fun fetchInternal(version: LayoutRowVersion<T>): T =
        fetchManyInternal(listOf(version))[version]
            ?: throw NoSuchEntityException(table.versionTable, version.toString())

    protected abstract fun fetchManyInternal(versions: Collection<LayoutRowVersion<T>>): Map<LayoutRowVersion<T>, T>

    abstract fun preloadCache(): Int

    private val allCandidateVersionsSql =
        """
            select id, version
            from ${table.fullName}
            where draft = (:publication_state::layout.publication_state = 'DRAFT')
              and design_id is not distinct from :design_id
        """
            .trimIndent()

    private val candidateVersionsSql =
        """
            select id, version
            from ${table.fullName}
            where id in (:ids)
              and draft = (:publication_state::layout.publication_state = 'DRAFT')
              and design_id is not distinct from :design_id
        """
            .trimIndent()

    override fun fetchCandidateVersions(candidateContext: LayoutContext): List<LayoutRowVersion<T>> {
        return jdbcTemplate.query<LayoutRowVersion<T>>(
            allCandidateVersionsSql,
            mapOf(
                "publication_state" to candidateContext.state.name,
                "design_id" to candidateContext.branch.designId?.intValue,
            ),
        ) { rs, _ ->
            LayoutRowVersion(rs.getIntId("id"), candidateContext, rs.getInt("version"))
        }
    }

    override fun fetchCandidateVersions(
        candidateContext: LayoutContext,
        ids: List<IntId<T>>,
    ): List<LayoutRowVersion<T>> {
        // Empty lists don't play nice in the SQL, but the result would be empty anyhow
        if (ids.isEmpty()) return listOf()
        val distinctIds = ids.distinct()
        if (distinctIds.size != ids.size) {
            logger.warn(
                "Requested publication versions with duplicate ids: duplicated=${ids.size - distinctIds.size} requested=$ids"
            )
        }
        val params =
            mapOf(
                "ids" to distinctIds.map { id -> id.intValue },
                "publication_state" to candidateContext.state.name,
                "design_id" to candidateContext.branch.designId?.intValue,
            )
        return jdbcTemplate
            .query<LayoutRowVersion<T>>(candidateVersionsSql, params) { rs, _ ->
                LayoutRowVersion(rs.getIntId("id"), candidateContext, rs.getInt("version"))
            }
            .also { found ->
                distinctIds.forEach { id ->
                    if (found.none { f -> f.id == id }) throw NoSuchEntityException(table.name, id)
                }
            }
    }

    override fun fetchChangeTime(): Instant = fetchLatestChangeTime(table.dbTable)

    private val layoutAssetChangeInfoSql =
        """
          with newest_draft as (
            select 
              case when deleted then null else change_time end as change_time,
              deleted
            from ${table.versionTable} 
            where id = :id and draft = true and design_id is not distinct from :design_id
            order by change_time desc limit 1
          ),
          newest_official as (
            select 
              change_time
            from ${table.versionTable} 
            where id = :id and draft = false and design_id is not distinct from :design_id
            order by change_time desc limit 1
          )
          select 
            (select min(change_time) from ${table.versionTable} where id = :id and version = 1) as creation_time,
            newest_official.change_time as official_change_time, 
            newest_draft.change_time as draft_change_time, 
            newest_draft.deleted as draft_deleted 
          from (select) table_dee left join newest_draft on (true) left join newest_official on (true)
        """
            .trimIndent()

    override fun fetchLayoutAssetChangeInfo(layoutContext: LayoutContext, id: IntId<T>): LayoutAssetChangeInfo? {
        return jdbcTemplate
            .query(
                layoutAssetChangeInfoSql,
                mapOf("id" to id.intValue, "design_id" to layoutContext.branch.designId?.intValue),
            ) { rs, _ ->
                val draftDeleted = rs.getBoolean("draft_deleted")
                if (layoutContext.state == OFFICIAL) {
                    LayoutAssetChangeInfo(
                        created = rs.getInstant("creation_time"),
                        changed = rs.getInstantOrNull("official_change_time"),
                    )
                } else {
                    LayoutAssetChangeInfo(
                        created = rs.getInstant("creation_time"),
                        changed =
                            if (draftDeleted) rs.getInstantOrNull("official_change_time")
                            else rs.getInstantOrNull("draft_change_time"),
                    )
                }
            }
            .firstOrNull()
    }

    private val singleLayoutContextVersionSql = fetchContextVersionSql(table, SINGLE)
    private val multiLayoutContextVersionSql = fetchContextVersionSql(table, MULTI)

    override fun fetchVersion(layoutContext: LayoutContext, id: IntId<T>): LayoutRowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        val params =
            mapOf(
                "id" to id.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )
        return jdbcTemplate.queryOptional(singleLayoutContextVersionSql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    override fun fetchVersionOrThrow(layoutContext: LayoutContext, id: IntId<T>): LayoutRowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        val params =
            mapOf(
                "id" to id.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )
        return jdbcTemplate.queryOne(singleLayoutContextVersionSql, params, id.toString()) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    override fun fetchVersions(layoutContext: LayoutContext, ids: List<IntId<T>>): List<LayoutRowVersion<T>> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, ids)
        return if (ids.isEmpty()) {
            emptyList()
        } else {
            val params =
                mapOf(
                    "ids" to ids.distinct().map { it.intValue },
                    "publication_state" to layoutContext.state.name,
                    "design_id" to layoutContext.branch.designId?.intValue,
                )
            val versions =
                jdbcTemplate
                    .query(multiLayoutContextVersionSql, params) { rs, _ ->
                        rs.getLayoutRowVersion<T>("id", "design_id", "draft", "version")
                    }
                    .associateBy { it.id }
            ids.mapNotNull(versions::get)
        }
    }

    override fun fetchOfficialVersionAtMomentOrThrow(
        branch: LayoutBranch,
        id: IntId<T>,
        moment: Instant,
    ): LayoutRowVersion<T> =
        fetchOfficialVersionAtMoment(branch, id, moment) ?: throw NoSuchEntityException(table.name, id)

    // language=SQL
    private val officialVersionsAtMomentSql =
        """
          select distinct on (id) id, design_id, false as draft, version
          from (
            select distinct on (id, design_id)
              id,
              design_id,
              design_id is not null as is_design,
              deleted,
              version
              from ${table.versionTable}
            where (:ids::int[] is null or id = any(array[:ids]::int[]))
              and not draft
              and (design_id is null or design_id = :design_id)
              and change_time <= :moment
              order by id, design_id, change_time desc
            ) tn
          where not deleted
          order by id, is_design desc
        """
            .trimIndent()

    override fun createId(): IntId<T> {
        val sql = "insert into ${table.idTable} default values returning id"
        return jdbcTemplate.queryOne(sql) { rs, _ -> rs.getIntId("id") }
    }

    override fun fetchManyOfficialVersionsAtMoment(
        branch: LayoutBranch,
        ids: List<IntId<T>>?,
        moment: Instant,
    ): List<LayoutRowVersion<T>> =
        if (ids != null && ids.isEmpty()) {
            emptyList()
        } else {
            val params =
                mapOf(
                    "design_id" to branch.designId?.intValue,
                    "ids" to ids?.map { id -> id.intValue }?.toTypedArray(),
                    "moment" to Timestamp.from(moment),
                )
            jdbcTemplate.query(officialVersionsAtMomentSql, params) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            }
        }

    @Transactional
    override fun deleteRow(rowId: LayoutRowId<T>): LayoutRowVersion<T> {
        val sql =
            """
                delete from ${table.fullName}
                where id = :id
                  and (draft = true or design_id is not null) -- Don't allow deleting main-official rows
                  and layout_context_id = :layout_context_id
                returning id, design_id, draft, version
            """
                .trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("id" to rowId.id.intValue, "layout_context_id" to rowId.context.toSqlString())
        return jdbcTemplate
            .query<LayoutRowVersion<T>>(sql, params) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            }
            .singleOrNull()
            .let { deleted ->
                if (deleted == null)
                    error(
                        "No rows were deleted (did you try to delete a main-official row?): type=${table.name} id=${rowId.id} context=${rowId.context}"
                    )
                logger.daoAccess(DELETE, table.fullName, deleted)
                deleted
            }
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<T>): LayoutRowVersion<T> =
        deleteDraftsInternal(branch, id).let { r ->
            if (r.size > 1) {
                error("Multiple rows deleted with one ID: type=${table.name} branch=$branch id=$id")
            } else if (r.isEmpty()) {
                throw DeletingFailureException(
                    "Trying to delete a non-existing draft object: type=${table.name} branch=$branch id=$id"
                )
            } else {
                r.first()
            }
        }

    @Transactional
    override fun deleteDrafts(branch: LayoutBranch): List<LayoutRowVersion<T>> = deleteDraftsInternal(branch)

    private fun deleteDraftsInternal(branch: LayoutBranch, id: IntId<T>? = null): List<LayoutRowVersion<T>> {
        val sql =
            """
                delete from ${table.fullName}
                where draft = true 
                  and (:id::int is null or :id = id)
                  and design_id is not distinct from :design_id
                returning 
                  id, version
            """
                .trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("id" to id?.intValue, "design_id" to branch.designId?.intValue)
        val deletedVersions =
            jdbcTemplate
                .query<LayoutRowVersion<T>>(sql, params) { rs, _ ->
                    LayoutRowVersion(rs.getIntId("id"), branch.draft, rs.getInt("version"))
                }
                .also { deleted -> logger.daoAccess(DELETE, table.fullName, deleted) }
        id?.let(::deleteVersionIfOrphaned)
        return deletedVersions
    }

    private fun deleteVersionIfOrphaned(id: IntId<T>) {
        // This deletion can break due to references from elsewhere to the orphaned ID, but if it
        // does, that's the responsibility of whoever left the stale reference in.
        val sql =
            """
            delete from ${table.idTable} ids
            where id = :id
              and not exists (select * from ${table.fullName} t where t.id = ids.id)
              and not exists (select * from ${table.publicationTable} t where t.id = ids.id)
            """
                .trimIndent()
        jdbcTemplate.execute(sql, mapOf("id" to id.intValue)) { it.execute() }
    }

    protected fun <Field> findFieldDuplicates(
        context: LayoutContext,
        items: List<Field>,
        fieldName: String,
        filterSqlFragment: String? = null,
        extractField: (resultSet: ResultSet) -> Field,
    ): Map<Field, List<LayoutRowVersion<T>>> {
        val sql =
            """
                select id, design_id, draft, version, $fieldName
                from ${table.fullLayoutContextFunction}(:publication_state::layout.publication_state, :design_id)
                where $fieldName = any(:items) and ${filterSqlFragment ?: "true"}
            """
                .trimIndent()
        val params =
            mapOf(
                "items" to items.map { it.toString() }.toTypedArray(),
                "publication_state" to context.state.name,
                "design_id" to context.branch.designId?.intValue,
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                extractField(rs) to rs.getLayoutRowVersion<T>("id", "design_id", "draft", "version")
            }
            .groupBy({ it.first }, { it.second })
            .let { found ->
                logger.daoAccess(AccessType.VERSION_FETCH, "${table.assetClassName} $fieldName duplicates", found.keys)
                items.associateWith { item -> found.getOrDefault(item, listOf()) }
            }
    }
}

private fun fetchContextVersionSql(table: LayoutAssetTable, fetchType: FetchType) =
    // language=SQL
    """
        select id, design_id, draft, version
        from ${table.fullLayoutContextFunction}(:publication_state::layout.publication_state, :design_id::int)
        where id ${idOrIdsEqualSqlFragment(fetchType)}
    """
        .trimIndent()

fun <T : LayoutAsset<T>> verifyObjectExists(item: T) = verifyObjectExists(item.contextData)

fun <T : LayoutAsset<T>> verifyObjectExists(contextData: LayoutContextData<T>) {
    require(contextData.dataType == DataType.STORED) { "Cannot update TEMP row: context=$contextData" }
    require(contextData.version != null) { "DB row should have DB ID: context=$contextData" }
}

inline fun <reified T : LayoutAsset<T>, reified S> getOne(rowVersion: LayoutRowVersion<T>, result: List<S>) =
    requireOne(T::class, rowVersion, result)

data class VersionComparison<T : LayoutAsset<T>>(
    val fromVersion: LayoutRowVersion<T>?,
    val toVersion: LayoutRowVersion<T>?,
) {
    init {
        if (fromVersion != null) {
            checkNotNull(toVersion) {
                "It should not be possible for the fromVersion to be non-null, while the toVersion is null."
            }
        }
    }

    fun areDifferent(): Boolean {
        return fromVersion != toVersion
    }
}
