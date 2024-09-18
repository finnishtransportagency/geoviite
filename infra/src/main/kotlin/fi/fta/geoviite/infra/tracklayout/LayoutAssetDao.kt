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
import fi.fta.geoviite.infra.logging.AccessType.UPDATE
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.FetchType
import fi.fta.geoviite.infra.util.FetchType.MULTI
import fi.fta.geoviite.infra.util.FetchType.SINGLE
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getDaoResponse
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.idOrIdsSqlFragment
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.requireOne
import fi.fta.geoviite.infra.util.setUser
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

data class LayoutDaoResponse<T>(val id: IntId<T>, val rowVersion: LayoutRowVersion<T>)

interface LayoutAssetWriter<T : LayoutAsset<T>> {
    fun insert(newItem: T): LayoutDaoResponse<T>

    fun update(updatedItem: T): LayoutDaoResponse<T>

    fun deleteRow(rowId: LayoutRowId<T>): LayoutDaoResponse<T>

    fun deleteDraft(branch: LayoutBranch, id: IntId<T>): LayoutDaoResponse<T>

    fun deleteDrafts(branch: LayoutBranch): List<LayoutDaoResponse<T>>

    fun updateImplementedDesignDraftReferences(
        designRowId: LayoutRowId<T>,
        officialRowId: LayoutRowId<T>,
    ): LayoutDaoResponse<T>?
}

@Transactional(readOnly = true)
interface LayoutAssetReader<T : LayoutAsset<T>> {
    fun fetch(version: LayoutRowVersion<T>): T

    fun fetchChangeTime(): Instant

    fun fetchLayoutAssetChangeInfo(layoutContext: LayoutContext, id: IntId<T>): LayoutAssetChangeInfo?

    fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean): List<LayoutDaoResponse<T>>

    fun fetchCandidateVersions(candidateContext: LayoutContext): List<ValidationVersion<T>>

    fun fetchCandidateVersions(candidateContext: LayoutContext, ids: List<IntId<T>>): List<ValidationVersion<T>>

    fun fetchVersion(layoutContext: LayoutContext, id: IntId<T>): LayoutRowVersion<T>?

    fun fetchVersionOrThrow(layoutContext: LayoutContext, id: IntId<T>): LayoutRowVersion<T>

    fun fetchVersions(layoutContext: LayoutContext, ids: List<IntId<T>>): List<LayoutDaoResponse<T>>

    fun fetchOfficialVersionAtMomentOrThrow(branch: LayoutBranch, id: IntId<T>, moment: Instant): LayoutRowVersion<T>

    fun fetchOfficialVersionAtMoment(branch: LayoutBranch, id: IntId<T>, moment: Instant): LayoutRowVersion<T>?

    fun get(context: LayoutContext, id: IntId<T>): T? = fetchVersion(context, id)?.let(::fetch)

    fun getOrThrow(context: LayoutContext, id: IntId<T>): T = fetch(fetchVersionOrThrow(context, id))

    fun getOfficialAtMoment(branch: LayoutBranch, id: IntId<T>, moment: Instant): T? =
        fetchOfficialVersionAtMoment(branch, id, moment)?.let(::fetch)

    fun getMany(context: LayoutContext, ids: List<IntId<T>>): List<T> =
        fetchVersions(context, ids).map { r -> fetch(r.rowVersion) }

    fun list(context: LayoutContext, includeDeleted: Boolean): List<T> =
        fetchVersions(context, includeDeleted).map { r -> fetch(r.rowVersion) }
}

interface ILayoutAssetDao<T : LayoutAsset<T>> : LayoutAssetReader<T>, LayoutAssetWriter<T>

