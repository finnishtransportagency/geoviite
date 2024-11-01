package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getDaoResponse
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.getTrackMeter
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val REFERENCE_LINE_CACHE_SIZE = 1000L

@Transactional(readOnly = true)
@Component
class ReferenceLineDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<ReferenceLine>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_REFERENCE_LINE,
        cacheEnabled,
        REFERENCE_LINE_CACHE_SIZE,
    ) {

    override fun fetchInternal(version: LayoutRowVersion<ReferenceLine>): ReferenceLine {
        val sql =
            """
            select
              rlv.id as row_id,
              rlv.version as row_version,
              rlv.official_row_id,
              rlv.design_row_id,
              rlv.design_id,
              rlv.draft,
              rlv.cancelled,
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
        """
                .trimIndent()
        val params = mapOf("id" to version.rowId.intValue, "version" to version.version)
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getReferenceLine(rs) }).also { rl ->
            logger.daoAccess(AccessType.FETCH, ReferenceLine::class, rl.id)
        }
    }

    override fun preloadCache(): Int {
        val sql =
            """
            select
              rl.id as row_id,
              rl.version as row_version,
              rl.official_row_id,
              rl.design_row_id,
              rl.design_id,
              rl.draft,
              rl.cancelled,
              rl.alignment_id,
              rl.alignment_version,
              rl.track_number_id, 
              postgis.st_astext(av.bounding_box) as bounding_box,
              av.length,
              av.segment_count,
              rl.start_address
            from layout.reference_line rl
              left join layout.alignment_version av on rl.alignment_id = av.id and rl.alignment_version = av.version
        """
                .trimIndent()

        val referenceLines =
            jdbcTemplate.query(sql) { rs, _ -> getReferenceLine(rs) }.associateBy(ReferenceLine::version)
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
            length = rs.getDouble("length"),
            segmentCount = rs.getInt("segment_count"),
            contextData =
                rs.getLayoutContextData(
                    "official_row_id",
                    "design_row_id",
                    "design_id",
                    "row_id",
                    "row_version",
                    "draft",
                    "cancelled",
                ),
        )

    @Transactional
    override fun insert(newItem: ReferenceLine): LayoutDaoResponse<ReferenceLine> {
        val sql =
            """
            insert into layout.reference_line(
              track_number_id,
              alignment_id,
              alignment_version,
              start_address,
              draft, 
              cancelled,
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
              :cancelled,
              :official_row_id,
              :design_row_id,
              :design_id
            ) 
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        val params =
            mapOf(
                "track_number_id" to newItem.trackNumberId.intValue,
                "alignment_id" to
                    (newItem.alignmentVersion?.id?.intValue ?: error("ReferenceLine in DB needs an alignment")),
                "alignment_version" to newItem.alignmentVersion.version,
                "start_address" to newItem.startAddress.toString(),
                "draft" to newItem.isDraft,
                "cancelled" to newItem.isCancelled,
                "official_row_id" to newItem.contextData.officialRowId?.intValue,
                "design_row_id" to newItem.contextData.designRowId?.intValue,
                "design_id" to newItem.contextData.designId?.intValue,
            )

        jdbcTemplate.setUser()
        val version: LayoutDaoResponse<ReferenceLine> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            } ?: error("Failed to generate ID for new Location Track")
        logger.daoAccess(AccessType.INSERT, ReferenceLine::class, version)
        return version
    }

    @Transactional
    override fun update(updatedItem: ReferenceLine): LayoutDaoResponse<ReferenceLine> {
        val rowId =
            requireNotNull(updatedItem.contextData.rowId) {
                "Cannot update a row that doesn't have a DB ID: kmPost=$updatedItem"
            }
        val sql =
            """
            update layout.reference_line
            set
              track_number_id = :track_number_id,
              alignment_id = :alignment_id,
              alignment_version = :alignment_version,
              start_address = :start_address,
              draft = :draft,
              cancelled = :cancelled,
              official_row_id = :official_row_id,
              design_row_id = :design_row_id,
              design_id = :design_id
            where id = :id
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        val alignmentVersion = updatedItem.getAlignmentVersionOrThrow()
        val params =
            mapOf(
                "id" to rowId.intValue,
                "track_number_id" to updatedItem.trackNumberId.intValue,
                "alignment_id" to alignmentVersion.id.intValue,
                "alignment_version" to alignmentVersion.version,
                "start_address" to updatedItem.startAddress.toString(),
                "draft" to updatedItem.isDraft,
                "cancelled" to updatedItem.isCancelled,
                "official_row_id" to updatedItem.contextData.officialRowId?.intValue,
                "design_row_id" to updatedItem.contextData.designRowId?.intValue,
                "design_id" to updatedItem.contextData.designId?.intValue,
            )
        jdbcTemplate.setUser()
        val result: LayoutDaoResponse<ReferenceLine> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            } ?: error("Failed to get new version for Reference Line")
        logger.daoAccess(AccessType.UPDATE, ReferenceLine::class, result)
        return result
    }

    fun getByTrackNumber(context: LayoutContext, trackNumberId: IntId<TrackLayoutTrackNumber>): ReferenceLine? =
        fetchVersionByTrackNumberId(context, trackNumberId)?.let(::fetch)

    fun fetchVersionByTrackNumberId(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): LayoutRowVersion<ReferenceLine>? {
        // language=SQL
        val sql =
            """
            select rl.row_id, rl.row_version 
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
        return jdbcTemplate.queryOptional(sql, params) { rs, _ -> rs.getLayoutRowVersion("row_id", "row_version") }
    }

    override fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
    ): List<LayoutDaoResponse<ReferenceLine>> {
        val sql =
            """
            select
              rl.official_id,
              rl.row_id,
              rl.row_version
            from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
              left join lateral layout.track_number_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id,
                                                                      rl.track_number_id) tn on true
            where (:include_deleted = true or tn.state != 'DELETED')
        """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getDaoResponse("official_id", "row_id", "row_version") }
    }

    fun fetchVersionsNear(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        includeDeleted: Boolean = false,
    ): List<LayoutRowVersion<ReferenceLine>> {
        val sql =
            """
            select reference_line.id as row_id, reference_line.version as row_version
              from (
                select *
                  from layout.reference_line, layout.reference_line_is_in_layout_context(
                      :publication_state::layout.publication_state, :design_id, reference_line)
              ) reference_line
                join (
                select *
                  from layout.track_number, layout.track_number_is_in_layout_context(
                      :publication_state::layout.publication_state, :design_id, track_number)
              ) track_number on reference_line.track_number_id = track_number.id
                join layout.alignment
                     on reference_line.alignment_id = alignment.id and reference_line.alignment_version = alignment.version
              where (:include_deleted or track_number.state != 'DELETED')
                and postgis.st_intersects(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                                          alignment.bounding_box)
                and exists(select *
                             from layout.segment_version
                               join layout.segment_geometry on geometry_id = segment_geometry.id
                             where segment_version.alignment_id = reference_line.alignment_id
                               and segment_version.alignment_version = reference_line.alignment_version
                               and postgis.st_intersects(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                                                         segment_geometry.bounding_box));
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

        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getLayoutRowVersion("row_id", "row_version") }
    }

    fun fetchVersionsNonLinked(context: LayoutContext): List<LayoutRowVersion<ReferenceLine>> {
        val sql =
            """
            select
              rl.row_id,
              rl.row_version
            from layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) rl
              left join lateral layout.track_number_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id,
                                                                      rl.track_number_id) tn on(true)
            where tn.state != 'DELETED'
              and rl.segment_count = 0
        """
                .trimIndent()
        val params = mapOf("publication_state" to context.state.name, "design_id" to context.branch.designId?.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getLayoutRowVersion("row_id", "row_version") }
    }
}
