package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class SplitDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun saveSplit(
        locationTrackId: IntId<LocationTrack>,
        splitTargets: List<SplitTargetSaveRequest>,
    ): IntId<SplitSource> {
        val sql = """
            insert into publication.split(source_location_track_id) values (:id)
            returning id
        """.trimIndent()

        jdbcTemplate.setUser()
        val splitId = jdbcTemplate.queryForObject(sql, mapOf("id" to locationTrackId.intValue)) { rs, _ ->
            rs.getIntId<SplitSource>("id")
        } ?: error("Failed to save split for location track id=$locationTrackId")

        logger.daoAccess(AccessType.INSERT, SplitSource::class, splitId)

        saveSplitTargets(splitId, splitTargets)

        return splitId
    }

    private fun saveSplitTargets(splitId: IntId<SplitSource>, splitTargets: List<SplitTargetSaveRequest>) {
        val sql = """
            insert into publication.split_target_location_track(
                split_id,
                location_track_id,
                source_start_segment_index,
                source_end_segment_index            
            )
            values (
                :splitId,
                :trackId,
                :segmentStart,
                :segmentEnd
            )
        """.trimIndent()

        val params = splitTargets.map { st ->
            mapOf(
                "splitId" to splitId.intValue,
                "trackId" to st.locationTrackId.intValue,
                "segmentStart" to st.segmentIndices.first,
                "segmentEnd" to st.segmentIndices.last
            )
        }

        jdbcTemplate.batchUpdate(sql, params.toTypedArray())
        logger.daoAccess(AccessType.INSERT, SplitTarget::class, splitId, splitTargets.map { it.locationTrackId })
    }

    fun getSplit(splitId: IntId<SplitSource>): SplitSource {
        val sql = """
          select
              slt.id,
              slt.state,
              slt.error_cause,
              slt.publication_id,
              slt.source_location_track_id
          from publication.split slt
          where id = :id
        """.trimIndent()

        return jdbcTemplate.queryOne(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
            val targetLocationTracks = getSplitTargets(splitId)

            SplitSource(
                id = splitId,
                locationTrackId = rs.getIntId("source_location_track_id"),
                state = rs.getEnum("state"),
                errorCause = rs.getString("error_cause"),
                publicationId = rs.getIntIdOrNull("publication_id"),
                targetLocationTracks = targetLocationTracks
            )
        }.also {
            logger.daoAccess(AccessType.FETCH, SplitSource::class, splitId)
        }
    }

    @Transactional
    fun updateSplitState(split: SplitSource): IntId<SplitSource> {
        val sql = """
            update publication.split
            set 
                state = :state::publication.split_push_state,
                error_cause = :errorCause,
                publication_id = :publicationId
            where id = :splitId
        """.trimIndent()

        val params = mapOf(
            "state" to split.state.name,
            "errorCause" to split.errorCause,
            "publicationId" to split.publicationId?.intValue,
            "splitId" to split.id.intValue
        )

        jdbcTemplate.setUser()
        return jdbcTemplate.update(sql, params).let {
            logger.daoAccess(AccessType.UPDATE, SplitSource::class, split.id)
            split.id
        }
    }

    private fun getSplitTargets(splitId: IntId<SplitSource>): List<SplitTarget> {
        val sql = """
          select
              split_id,
              location_track_id,
              source_start_segment_index,
              source_end_segment_index
          from publication.split_target_location_track
          where split_id = :id
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
            SplitTarget(
                splitId = rs.getIntId("split_id"),
                locationTrackId = rs.getIntId("location_track_id"),
                segmentIndices = rs.getInt("source_start_segment_index")..rs.getInt("source_end_segment_index")
            )
        }.also {
            logger.daoAccess(AccessType.FETCH, SplitTarget::class, splitId)
        }
    }

    fun fetchUnfinishedSplits(): List<SplitSource> {
        val sql = """
          select
              slt.id,
              slt.state,
              slt.error_cause,
              slt.publication_id,
              slt.source_location_track_id
          from publication.split slt
          where slt.state != 'DONE'
        """.trimIndent()

        return jdbcTemplate.query(sql) { rs, _ ->
            val splitId = rs.getIntId<SplitSource>("id")
            val targetLocationTracks = getSplitTargets(splitId)

            SplitSource(
                id = splitId,
                locationTrackId = rs.getIntId("source_location_track_id"),
                state = rs.getEnum("state"),
                errorCause = rs.getString("error_cause"),
                publicationId = rs.getIntIdOrNull("publication_id"),
                targetLocationTracks = targetLocationTracks
            )
        }.also { ids ->
            logger.daoAccess(AccessType.FETCH, SplitTarget::class, ids.map { it.id })
        }
    }

    fun fetchUnfinishedSplitsByTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>): List<IntId<SplitSource>> {
        val sql = """
            select slt.id
            from publication.split slt
            left join publication.split_target_location_track tlt on tlt.split_id = slt.id
            inner join layout.location_track lt on lt.id = slt.source_location_track_id or lt.id = tlt.location_track_id 
            where slt.state != 'DONE' 
                and slt.publication_id is null 
                and lt.track_number_id = :trackNumberId 
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("trackNumberId" to trackNumberId.intValue)) { rs, _ ->
            rs.getIntId<SplitSource>("id")
        }.also { ids ->
            logger.daoAccess(AccessType.FETCH, SplitTarget::class, ids)
        }
    }
}
