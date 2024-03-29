package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_REFERENCE_LINE
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
) : LayoutAssetDao<ReferenceLine>(jdbcTemplateParam, LAYOUT_REFERENCE_LINE, cacheEnabled, REFERENCE_LINE_CACHE_SIZE) {

    override fun fetchInternal(version: RowVersion<ReferenceLine>): ReferenceLine {
        val sql = """
            select
              rlv.id as row_id,
              rlv.version as row_version,
              rlv.official_row_id, 
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
        return getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ -> getReferenceLine(rs) }).also { rl ->
            logger.daoAccess(AccessType.FETCH, ReferenceLine::class, rl.id)
        }
    }

    override fun preloadCache() {
        val sql = """
            select
              rl.id as row_id,
              rl.version as row_version,
              rl.official_row_id, 
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
        contextData = rs.getLayoutContextData("official_row_id", "row_id", "draft"),
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
              official_row_id
            ) 
            values (
              :track_number_id,
              :alignment_id,
              :alignment_version,
              :start_address, 
              :draft, 
              :official_row_id
            ) 
            returning 
              coalesce(official_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to newItem.trackNumberId.intValue,
            "alignment_id" to (newItem.alignmentVersion?.id?.intValue
                ?: throw IllegalStateException("ReferenceLine in DB needs an alignment")),
            "alignment_version" to newItem.alignmentVersion.version,
            "start_address" to newItem.startAddress.toString(),
            "draft" to newItem.isDraft,
            "official_row_id" to newItem.contextData.officialRowId?.let(::toDbId)?.intValue,
        )

        jdbcTemplate.setUser()
        val version: DaoResponse<ReferenceLine> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to generate ID for new Location Track")
        logger.daoAccess(AccessType.INSERT, ReferenceLine::class, version)
        return version
    }

    @Transactional
    override fun update(updatedItem: ReferenceLine): DaoResponse<ReferenceLine> {
        val sql = """
            update layout.reference_line
            set
              track_number_id = :track_number_id,
              alignment_id = :alignment_id,
              alignment_version = :alignment_version,
              start_address = :start_address,
              draft = :draft,
              official_row_id = :official_row_id
            where id = :id
            returning 
              coalesce(official_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "id" to toDbId(updatedItem.contextData.rowId).intValue,
            "track_number_id" to updatedItem.trackNumberId.intValue,
            "alignment_id" to updatedItem.getAlignmentVersionOrThrow().id.intValue,
            "alignment_version" to updatedItem.getAlignmentVersionOrThrow().version,
            "start_address" to updatedItem.startAddress.toString(),
            "draft" to updatedItem.isDraft,
            "official_row_id" to updatedItem.contextData.officialRowId?.let(::toDbId)?.intValue,
        )
        jdbcTemplate.setUser()
        val result: DaoResponse<ReferenceLine> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to get new version for Reference Line")
        logger.daoAccess(AccessType.UPDATE, ReferenceLine::class, result)
        return result
    }

    fun getByTrackNumber(publicationState: PublicationState, trackNumberId: IntId<TrackLayoutTrackNumber>): ReferenceLine? =
        fetchVersionByTrackNumberId(publicationState, trackNumberId)?.let(::fetch)

    fun fetchVersionByTrackNumberId(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): RowVersion<ReferenceLine>? {
        //language=SQL
        val sql = """
            select rl.row_id, rl.row_version 
            from layout.reference_line_publication_view rl
            where rl.track_number_id = :track_number_id 
              and :publication_state = any(rl.publication_states)
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "publication_state" to publicationState.name,
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    override fun fetchVersions(
        publicationState: PublicationState,
        includeDeleted: Boolean,
    ): List<RowVersion<ReferenceLine>> {
        val sql = """
            select
              rl.row_id,
              rl.row_version
            from layout.reference_line_publication_view rl
              left join layout.track_number_publication_view tn
                on rl.track_number_id = tn.official_id and :publication_state = any(tn.publication_states)
            where :publication_state = any(rl.publication_states) 
              and (:include_deleted = true or tn.state != 'DELETED')
        """.trimIndent()
        val params = mapOf(
            "publication_state" to publicationState.name,
            "include_deleted" to includeDeleted,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    // TODO: No IT test runs this
    fun fetchVersionsNear(
        publicationState: PublicationState,
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
                join layout.reference_line_publication_view rl using (alignment_id, alignment_version)
                join layout.track_number_publication_view tn on rl.track_number_id = tn.official_id
              where :publication_state = any (rl.publication_states)
                and :publication_state = any (tn.publication_states)
                and tn.state != 'DELETED';
        """.trimIndent()

        val params = mapOf(
            "x_min" to bbox.min.x,
            "y_min" to bbox.min.y,
            "x_max" to bbox.max.x,
            "y_max" to bbox.max.y,
            "layout_srid" to LAYOUT_SRID.code,
            "publication_state" to publicationState.name,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersionsNonLinked(publicationState: PublicationState): List<RowVersion<ReferenceLine>> {
        val sql = """
            select
              rl.row_id,
              rl.row_version
            from layout.reference_line_publication_view rl
              left join layout.track_number_publication_view tn
                on rl.track_number_id = tn.official_id and :publication_state = any(tn.publication_states)
            where :publication_state = any(rl.publication_states) 
              and tn.state != 'DELETED'
              and rl.segment_count = 0
        """.trimIndent()
        val params = mapOf("publication_state" to publicationState.name)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }
}