@Transactional(readOnly = true)
abstract class LayoutAssetDao<T : LayoutAsset<T>>(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val table: LayoutAssetTable,
    val cacheEnabled: Boolean,
    cacheSize: Long,
) : DaoBase(jdbcTemplateParam), ILayoutAssetDao<T> {

    protected val cache: Cache<LayoutRowVersion<T>, T> =
        Caffeine.newBuilder().maximumSize(cacheSize).expireAfterAccess(layoutCacheDuration).build()

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun fetch(version: LayoutRowVersion<T>): T =
        if (cacheEnabled) {
            cache.get(version, ::fetchInternal)
        } else {
            fetchInternal(version)
        }

    protected abstract fun fetchInternal(version: LayoutRowVersion<T>): T

    abstract fun preloadCache()

    private val allCandidateVersionsSql =
        """
        select
          official_id,
          id as row_id,
          version as row_version
        from ${table.fullName}
        where draft = (:publication_state::layout.publication_state = 'DRAFT')
          and design_id is not distinct from :design_id
    """
            .trimIndent()

    private val candidateVersionsSql =
        """
        select
          official_id,
          id as row_id,
          version as row_version
        from ${table.fullName}
        where official_id in (:ids)
          and draft = (:publication_state::layout.publication_state = 'DRAFT')
          and design_id is not distinct from :design_id
    """
            .trimIndent()

    override fun fetchCandidateVersions(candidateContext: LayoutContext): List<ValidationVersion<T>> {
        return jdbcTemplate.query<ValidationVersion<T>>(
            allCandidateVersionsSql,
            mapOf(
                "publication_state" to candidateContext.state.name,
                "design_id" to candidateContext.branch.designId?.intValue,
            ),
        ) { rs, _ ->
            ValidationVersion(rs.getIntId("official_id"), rs.getLayoutRowVersion("row_id", "row_version"))
        }
    }

    override fun fetchCandidateVersions(
        candidateContext: LayoutContext,
        ids: List<IntId<T>>,
    ): List<ValidationVersion<T>> {
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
            .query<ValidationVersion<T>>(candidateVersionsSql, params) { rs, _ ->
                ValidationVersion(rs.getIntId("official_id"), rs.getLayoutRowVersion("row_id", "row_version"))
            }
            .also { found ->
                distinctIds.forEach { id ->
                    if (found.none { f -> f.officialId == id }) throw NoSuchEntityException(table.name, id)
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
        where (id = :id or design_row_id = :id or official_row_id = :id)
          and draft = true and design_id is not distinct from :design_id
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
        first.change_time as creation_time, 
        newest_official.change_time as official_change_time, 
        newest_draft.change_time as draft_change_time, 
        newest_draft.deleted as draft_deleted 
      from ${table.versionTable} first 
        left join newest_draft on true 
        left join newest_official on true 
      where id = :id and version = 1 limit 1
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
            rs.getLayoutRowVersion("row_id", "row_version")
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
            rs.getLayoutRowVersion("row_id", "row_version")
        }
    }

    override fun fetchVersions(layoutContext: LayoutContext, ids: List<IntId<T>>): List<LayoutDaoResponse<T>> {
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
            jdbcTemplate.query(multiLayoutContextVersionSql, params) { rs, _ ->
                LayoutDaoResponse(rs.getIntId("official_id"), rs.getLayoutRowVersion("row_id", "row_version"))
            }
        }
    }

    override fun fetchOfficialVersionAtMomentOrThrow(
        branch: LayoutBranch,
        id: IntId<T>,
        moment: Instant,
    ): LayoutRowVersion<T> =
        fetchOfficialVersionAtMoment(branch, id, moment) ?: throw NoSuchEntityException(table.name, id)

    // language=SQL
    private val officialVersionAtMomentSql =
        """
        with
          versions as (
            select distinct on (v.id) id, v.version, v.deleted, design_id is not null as is_design
              from ${table.versionTable} v
              where
                (v.official_row_id = :id or v.id = :id)
                and v.change_time <= :moment
                and not v.draft
                and (design_id is null or design_id = :design_id)
              order by v.id, v.change_time desc
          )
        select id, version
        from versions
        where deleted = false
        order by (case when is_design then 0 else 1 end)
        limit 1
    """
            .trimIndent()

    override fun fetchOfficialVersionAtMoment(
        branch: LayoutBranch,
        id: IntId<T>,
        moment: Instant,
    ): LayoutRowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, LocationTrack::class, id)
        val params =
            mapOf("design_id" to branch.designId?.intValue, "id" to id.intValue, "moment" to Timestamp.from(moment))
        return jdbcTemplate.queryOptional(officialVersionAtMomentSql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "version")
        }
    }

    @Transactional
    override fun deleteRow(rowId: LayoutRowId<T>): LayoutDaoResponse<T> {
        val sql =
            """
            delete from ${table.fullName}
            where id = :row_id
              and (draft = true or design_id is not null) -- Don't allow deleting main-official rows
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("row_id" to rowId.intValue)
        return jdbcTemplate
            .query<LayoutDaoResponse<T>>(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            }
            .singleOrNull()
            .let { deleted ->
                if (deleted == null)
                    error(
                        "No rows were deleted (did you try to delete a main-official row?): type=${table.name} id=$rowId"
                    )
                logger.daoAccess(DELETE, table.fullName, deleted)
                deleted
            }
    }

    override fun updateImplementedDesignDraftReferences(
        designRowId: LayoutRowId<T>,
        officialRowId: LayoutRowId<T>,
    ): LayoutDaoResponse<T>? {
        val sql =
            """
            update ${table.fullName} set
              design_row_id = null,
              official_row_id = :official_row_id
            where draft = true
              and design_id is not null
              and design_row_id = :design_row_id
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("official_row_id" to officialRowId.intValue, "design_row_id" to designRowId.intValue)
        return jdbcTemplate
            .query<LayoutDaoResponse<T>>(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            }
            .singleOrNull()
            ?.also { updated -> logger.daoAccess(UPDATE, table.fullName, updated) }
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<T>): LayoutDaoResponse<T> =
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
    override fun deleteDrafts(branch: LayoutBranch): List<LayoutDaoResponse<T>> = deleteDraftsInternal(branch)

    private fun deleteDraftsInternal(branch: LayoutBranch, id: IntId<T>? = null): List<LayoutDaoResponse<T>> {
        val sql =
            """
            delete from ${table.fullName}
            where draft = true 
              and (:id::int is null or :id = id or :id = design_row_id or :id = official_row_id)
              and design_id is not distinct from :design_id
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("id" to id?.intValue, "design_id" to branch.designId?.intValue)
        return jdbcTemplate
            .query<LayoutDaoResponse<T>>(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            }
            .also { deleted -> logger.daoAccess(DELETE, table.fullName, deleted) }
    }
}

private fun fetchContextVersionSql(table: LayoutAssetTable, fetchType: FetchType) =
    // language=SQL
    """
        select official_id, row_id, row_version
          from ${idOrIdsSqlFragment(fetchType)} official_ids (id)
            cross join lateral ${table.fullLayoutContextFunction}(:publication_state::layout.publication_state,
                                                                  :design_id::int,
                                                                  id) ilc;
    """
        .trimIndent()

fun <T : LayoutAsset<T>> verifyObjectIsExisting(item: T) = verifyObjectIsExisting(item.contextData)

fun <T : LayoutAsset<T>> verifyObjectIsExisting(contextData: LayoutContextData<T>) {
    require(contextData.dataType == DataType.STORED) { "Cannot update TEMP row: context=$contextData" }
    require(contextData.rowId != null) { "DB row should have DB ID: context=$contextData" }
}

fun <T : LayoutAsset<T>> verifyObjectIsNew(item: T) = verifyObjectIsNew(item.contextData)

fun <T : LayoutAsset<T>> verifyObjectIsNew(contextData: LayoutContextData<T>) {
    require(contextData.dataType == DataType.TEMP) { "Cannot insert existing row as new: context=$contextData" }
    require(contextData.rowId == null) { "TEMP row should not have DB ID: context=$contextData" }
}

inline fun <reified T, reified S> getOne(rowVersion: LayoutRowVersion<T>, result: List<S>) =
    requireOne(T::class, rowVersion, result)
