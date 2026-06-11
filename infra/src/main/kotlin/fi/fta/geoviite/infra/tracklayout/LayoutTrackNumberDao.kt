package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getTrackMeter
import fi.fta.geoviite.infra.util.getTrackNumber
import fi.fta.geoviite.infra.util.setForceCustomPlan
import fi.fta.geoviite.infra.util.setUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

const val TRACK_NUMBER_CACHE_SIZE = 1000L

@Component
class LayoutTrackNumberDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val alignmentDao: LayoutAlignmentDao,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LayoutTrackNumber, ReferenceLineGeometry>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER,
        cacheEnabled,
        TRACK_NUMBER_CACHE_SIZE,
    ),
    IExternalIdDao<LayoutTrackNumber> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.track_number_external_id",
        "layout.track_number_external_id",
    ),
    IExternallyIdentifiedLayoutAssetDao<LayoutTrackNumber> {

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<LayoutTrackNumber>) = alignmentDao.fetch(rowVersion)

    override fun fetchVersionsInternal(layoutContext: LayoutContext): List<CachedLayoutVersion<LayoutTrackNumber>> {
        val sql =
            """
            select id, design_id, draft, version, (state = 'DELETED') as deleted
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id)
            order by number
            """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            CachedLayoutVersion(
                rs.getLayoutRowVersion<LayoutTrackNumber>("id", "design_id", "draft", "version"),
                deleted = rs.getBoolean("deleted"),
            )
        }
    }

    @Transactional(readOnly = true)
    fun list(layoutContext: LayoutContext, trackNumber: TrackNumber): List<LayoutTrackNumber> =
        fetchVersions(layoutContext, false, trackNumber).map(::fetch)

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        number: TrackNumber?,
    ): List<LayoutRowVersion<LayoutTrackNumber>> {
        val sql =
            """
            select id, design_id, draft, version
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id)
            where (:number::varchar is null or :number = number)
              and (:include_deleted = true or state != 'DELETED')
            order by number
            """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
                "number" to number,
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    override fun fetchManyInternal(
        versions: Collection<LayoutRowVersion<LayoutTrackNumber>>
    ): Map<LayoutRowVersion<LayoutTrackNumber>, LayoutTrackNumber> {
        if (versions.isEmpty()) return emptyMap()
        val sql =
            """
            select
              tn.id,
              tn.version,
              tn.design_id,
              tn.draft,
              tn.design_asset_state,
              tn.number,
              tn.description,
              tn.state,
              tn.origin_design_id,
              tn.start_address,
              tn.bounding_box,
              tn.segment_count,
              tn.length
            from layout.track_number_version tn
              inner join lateral
                (
                  select
                    unnest(:ids) id,
                    unnest(:layout_context_ids) layout_context_id,
                    unnest(:versions) version
                ) args on args.id = tn.id and args.layout_context_id = tn.layout_context_id and args.version = tn.version
              where tn.deleted = false
            """
                .trimIndent()
        val params =
            mapOf(
                "ids" to versions.map { v -> v.id.intValue }.toTypedArray(),
                "versions" to versions.map { v -> v.version }.toTypedArray(),
                "layout_context_ids" to versions.map { v -> v.context.toSqlString() }.toTypedArray(),
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> getLayoutTrackNumber(rs) }
            .associateBy { tn -> tn.getVersionOrThrow() }
            .also { logger.daoAccess(AccessType.FETCH, LayoutTrackNumber::class, versions) }
    }

    override fun preloadCache(): Int {
        val sql =
            """
            select
              tn.id,
              tn.version,
              tn.design_id,
              tn.draft,
              tn.number, 
              tn.description,
              tn.state,
              tn.design_asset_state,
              tn.origin_design_id,
              tn.start_address,
              tn.bounding_box,
              tn.segment_count,
              tn.length
            from layout.track_number tn
            order by tn.id
            """
                .trimIndent()

        val trackNumbers =
            jdbcTemplate
                .query(sql) { rs, _ -> getLayoutTrackNumber(rs) }
                .associateBy { trackNumber -> requireNotNull(trackNumber.version) }

        logger.daoAccess(AccessType.FETCH, LayoutTrackNumber::class, trackNumbers.keys)
        cache.putAll(trackNumbers)

        return trackNumbers.size
    }

    private fun getLayoutTrackNumber(rs: ResultSet): LayoutTrackNumber =
        LayoutTrackNumber(
            number = rs.getTrackNumber("number"),
            description = rs.getString("description").let(::TrackNumberDescription),
            state = rs.getEnum("state"),
            contextData =
                rs.getLayoutContextData(
                    "id",
                    "design_id",
                    "draft",
                    "version",
                    "design_asset_state",
                    "origin_design_id",
                ),
            startAddress = rs.getTrackMeter("start_address"),
            boundingBox = rs.getBboxOrNull("bounding_box"),
            length = LineM(rs.getDouble("length")),
            segmentCount = rs.getInt("segment_count"),
        )

    //    @Transactional
    //    fun save(item: LayoutTrackNumber): LayoutRowVersion<LayoutTrackNumber> = save(item, NoParams.instance)

    @Transactional
    override fun save(item: LayoutTrackNumber, params: ReferenceLineGeometry): LayoutRowVersion<LayoutTrackNumber> {
        val id = item.id as? IntId ?: createId()

        // language=sql
        val sql =
            """
            insert into layout.track_number(layout_context_id,
                                            id,
                                            number,
                                            description,
                                            state,
                                            draft,
                                            design_asset_state,
                                            design_id,
                                            origin_design_id,
                                            start_address,
                                            bounding_box,
                                            segment_count,
                                            length)
              values
                (:layout_context_id,
                 :id,
                 :number,
                 :description,
                 :state::layout.state,
                 :draft,
                 :design_asset_state::layout.design_asset_state,
                 :design_id,
                 :origin_design_id,
                 :start_address,
                 postgis.st_polygonfromtext(:bounding_box, :layout_srid),
                 :segment_count,
                 :length)
              on conflict (id, layout_context_id) do update
                set number = excluded.number,
                    description = excluded.description,
                    state = excluded.state,
                    design_asset_state = excluded.design_asset_state,
                    origin_design_id = excluded.origin_design_id,
                    start_address = excluded.start_address,
                    bounding_box = excluded.bounding_box,
                    segment_count = excluded.segment_count,
                    length = excluded.length
              returning id, design_id, draft, version;
            """
                .trimIndent()
        val sqlParams =
            mapOf(
                "layout_context_id" to item.layoutContext.toSqlString(),
                "id" to id.intValue,
                "number" to item.number,
                "description" to item.description,
                "state" to item.state.name,
                "draft" to item.isDraft,
                "design_asset_state" to item.designAssetState?.name,
                "design_id" to item.contextData.designId?.intValue,
                "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
                "start_address" to item.startAddress.toString(),
                "bounding_box" to params.boundingBox?.polygonFromCorners?.toWkt(),
                "layout_srid" to LAYOUT_SRID.code,
                "segment_count" to params.segments.size,
                "length" to params.length.distance,
            )
        jdbcTemplate.setUser()
        val response: LayoutRowVersion<LayoutTrackNumber> =
            jdbcTemplate.queryForObject(sql, sqlParams) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            } ?: throw IllegalStateException("Failed to generate ID for new TrackNumber")
        logger.daoAccess(AccessType.INSERT, LayoutTrackNumber::class, response)
        clearVersionCache()
        return response
    }

    fun fetchTrackNumberNames(layoutBranch: LayoutBranch): List<TrackNumberAndChangeTime> {
        // language=sql
        val sql =
            """
            select id,
                   design_id is not null as is_design,
                   deleted or design_asset_state = 'CANCELLED' as is_gone,
                   number,
                   change_time
            from layout.track_number_version
            where (design_id is null or design_id = :design_id) and not draft
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("design_id" to layoutBranch.designId?.intValue)) { rs, _ ->
                AnyTrackNumberChange(
                    rs.getBoolean("is_design"),
                    rs.getBoolean("is_gone"),
                    TrackNumberAndChangeTime(
                        rs.getIntId("id"),
                        rs.getTrackNumber("number"),
                        rs.getInstant("change_time"),
                    ),
                )
            }
            .let(::processTrackNumberChangeHistory)
            .also { logger.daoAccess(AccessType.FETCH, "track_number_version") }
    }

    fun findNumberDuplicates(
        context: LayoutContext,
        numbers: List<TrackNumber>,
    ): Map<TrackNumber, List<LayoutRowVersion<LayoutTrackNumber>>> =
        findFieldDuplicates(context, numbers, "number") { rs -> rs.getString("number").let(::TrackNumber) }

    @Transactional
    fun savePlanItemId(id: IntId<LayoutTrackNumber>, branch: DesignBranch, planItemId: RatkoPlanItemId) {
        jdbcTemplate.setUser()
        savePlanItemIdInExistingTransaction(branch, id, planItemId)
    }

    @Transactional
    fun insertExternalId(id: IntId<LayoutTrackNumber>, branch: LayoutBranch, oid: Oid<LayoutTrackNumber>) {
        jdbcTemplate.setUser()
        insertExternalIdInExistingTransaction(branch, id, oid)
    }

    @Transactional(readOnly = true)
    fun fetchVersionsNear(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        includeDeleted: Boolean = false,
    ): List<LayoutRowVersion<LayoutTrackNumber>> {
        val sql =
            """
            select id, design_id, draft, version
              from layout.track_number_in_layout_context(
                      :publication_state::layout.publication_state, :design_id) track_number
              where (:include_deleted or state != 'DELETED')
                and exists(
                  select *
                    from layout.track_number_version_segment sv
                      inner join layout.segment_geometry on segment_geometry.id = sv.geometry_id
                    where sv.track_number_id = track_number.id
                      and sv.track_layout_context_id = track_number.layout_context_id
                      and sv.track_number_version = track_number.version
                      and postgis.st_intersects(
                        postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                        segment_geometry.bounding_box
                      )
                );
            """
                .trimIndent()

        val params =
            mapOf(
                "x_min" to bbox.min.x,
                "y_min" to bbox.min.y,
                "x_max" to bbox.max.x,
                "y_max" to bbox.max.y,
                "layout_srid" to LAYOUT_SRID.code,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
            )

        // GVT-3181 This query is poorly optimized when JDBC tries to prepare a plan for it.
        // Force a custom plan to avoid the issue. Note: this must be in the same transaction as the query.
        jdbcTemplate.setForceCustomPlan()
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    fun fetchVersionsNonLinked(context: LayoutContext): List<LayoutRowVersion<LayoutTrackNumber>> {
        val sql =
            """
            select id, design_id, draft, version
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id)
            where state != 'DELETED'
              and segment_count = 0
            """
                .trimIndent()
        val params = mapOf("publication_state" to context.state.name, "design_id" to context.branch.designId?.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }
}

private data class AnyTrackNumberChange(
    val isDesign: Boolean,
    val isGone: Boolean,
    val change: TrackNumberAndChangeTime,
)

private fun processTrackNumberChangeHistory(
    allChangesHistory: List<AnyTrackNumberChange>
): List<TrackNumberAndChangeTime> =
    allChangesHistory
        .groupBy { it.change.id }
        .mapValues { (_, trackNumberChanges) ->
            var currentInMain: TrackNumberAndChangeTime? = null
            var currentInDesign: TrackNumberAndChangeTime? = null
            trackNumberChanges
                .sortedBy { it.change.changeTime }
                .mapNotNull { row ->
                    val (isDesign, isGone, change) = row
                    when {
                        isDesign && isGone -> {
                            currentInDesign = null
                            // if the design row goes away, go back to main's version of the track number, but at the
                            // time of the deletion
                            currentInMain?.let { inMain -> change.copy(number = inMain.number) }
                        }

                        isDesign -> {
                            currentInDesign = change
                            currentInDesign
                        }

                        else -> {
                            currentInMain = change
                            currentInDesign ?: currentInMain
                        }
                    }
                }
                .let(::getSequentiallyDistinctTrackNumberChanges)
        }
        .values
        .flatten()

private fun getSequentiallyDistinctTrackNumberChanges(
    changes: List<TrackNumberAndChangeTime>
): List<TrackNumberAndChangeTime> {
    var current: TrackNumber? = null
    return changes.filter { change ->
        if (change.number == current) false
        else {
            current = change.number
            true
        }
    }
}
