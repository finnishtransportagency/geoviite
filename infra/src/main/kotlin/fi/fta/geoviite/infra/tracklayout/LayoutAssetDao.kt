package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.DELETE
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
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.idOrIdsEqualSqlFragment
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

data class VersionPair<T>(val official: RowVersion<T>?, val draft: RowVersion<T>?) {
    fun getOfficialId() = official?.id ?: draft?.id
}

data class DaoResponse<T>(val id: IntId<T>, val rowVersion: RowVersion<T>)

interface LayoutAssetWriter<T : LayoutAsset<T>> {
    fun insert(newItem: T): DaoResponse<T>

    fun update(updatedItem: T): DaoResponse<T>

    fun deleteDraft(branch: LayoutBranch, id: IntId<T>): DaoResponse<T>

    fun deleteDrafts(branch: LayoutBranch): List<DaoResponse<T>>
}

@Transactional(readOnly = true)
interface LayoutAssetReader<T : LayoutAsset<T>> {
    fun fetch(version: RowVersion<T>): T

    fun fetchChangeTime(): Instant
    fun fetchLayoutAssetChangeInfo(layoutContext: LayoutContext, id: IntId<T>): LayoutAssetChangeInfo?

    fun fetchAllVersions(): List<RowVersion<T>>
    fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean): List<RowVersion<T>>

    fun fetchPublicationVersions(branch: LayoutBranch): List<ValidationVersion<T>>
    fun fetchPublicationVersions(branch: LayoutBranch, ids: List<IntId<T>>): List<ValidationVersion<T>>

    fun fetchVersion(layoutContext: LayoutContext, id: IntId<T>): RowVersion<T>?
    fun fetchVersionOrThrow(layoutContext: LayoutContext, id: IntId<T>): RowVersion<T>
    fun fetchVersions(layoutContext: LayoutContext, ids: List<IntId<T>>): List<RowVersion<T>>

    fun fetchOfficialRowVersionForPublishingInBranch(branch: LayoutBranch, version: RowVersion<T>): RowVersion<T>?

    fun fetchOfficialVersionAtMomentOrThrow(id: IntId<T>, moment: Instant): RowVersion<T>
    fun fetchOfficialVersionAtMoment(id: IntId<T>, moment: Instant): RowVersion<T>?

    fun get(context: LayoutContext, id: IntId<T>): T? = fetchVersion(context, id)?.let(::fetch)

    fun getOrThrow(context: LayoutContext, id: IntId<T>): T = fetch(fetchVersionOrThrow(context, id))

    fun getOfficialAtMoment(id: IntId<T>, moment: Instant): T? = fetchOfficialVersionAtMoment(id, moment)?.let(::fetch)

    fun getMany(context: LayoutContext, ids: List<IntId<T>>): List<T> = fetchVersions(context, ids).map(::fetch)

    fun list(context: LayoutContext, includeDeleted: Boolean): List<T> =
        fetchVersions(context, includeDeleted).map(::fetch)
}

interface ILayoutAssetDao<T : LayoutAsset<T>> : LayoutAssetReader<T>, LayoutAssetWriter<T>

