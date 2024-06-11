package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getDaoResponse
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.getTrackMeter
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

const val REFERENCE_LINE_CACHE_SIZE = 1000L

@Transactional(readOnly = true)
@Component
class ReferenceLineDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) : LayoutAssetDao<ReferenceLine>(jdbcTemplateParam, LayoutAssetTable.LAYOUT_ASSET_REFERENCE_LINE, cacheEnabled, REFERENCE_LINE_CACHE_SIZE) {

    override fun fetchInternal(version: RowVersion<ReferenceLine>): ReferenceLine {
        val sql = """
            select
              rlv.id as row_id,
              rlv.version as row_version,
              rlv.official_row_id,
              rlv.design_row_id,
              rlv.design_id,
              rlv.draft,
              rlv.alignment_id,
              rlv.alignment_version,
              rlv.track_number_id, 
              postgis.st_astext(av.bounding_box) as bounding_box,
              av.length,
              av.segment_count,
              rlv.start_address
            from layout.reference_line_version rlv
              left join layout.alignment_version av on rlv.alignment_id = av.id and rlv.alignment_version = av.version
            where rlv.id = :id
              and rlv.version = :version
              and rlv.deleted = false
        """.trimIndent()
        val params = mapOf(
            "id" to version.id.intValue,
            "version" to version.version,
        )
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getReferenceLine(rs) }).also { rl ->
            logger.daoAccess(AccessType.FETCH, ReferenceLine::class, rl.id)
        }
    }

    override fun preloadCache() {
        val sql = """
            select
              rl.id as row_id,
              rl.version as row_version,
              rl.official_row_id,
              rl.design_row_id,
              rl.design_id,
              rl.draft,
              rl.alignment_id,
              rl.alignment_version,
              rl.track_number_id, 
              postgis.st_astext(av.bounding_box) as bounding_box,
              av.length,
              av.segment_count,
              rl.start_address
            from layout.reference_line rl
              left join layout.alignment_version av on rl.alignment_id = av.id and rl.alignment_version = av.version
        """.trimIndent()

        val referenceLines = jdbcTemplate
            .query(sql, mapOf<String, Any>()) { rs, _ -> getReferenceLine(rs) }
            .associateBy(ReferenceLine::version)
        logger.daoAccess(AccessType.FETCH, ReferenceLine::class, referenceLines.keys)
        cache.putAll(referenceLines)
    }

    private fun getReferenceLine(rs: ResultSet): ReferenceLine = ReferenceLine(
        alignmentVersion = rs.getRowVersion("alignment_id", "alignment_version"),
        sourceId = null,
        trackNumberId = rs.getIntId("track_number_id"),
        startAddress = rs.getTrackMeter("start_address"),
        boundingBox = rs.getBboxOrNull("bounding_box"),
        length = rs.getDouble("length"),
        segmentCount = rs.getInt("segment_count"),
        version = rs.getRowVersion("row_id", "row_version"),
        contextData = rs.getLayoutContextData("official_row_id", "design_row_id", "design_id", "row_id", "draft"),
    )

    @Transactional
    override fun insert(newItem: ReferenceLine): DaoResponse<ReferenceLine> {
        val sql = """
            insert into layout.reference_line(
              track_number_id,
              alignment_id,
              alignment_version,
              start_address,
              draft, 
              official_row_id,
              design_row_id,
              design_id
            ) 
            values (
              :track_number_id,
              :alignment_id,
              :alignment_version,
              :start_address, 
              :draft, 
              :official_row_id,
              :design_row_id,
              :design_id
            ) 
            returning 
              coalesce(official_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val alignmentVersion = newItem.getAlignmentVersionOrThrow()
        val params = mapOf(
            "track_number_id" to newItem.trackNumberId.intValue,
            "alignment_id" to alignmentVersion.id.intValue,
            "alignment_version" to alignmentVersion.version,
            "start_address" to newItem.startAddress.toString(),
            "draft" to newItem.isDraft,
            "official_row_id" to newItem.contextData.officialRowId?.intValue,
            "design_row_id" to newItem.contextData.designRowId?.intValue,
            "design_id" to newItem.contextData.designId?.intValue,
        )

        jdbcTemplate.setUser()
        val version: DaoResponse<ReferenceLine> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: error("Failed to generate ID for new Reference Line")
        logger.daoAccess(AccessType.INSERT, ReferenceLine::class, version)
        return version
    }

    @Transactional
    override fun update(updatedItem: ReferenceLine): DaoResponse<ReferenceLine> {
        val rowId = requireNotNull(updatedItem.contextData.rowId) {
            "Cannot update a row that doesn't have a DB ID: kmPost=$updatedItem"
        }
        val sql = """
            update layout.reference_line
            set
              track_number_id = :track_number_id,
              alignment_id = :alignment_id,
              alignment_version = :alignment_version,
              start_address = :start_address,
              draft = :draft,
              official_row_id = :official_row_id,
              design_row_id = :design_row_id,
              design_id = :design_id
            where id = :id
            returning 
              coalesce(official_row_id, design_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val alignmentVersion = updatedItem.getAlignmentVersionOrThrow()
        val params = mapOf(
            "id" to rowId.intValue,
            "track_number_id" to updatedItem.trackNumberId.intValue,
            "alignment_id" to alignmentVersion.id.intValue,
            "alignment_version" to alignmentVersion.version,
            "start_address" to updatedItem.startAddress.toString(),
            "draft" to updatedItem.isDraft,
            "official_row_id" to updatedItem.contextData.officialRowId?.intValue,
            "design_row_id" to updatedItem.contextData.designRowId?.intValue,
            "design_id" to updatedItem.contextData.designId?.intValue,
        )
        jdbcTemplate.setUser()
        val result: DaoResponse<ReferenceLine> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: error("Failed to get new version for updated Reference Line")
        logger.daoAccess(AccessType.UPDATE, ReferenceLine::class, result)
        return result
    }

    fun getByTrackNumber(context: LayoutContext, trackNumberId: IntId<TrackLayoutTrackNumber>): ReferenceLine? =
        fetchVersionByTrackNumberId(context, trackNumberId)?.let(::fetch)

    fun fetchVersionByTrackNumberId(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): RowVersion<ReferenceLine>? {
        //language=SQL
        val sql = """
            select rl.row_id, rl.row_version 
            from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
            where rl.track_number_id = :track_number_id
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    override fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
    ): List<RowVersion<ReferenceLine>> {
        val sql = """
            select
              rl.row_id,
              rl.row_version
            from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
              left join lateral layout.track_number_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id,
                                                                      rl.track_number_id) tn on true
            where (:include_deleted = true or tn.state != 'DELETED')
        """.trimIndent()
        val params = mapOf(
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
            "include_deleted" to includeDeleted,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    // TODO: No IT test runs this
    fun fetchVersionsNear(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<RowVersion<ReferenceLine>> {
        val sql = """
            select
              rl.row_id,
              rl.row_version
              from (
                select distinct alignment_id, alignment_version
                  from layout.segment_geometry
                    join layout.segment_version on segment_geometry.id = geometry_id
                  where postgis.st_intersects(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                                              bounding_box)
              ) sv
                join layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl using (alignment_id, alignment_version)
                cross join lateral layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id, rl.track_number_id) tn
              where tn.state != 'DELETED';
        """.trimIndent()

        val params = mapOf(
            "x_min" to bbox.min.x,
            "y_min" to bbox.min.y,
            "x_max" to bbox.max.x,
            "y_max" to bbox.max.y,
            "layout_srid" to LAYOUT_SRID.code,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersionsNonLinked(context: LayoutContext): List<RowVersion<ReferenceLine>> {
        val sql = """
            select
              rl.row_id,
              rl.row_version
            from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
              left join lateral layout.track_number_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id,
                                                                      rl.track_number_id) tn on(true)
            where tn.state != 'DELETED'
              and rl.segment_count = 0
        """.trimIndent()
        val params = mapOf(
            "publication_state" to context.state.name,
            "design_id" to context.branch.designId?.intValue,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }
}
