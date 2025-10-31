package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.getTrackMeter
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setForceCustomPlan
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val REFERENCE_LINE_CACHE_SIZE = 1000L

@Component
class ReferenceLineDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<ReferenceLine, NoParams>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_REFERENCE_LINE,
        cacheEnabled,
        REFERENCE_LINE_CACHE_SIZE,
    ) {

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<ReferenceLine>) = NoParams.instance

    override fun fetchManyInternal(
        versions: Collection<LayoutRowVersion<ReferenceLine>>
    ): Map<LayoutRowVersion<ReferenceLine>, ReferenceLine> {
        if (versions.isEmpty()) return emptyMap()
        val sql =
            """
                select
                  rlv.id,
                  rlv.version,
                  rlv.design_id,
                  rlv.draft,
                  rlv.design_asset_state,
                  rlv.alignment_id,
                  rlv.alignment_version,
                  rlv.track_number_id,
                  postgis.st_astext(av.bounding_box) as bounding_box,
                  av.length,
                  av.segment_count,
                  rlv.start_address,
                  rlv.origin_design_id
                from layout.reference_line_version rlv
                  inner join lateral
                    (
                      select
                        unnest(:ids) id,
                        unnest(:layout_context_ids) layout_context_id,
                        unnest(:versions) version
                    ) args on args.id = rlv.id and args.layout_context_id = rlv.layout_context_id and args.version = rlv.version
                  left join layout.alignment_version av on rlv.alignment_id = av.id and rlv.alignment_version = av.version
                where rlv.deleted = false
            """
                .trimIndent()
        val params =
            mapOf(
                "ids" to versions.map { v -> v.id.intValue }.toTypedArray(),
                "versions" to versions.map { v -> v.version }.toTypedArray(),
                "layout_context_ids" to versions.map { v -> v.context.toSqlString() }.toTypedArray(),
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> getReferenceLine(rs) }
            .associateBy { rl -> rl.getVersionOrThrow() }
            .also { logger.daoAccess(AccessType.FETCH, ReferenceLine::class, versions) }
    }

    override fun preloadCache(): Int {
        val sql =
            """
                select
                  rl.id,
                  rl.version,
                  rl.design_id,
                  rl.draft,
                  rl.design_asset_state,
                  rl.alignment_id,
                  rl.alignment_version,
                  rl.track_number_id,
                  postgis.st_astext(av.bounding_box) as bounding_box,
                  av.length,
                  av.segment_count,
                  rl.start_address,
                  rl.origin_design_id
                from layout.reference_line rl
                  left join layout.alignment_version av on rl.alignment_id = av.id and rl.alignment_version = av.version
            """
                .trimIndent()

        val referenceLines =
            jdbcTemplate
                .query(sql) { rs, _ -> getReferenceLine(rs) }
                .associateBy { referenceLine -> requireNotNull(referenceLine.version) }

        logger.daoAccess(AccessType.FETCH, ReferenceLine::class, referenceLines.keys)
        cache.putAll(referenceLines)

        return referenceLines.size
    }

    private fun getReferenceLine(rs: ResultSet): ReferenceLine =
        ReferenceLine(
            alignmentVersion = rs.getRowVersion("alignment_id", "alignment_version"),
            sourceId = null,
            trackNumberId = rs.getIntId("track_number_id"),
            startAddress = rs.getTrackMeter("start_address"),
            boundingBox = rs.getBboxOrNull("bounding_box"),
            length = LineM(rs.getDouble("length")),
            segmentCount = rs.getInt("segment_count"),
            contextData =
                rs.getLayoutContextData("id", "design_id", "draft", "version", "design_asset_state", "origin_design_id"),
        )

    @Transactional fun save(item: ReferenceLine): LayoutRowVersion<ReferenceLine> = save(item, NoParams.instance)

    @Transactional
    override fun save(item: ReferenceLine, params: NoParams): LayoutRowVersion<ReferenceLine> {
        val id = item.id as? IntId ?: createId()

        val sql =
            """
                insert into layout.reference_line(
                  layout_context_id,
                  id,
                  track_number_id,
                  alignment_id,
                  alignment_version,
                  start_address,
                  draft,
                  design_asset_state,
                  design_id,
                  origin_design_id
                )
                values (
                  :layout_context_id,
                  :id,
                  :track_number_id,
                  :alignment_id,
                  :alignment_version,
                  :start_address,
                  :draft,
                  :design_asset_state::layout.design_asset_state,
                  :design_id,
                  :origin_design_id
                ) on conflict (id, layout_context_id) do update set
                  track_number_id = excluded.track_number_id,
                  alignment_id = excluded.alignment_id,
                  alignment_version = excluded.alignment_version,
                  start_address = excluded.start_address,
                  design_asset_state = excluded.design_asset_state,
                  origin_design_id = excluded.origin_design_id
                returning id, design_id, draft, version
            """
                .trimIndent()
        val params =
            mapOf(
                "layout_context_id" to item.contextData.layoutContext.toSqlString(),
                "id" to id.intValue,
                "track_number_id" to item.trackNumberId.intValue,
                "alignment_id" to
                    (item.alignmentVersion?.id?.intValue ?: error("ReferenceLine in DB needs an alignment")),
                "alignment_version" to item.alignmentVersion.version,
                "start_address" to item.startAddress.toString(),
                "draft" to item.isDraft,
                "design_asset_state" to item.designAssetState?.name,
                "design_id" to item.contextData.designId?.intValue,
                "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
            )

        jdbcTemplate.setUser()
        val version: LayoutRowVersion<ReferenceLine> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            } ?: error("Failed to save Location Track")
        logger.daoAccess(AccessType.INSERT, ReferenceLine::class, version)
        return version
    }

    fun getByTrackNumber(context: LayoutContext, trackNumberId: IntId<LayoutTrackNumber>): ReferenceLine? =
        fetchVersionByTrackNumberId(context, trackNumberId)?.let(::fetch)

    fun fetchVersionByTrackNumberId(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): LayoutRowVersion<ReferenceLine>? {
        // language=SQL
        val sql =
            """
                select id, design_id, draft, version
                from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
                where rl.track_number_id = :track_number_id
            """
                .trimIndent()
        val params =
            mapOf(
                "track_number_id" to trackNumberId.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    override fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
    ): List<LayoutRowVersion<ReferenceLine>> {
        val sql =
            """
                select rl.id, rl.design_id, rl.draft, rl.version
                from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
                  left join layout.track_number_in_layout_context(:publication_state::layout.publication_state,
                                                                  :design_id) tn on rl.track_number_id = tn.id
                where (:include_deleted = true or tn.state != 'DELETED')
            """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    @Transactional(readOnly = true)
    fun fetchVersionsNear(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        includeDeleted: Boolean = false,
    ): List<LayoutRowVersion<ReferenceLine>> {
        val sql =
            """
                select reference_line.id, reference_line.design_id, reference_line.draft, reference_line.version
                  from layout.reference_line_in_layout_context(
                          :publication_state::layout.publication_state, :design_id) reference_line
                    join layout.track_number_in_layout_context(
                          :publication_state::layout.publication_state, :design_id) track_number
                             on reference_line.track_number_id = track_number.id
                    join layout.alignment
                         on reference_line.alignment_id = alignment.id and reference_line.alignment_version = alignment.version
                  where (:include_deleted or track_number.state != 'DELETED')
                    and postgis.st_intersects(
                      postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                      alignment.bounding_box
                    )
                    and exists(
                      select *
                        from layout.segment_version
                          join layout.segment_geometry on geometry_id = segment_geometry.id
                        where segment_version.alignment_id = reference_line.alignment_id
                          and segment_version.alignment_version = reference_line.alignment_version
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

    fun fetchVersionsNonLinked(context: LayoutContext): List<LayoutRowVersion<ReferenceLine>> {
        val sql =
            """
                select rl.id, rl.design_id, rl.draft, rl.version
                from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
                  left join layout.track_number_in_layout_context(:publication_state::layout.publication_state,
                                                                  :design_id) tn on rl.track_number_id = tn.id
                  left join layout.alignment on rl.alignment_id = alignment.id
                where tn.state != 'DELETED'
                  and alignment.segment_count = 0
            """
                .trimIndent()
        val params = mapOf("publication_state" to context.state.name, "design_id" to context.branch.designId?.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }
}
