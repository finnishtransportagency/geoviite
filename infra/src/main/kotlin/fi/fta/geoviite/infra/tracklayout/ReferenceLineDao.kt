package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_REFERENCE_LINE
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.PublishedReferenceLine
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_REFERENCE_LINE
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class ReferenceLineDao(jdbcTemplateParam: NamedParameterJdbcTemplate?)
    : DraftableDaoBase<ReferenceLine>(jdbcTemplateParam, LAYOUT_REFERENCE_LINE) {

    @Cacheable(CACHE_LAYOUT_REFERENCE_LINE, sync = true)
    override fun fetch(version: RowVersion<ReferenceLine>): ReferenceLine {
        val sql = """
            select
              rlv.id as row_id,
              rlv.version as row_version,
              coalesce(rlv.draft_of_reference_line_id, rlv.id) official_id, 
              case when rlv.draft then rlv.id end as draft_id,
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
        val referenceLine = getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ ->
            ReferenceLine(
                dataType = DataType.STORED,
                id = rs.getIntId("official_id"),
                alignmentVersion = rs.getRowVersion("alignment_id", "alignment_version"),
                sourceId = null,
                trackNumberId = rs.getIntId("track_number_id"),
                startAddress = rs.getTrackMeter("start_address"),
                boundingBox = rs.getBboxOrNull("bounding_box"),
                length = rs.getDouble("length"),
                segmentCount = rs.getInt("segment_count"),
                draft = rs.getIntIdOrNull<ReferenceLine>("draft_id")?.let { id -> Draft(id) },
                version = rs.getRowVersion("row_id", "row_version"),
            )
        })
        logger.daoAccess(AccessType.FETCH, ReferenceLine::class, referenceLine.id)
        return referenceLine
    }

    @Transactional
    override fun insert(newItem: ReferenceLine): DaoResponse<ReferenceLine> {
        val sql = """
            insert into layout.reference_line(
              track_number_id,
              alignment_id,
              alignment_version,
              start_address,
              draft, 
              draft_of_reference_line_id
            ) 
            values (
              :track_number_id,
              :alignment_id,
              :alignment_version,
              :start_address, 
              :draft, 
              :draft_of_reference_line_id
            ) 
            returning 
              coalesce(draft_of_reference_line_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to newItem.trackNumberId.intValue,
            "alignment_id" to (newItem.alignmentVersion?.id?.intValue
                ?: throw IllegalStateException("ReferenceLine in DB needs an alignment")),
            "alignment_version" to newItem.alignmentVersion.version,
            "start_address" to newItem.startAddress.toString(),
            "draft" to (newItem.draft != null),
            "draft_of_reference_line_id" to draftOfId(newItem.id, newItem.draft)?.intValue,
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
        val rowId = toDbId(updatedItem.draft?.draftRowId ?: updatedItem.id)
        val sql = """
            update layout.reference_line
            set
              track_number_id = :track_number_id,
              alignment_id = :alignment_id,
              alignment_version = :alignment_version,
              start_address = :start_address,
              draft = :draft,
              draft_of_reference_line_id = :draft_of_reference_line_id
            where id = :id
            returning 
              coalesce(draft_of_reference_line_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "id" to rowId.intValue,
            "track_number_id" to updatedItem.trackNumberId.intValue,
            "alignment_id" to (updatedItem.alignmentVersion?.id?.intValue
                ?: throw IllegalStateException("ReferenceLine in DB needs an alignment")),
            "alignment_version" to updatedItem.alignmentVersion.version,
            "start_address" to updatedItem.startAddress.toString(),
            "draft" to (updatedItem.draft != null),
            "draft_of_reference_line_id" to draftOfId(updatedItem.id, updatedItem.draft)?.intValue,
        )
        jdbcTemplate.setUser()
        val result: DaoResponse<ReferenceLine> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to get new version for Reference Line")
        logger.daoAccess(AccessType.UPDATE, ReferenceLine::class, rowId)
        return result
    }

    fun fetchVersion(
        publicationState: PublishType,
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
        publicationState: PublishType,
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

    fun fetchVersionsNear(
        publicationState: PublishType,
        bbox: BoundingBox,
    ): List<RowVersion<ReferenceLine>> {
        val sql = """
            select
              rl.row_id, 
              rl.row_version
              from layout.reference_line_publication_view rl
                inner join layout.segment s on rl.alignment_id = s.alignment_id
                  and postgis.st_intersects(
                    postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                    s.bounding_box
                  )
                left join layout.track_number_publication_view tn
                  on rl.track_number_id = tn.official_id and :publication_state = any(tn.publication_states)
              where :publication_state = any(rl.publication_states) 
                and tn.state != 'DELETED'
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

    fun fetchVersionsNonLinked(publicationState: PublishType): List<RowVersion<ReferenceLine>> {
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

    fun fetchPublicationInformation(publicationId: IntId<Publication>): List<PublishedReferenceLine> {
        val sql = """
          select 
            prl.reference_line_id as id,
            prl.reference_line_version as version,
            rl.track_number_id
          from publication.reference_line prl
          left join layout.reference_line_version rl
            on rl.id = prl.reference_line_id and rl.version = prl.reference_line_version
          where prl.publication_id = :publication_id;
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            PublishedReferenceLine(
                version = rs.getRowVersion("id", "version"),
                trackNumberId = rs.getIntId("track_number_id"),
            )
        }.also { referenceLines ->
            logger.daoAccess(
                AccessType.FETCH,
                PublishedReferenceLine::class,
                referenceLines.map { it.version })
        }
    }
}