@Transactional(readOnly = true)
abstract class LayoutAssetDao<T : LayoutAsset<T>>(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val table: LayoutAssetTable,
    val cacheEnabled: Boolean,
    cacheSize: Long,
) : DaoBase(jdbcTemplateParam), ILayoutAssetDao<T> {

    protected val cache: Cache<RowVersion<T>, T> =
        Caffeine.newBuilder().maximumSize(cacheSize).expireAfterAccess(layoutCacheDuration).build()

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun fetch(version: RowVersion<T>): T = if (cacheEnabled) {
        cache.get(version, ::fetchInternal)
    } else {
        fetchInternal(version)
    }

    protected abstract fun fetchInternal(version: RowVersion<T>): T

    abstract fun preloadCache()

    private val allPublicationVersionsSql = """
        select
          coalesce(official_row_id, design_row_id, id) as official_id,
          id as row_id,
          version as row_version
        from ${table.fullName}
        where draft and design_id is not distinct from :design_id
    """.trimIndent()

    private val publicationVersionsSql = """
        select
          coalesce(official_row_id, design_row_id, id) as official_id,
          id as row_id,
          version as row_version
        from ${table.fullName}
        where coalesce(official_row_id, design_row_id, id) in (:ids)
          and draft and design_id is not distinct from :design_id
    """.trimIndent()

    override fun fetchPublicationVersions(branch: LayoutBranch): List<ValidationVersion<T>> {
        return jdbcTemplate.query<ValidationVersion<T>>(
            allPublicationVersionsSql,
            mapOf("design_id" to branch.designId?.intValue)
        ) { rs, _ ->
            ValidationVersion(
                rs.getIntId("official_id"),
                rs.getRowVersion("row_id", "row_version"),
            )
        }
    }

    override fun fetchPublicationVersions(branch: LayoutBranch, ids: List<IntId<T>>): List<ValidationVersion<T>> {
        // Empty lists don't play nice in the SQL, but the result would be empty anyhow
        if (ids.isEmpty()) return listOf()
        val distinctIds = ids.distinct()
        if (distinctIds.size != ids.size) {
            logger.warn(
                "Requested publication versions with duplicate ids: duplicated=${ids.size - distinctIds.size} requested=$ids"
            )
        }
        val params = mapOf("ids" to distinctIds.map { id -> id.intValue }, "design_id" to branch.designId?.intValue)
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

    override fun fetchChangeTime(): Instant = fetchLatestChangeTime(table.dbTable)

    private val layoutAssetChangeInfoSql = """
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
    """.trimIndent()

    override fun fetchLayoutAssetChangeInfo(layoutContext: LayoutContext, id: IntId<T>): LayoutAssetChangeInfo? {
        return jdbcTemplate
            .query(
                layoutAssetChangeInfoSql,
                mapOf("id" to id.intValue, "design_id" to layoutContext.branch.designId?.intValue)
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
                    changed = if (draftDeleted) rs.getInstantOrNull("official_change_time") else rs.getInstantOrNull("draft_change_time"),
                )
            }
        }.firstOrNull()
    }

    override fun fetchAllVersions(): List<RowVersion<T>> = fetchRowVersions(table.dbTable)

    private val singleLayoutContextVersionSql = fetchContextVersionSql(table, SINGLE)
    private val multiLayoutContextVersionSql = fetchContextVersionSql(table, MULTI)

    override fun fetchVersion(layoutContext: LayoutContext, id: IntId<T>): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return jdbcTemplate.queryOptional(
            singleLayoutContextVersionSql, mapOf(
                "id" to id.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue
            ), ::toRowVersion
        )
    }

    override fun fetchVersionOrThrow(layoutContext: LayoutContext, id: IntId<T>): RowVersion<T> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, id)
        return jdbcTemplate.queryOne(
            singleLayoutContextVersionSql, mapOf(
                "id" to id.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue
            ), id.toString(), ::toRowVersion
        )
    }

    override fun fetchVersions(
        layoutContext: LayoutContext,
        ids: List<IntId<T>>,
    ): List<RowVersion<T>> {
        logger.daoAccess(AccessType.VERSION_FETCH, table.name, ids)
        return if (ids.isEmpty()) emptyList() else jdbcTemplate.query(
            multiLayoutContextVersionSql, mapOf(
                "ids" to ids.distinct().map { it.intValue },
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue
            ), ::toRowVersion
        )
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
          and design_id is null
        order by v.change_time desc
        limit 1
    """.trimIndent()

    override fun fetchOfficialVersionAtMoment(id: IntId<T>, moment: Instant): RowVersion<T>? {
        logger.daoAccess(AccessType.VERSION_FETCH, LocationTrack::class, id)
        val params = mapOf(
            "id" to id.intValue,
            "moment" to Timestamp.from(moment),
        )
        return jdbcTemplate.queryOptional(officialVersionAtMomentSql, params, ::toRowVersion)
    }

    override fun fetchOfficialRowVersionForPublishingInBranch(
        branch: LayoutBranch,
        version: RowVersion<T>,
    ): RowVersion<T>? {
        val draft = fetch(version)
        val designRowId = draft.contextData.designRowId
        return if (branch.designId == null && draft.contextData.officialRowId == null && designRowId is IntId) {
            queryDesignRowVersion(designRowId)
        } else if (draft.isDesign && draft.contextData.officialRowId != null) {
            if (designRowId is IntId) queryDesignRowVersion(designRowId) else null
        }
        else fetchVersion(branch.official, version.id)
    }

    private fun queryDesignRowVersion(designRowId: IntId<T>): RowVersion<T> = jdbcTemplate.queryOne(
        "select id, version from ${table.fullName} where id = :designRowId", mapOf("designRowId" to designRowId.intValue)
    ) { rs, _ ->
        rs.getRowVersion("id", "version")
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<T>): DaoResponse<T> = deleteDraftsInternal(branch, id)
        .let { r ->
            if (r.size > 1) {
                error { "Multiple rows deleted with one ID: type=${table.name} id=$id" }
            } else if (r.isEmpty()) {
                throw DeletingFailureException("Trying to delete a non-existing draft object: type=${table.name} id=$id")
            } else {
                r.first()
            }
        }

    @Transactional
    override fun deleteDrafts(branch: LayoutBranch): List<DaoResponse<T>> = deleteDraftsInternal(branch)

    private fun deleteDraftsInternal(branch: LayoutBranch, id: IntId<T>? = null): List<DaoResponse<T>> {
        val sql = """
            delete from ${table.fullName}
            where draft = true 
              and (:id::int is null or :id = id or :id = official_row_id)
              and design_id is not distinct from :design_id
            returning 
              coalesce(official_row_id, design_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        jdbcTemplate.setUser()
        return jdbcTemplate
            .query<DaoResponse<T>>(
                sql,
                mapOf("id" to id?.intValue, "design_id" to branch.designId?.intValue)
            ) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        }.also { deleted -> logger.daoAccess(DELETE, table.fullName, deleted) }
    }
}

private fun fetchContextVersionSql(table: LayoutAssetTable, fetchType: FetchType) =
    //language=SQL
    """
        select distinct id, version
          from (
            select coalesce(official_row_id, design_row_id, id) as official_id
              from ${table.fullName}
              where id ${idOrIdsEqualSqlFragment(fetchType)}
          ) lookup cross join lateral (
            select row_id as id, row_version as version
              from ${table.fullLayoutContextFunction}(:publication_state::layout.publication_state, :design_id::int, official_id)
          ) ilc
          """.trimIndent()

fun <T : LayoutAsset<T>> verifyObjectIsExisting(item: T) = verifyObjectIsExisting(item.contextData)

fun <T : LayoutAsset<T>> verifyObjectIsExisting(contextData: LayoutContextData<T>) {
    require(contextData.dataType == DataType.STORED) { "Cannot update TEMP row: context=$contextData" }
    require(contextData.rowId is IntId) { "DB row should have DB ID: context=$contextData" }
}

fun <T : LayoutAsset<T>> verifyObjectIsNew(item: T) = verifyObjectIsNew(item.contextData)

fun <T : LayoutAsset<T>> verifyObjectIsNew(contextData: LayoutContextData<T>) {
    require(contextData.dataType == DataType.TEMP) { "Cannot insert existing row as new: context=$contextData" }
    require(contextData.rowId !is IntId) { "TEMP row should not have DB ID: context=$contextData" }
}
