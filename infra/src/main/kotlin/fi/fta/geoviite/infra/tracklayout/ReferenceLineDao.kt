package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_REFERENCE_LINE
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.ReferenceLinePublishCandidate
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
              official_id, 
              official_version,
              draft_id,
              draft_version,
              alignment_id,
              alignment_version,
              track_number_id, 
              postgis.st_astext(bounding_box) as bounding_box,
              length,
              segment_count,
              start_address
            from layout.reference_line_publication_view
            where row_id = :reference_line_id
        """.trimIndent()
        val params = mapOf("reference_line_id" to version.id.intValue)
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
                version = rs.getVersion("official_version", "draft_version"),
            )
        })
        logger.daoAccess(AccessType.FETCH, ReferenceLine::class, referenceLine.id)
        return referenceLine
    }

    @Transactional
    override fun insert(newItem: ReferenceLine): RowVersion<ReferenceLine> {
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
            returning id, version
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
        val version: RowVersion<ReferenceLine> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to generate ID for new Location Track")
        logger.daoAccess(AccessType.INSERT, ReferenceLine::class, version)
        return version
    }

    @Transactional
    override fun update(updatedItem: ReferenceLine): RowVersion<ReferenceLine> {
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
            returning id, version 
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
        val result: RowVersion<ReferenceLine> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to get new version for Reference Line")
        logger.daoAccess(AccessType.UPDATE, ReferenceLine::class, rowId)
        return result
    }

    fun fetchVersion(
        publishType: PublishType,
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
            "publication_state" to publishType.name,
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersionsNear(publishType: PublishType, bbox: BoundingBox): List<RowVersion<ReferenceLine>> {
        val sql = """
            select
              distinct rl.row_id, rl.row_version
              from layout.reference_line_publication_view rl
                inner join layout.segment s on rl.alignment_id = s.alignment_id
                and postgis.st_intersects(
                    postgis.st_makeenvelope(
                    :x_min, :y_min,
                    :x_max, :y_max,
                    :layout_srid
                  ),
                  s.bounding_box
                )
                left join layout.track_number track_number
                  on rl.track_number_id = track_number.id
              where :publication_state = any(rl.publication_states) and
                    track_number.state != 'DELETED'
        """.trimIndent()

        val params = mapOf(
            "x_min" to bbox.min.x,
            "y_min" to bbox.min.y,
            "x_max" to bbox.max.x,
            "y_max" to bbox.max.y,
            "layout_srid" to LAYOUT_SRID.code,
            "publication_state" to publishType.name,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchNonLinked(): List<ReferenceLine> {
        val sql = """
            select
              official_id,
              official_version,
              draft_id,
              draft_version,
              alignment_id,
              alignment_version,
              track_number_id,
              postgis.st_astext(bounding_box) as bounding_box,
              length,
              segment_count,
              start_address
            from layout.reference_line_publication_view
              left join layout.track_number
                on reference_line_publication_view.track_number_id = track_number.id
            where
              track_number.state != 'DELETED' and
              segment_count = 0
        """.trimIndent()
        val referenceLines = jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
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
                draft = rs.getIntIdOrNull<ReferenceLine>("draft_id")?.let(::Draft),
                version = rs.getVersion("official_version", "draft_version"),
            )
        }
        logger.daoAccess(AccessType.FETCH, ReferenceLine::class)
        return referenceLines
    }

    fun fetchPublicationInformation(publicationId: IntId<Publication>): List<ReferenceLinePublishCandidate> {
        val sql = """
          select
            reference_line_version.id,
            reference_line_version.change_time,
            reference_line_version.track_number_id,
            track_number.number as name
          from publication.reference_line published_reference_line
            left join layout.reference_line_version
              on published_reference_line.reference_line_id = reference_line_version.id
                and published_reference_line.reference_line_version = reference_line_version.version
            left join layout.track_number
              on reference_line_version.track_number_id = track_number.id
          where publication_id = :id
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "id" to publicationId.intValue,
            )
        ) { rs, _ ->
            ReferenceLinePublishCandidate(
                id = rs.getIntId("id"),
                draftChangeTime = rs.getInstant("change_time"),
                trackNumberId = rs.getIntId("track_number_id"),
                name = rs.getTrackNumber("name"),
            )
        }.also { logger.daoAccess(AccessType.FETCH, Publication::class, publicationId) }
    }
}
