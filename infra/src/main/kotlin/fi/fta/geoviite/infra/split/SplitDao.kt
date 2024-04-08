package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

private fun toSplit(rs: ResultSet, targetLocationTracks: List<SplitTarget>) = Split(
    id = rs.getIntId("id"),
    locationTrackId = rs.getIntId("source_location_track_id"),
    bulkTransferState = rs.getEnum("bulk_transfer_state"),
    publicationId = rs.getIntIdOrNull("publication_id"),
    targetLocationTracks = targetLocationTracks,
    relinkedSwitches = rs.getIntIdArray("switch_ids"),
    updatedDuplicates = rs.getIntIdArray("updated_duplicate_ids"),
)

@Transactional(readOnly = true)
@Component
class SplitDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun saveSplit(
        locationTrackId: IntId<LocationTrack>,
        splitTargets: Collection<SplitTarget>,
        relinkedSwitches: Collection<IntId<TrackLayoutSwitch>>,
        updatedDuplicates: Collection<IntId<LocationTrack>>,
    ): IntId<Split> {
        val sql = """
            insert into publication.split(source_location_track_id, bulk_transfer_state, publication_id) 
            values (:id, 'PENDING', null)
            returning id
        """.trimIndent()

        jdbcTemplate.setUser()
        val splitId = jdbcTemplate.queryForObject(sql, mapOf("id" to locationTrackId.intValue)) { rs, _ ->
            rs.getIntId<Split>("id")
        } ?: error("Failed to save split for location track id=$locationTrackId")

        logger.daoAccess(AccessType.INSERT, Split::class, splitId)

        saveSplitTargets(splitId, splitTargets)
        saveRelinkedSwitches(splitId, relinkedSwitches)
        saveUpdatedDuplicates(splitId, updatedDuplicates)

        return splitId
    }

    private fun saveRelinkedSwitches(splitId: IntId<Split>, relinkedSwitches: Collection<IntId<TrackLayoutSwitch>>) {
        val sql = """
            insert into publication.split_relinked_switch(split_id, switch_id)
            values (:splitId, :switchId)
        """.trimIndent()

        val params = relinkedSwitches.map { switchId ->
            mapOf(
                "splitId" to splitId.intValue,
                "switchId" to switchId.intValue,
            )
        }

        jdbcTemplate.batchUpdate(sql, params.toTypedArray())
    }

    private fun saveUpdatedDuplicates(splitId: IntId<Split>, updatedDuplicates: Collection<IntId<LocationTrack>>) {
        val sql = """
            insert into publication.split_updated_duplicate(split_id, duplicate_location_track_id)
            values (:splitId, :duplicateLocationTrackId)
        """.trimIndent()

        val params = updatedDuplicates.map { duplicateId ->
            mapOf(
                "splitId" to splitId.intValue,
                "duplicateLocationTrackId" to duplicateId.intValue,
            )
        }

        jdbcTemplate.batchUpdate(sql, params.toTypedArray())
    }

    @Transactional
    fun deleteSplit(splitId: IntId<Split>) {
        val sql = """
            delete from publication.split where id = :id 
        """.trimIndent()

        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, mapOf("id" to splitId.intValue)).also {
            logger.daoAccess(AccessType.DELETE, Split::class, splitId)
        }
    }

    private fun saveSplitTargets(splitId: IntId<Split>, splitTargets: Collection<SplitTarget>) {
        val sql = """
            insert into publication.split_target_location_track(
                split_id,
                location_track_id,
                source_start_segment_index,
                source_end_segment_index,
                operation
            )
            values (
                :splitId,
                :trackId,
                :segmentStart,
                :segmentEnd,
                :operation::publication.split_target_operaton
            )
        """.trimIndent()

        val params = splitTargets.map { st ->
            mapOf(
                "splitId" to splitId.intValue,
                "trackId" to st.locationTrackId.intValue,
                "segmentStart" to st.segmentIndices.first,
                "segmentEnd" to st.segmentIndices.last,
                "operation" to st.operation.name,
            )
        }

        jdbcTemplate.batchUpdate(sql, params.toTypedArray())
    }

    val splitFetchSql = """
          select
              split.id,
              split.bulk_transfer_state,
              split.publication_id,
              split.source_location_track_id,
              array_agg(split_relinked_switch.switch_id) as switch_ids,
              array_agg(split_updated_duplicate.duplicate_location_track_id) as updated_duplicate_ids
          from publication.split 
              left join publication.split_relinked_switch on split.id = split_relinked_switch.split_id
              left join publication.split_updated_duplicate on split.id = split_updated_duplicate.split_id
          where id = :id
          group by split.id
        """.trimIndent()

    fun get(splitId: IntId<Split>): Split? {
        return jdbcTemplate.queryOptional(splitFetchSql, mapOf("id" to splitId.intValue)) { rs, _ ->
            toSplit(rs, getSplitTargets(splitId))
        }.also {
            logger.daoAccess(AccessType.FETCH, Split::class, splitId)
        }
    }

    fun getOrThrow(splitId: IntId<Split>): Split {
        return jdbcTemplate.queryOne(splitFetchSql, mapOf("id" to splitId.intValue)) { rs, _ ->
            toSplit(rs, getSplitTargets(splitId))
        }.also {
            logger.daoAccess(AccessType.FETCH, Split::class, splitId)
        }
    }

    fun getSplitHeader(splitId: IntId<Split>): SplitHeader {
        val sql = """
          select
              split.id,
              split.bulk_transfer_state,
              split.publication_id,
              split.source_location_track_id
          from publication.split 
          where id = :id
          group by split.id
        """.trimIndent()

        return jdbcTemplate.queryOne(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
            SplitHeader(
                id = splitId,
                locationTrackId = rs.getIntId("source_location_track_id"),
                bulkTransferState = rs.getEnum("bulk_transfer_state"),
                publicationId = rs.getIntIdOrNull("publication_id"),
            )
        }.also {
            logger.daoAccess(AccessType.FETCH, SplitHeader::class, splitId)
        }
    }

    @Transactional
    fun updateSplitState(
        splitId: IntId<Split>,
        bulkTransferState: BulkTransferState? = null,
        publicationId: IntId<Publication>? = null,
    ): IntId<Split> {
        val sql = """
            update publication.split
            set 
                bulk_transfer_state = coalesce(:bulk_transfer_state::publication.bulk_transfer_state, bulk_transfer_state),
                publication_id = coalesce(:publication_id, publication_id)
            where id = :split_id
        """.trimIndent()

        val params = mapOf(
            "bulk_transfer_state" to bulkTransferState?.name,
            "publication_id" to publicationId?.intValue,
            "split_id" to splitId.intValue
        )

        jdbcTemplate.setUser()
        return jdbcTemplate.update(sql, params).let {
            logger.daoAccess(AccessType.UPDATE, Split::class, splitId)
            splitId
        }
    }

    private fun getSplitTargets(splitId: IntId<Split>): List<SplitTarget> {
        val sql = """
          select
              location_track_id,
              source_start_segment_index,
              source_end_segment_index,
              operation
          from publication.split_target_location_track
          where split_id = :id
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
            SplitTarget(
                locationTrackId = rs.getIntId("location_track_id"),
                segmentIndices = rs.getInt("source_start_segment_index")..rs.getInt("source_end_segment_index"),
                operation = rs.getEnum("operation"),
            )
        }
    }

    fun fetchUnfinishedSplits(): List<Split> {
        val sql = """
          select
              split.id,
              split.bulk_transfer_state,
              split.publication_id,
              split.source_location_track_id,
              array_agg(split_relinked_switch.switch_id) as switch_ids,
              array_agg(split_updated_duplicate.duplicate_location_track_id) as updated_duplicate_ids
          from publication.split 
              left join publication.split_relinked_switch on split.id = split_relinked_switch.split_id
              left join publication.split_updated_duplicate on split.id = split_updated_duplicate.split_id
          where split.bulk_transfer_state != 'DONE'
          group by split.id
        """.trimIndent()

        return jdbcTemplate.query(sql) { rs, _ ->
            val splitId = rs.getIntId<Split>("id")
            toSplit(rs, getSplitTargets(splitId))
        }.also { ids ->
            logger.daoAccess(AccessType.FETCH, SplitTarget::class, ids.map { it.id })
        }
    }

    fun fetchUnfinishedSplitIdsByTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>): List<IntId<Split>> {
        val sql = """
            select slt.id
            from publication.split slt
                left join publication.split_target_location_track tlt on tlt.split_id = slt.id
                inner join layout.location_track lt 
                    on lt.id = slt.source_location_track_id or lt.id = tlt.location_track_id 
            where slt.bulk_transfer_state != 'DONE' and lt.track_number_id = :trackNumberId 
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("trackNumberId" to trackNumberId.intValue)) { rs, _ ->
            rs.getIntId<Split>("id")
        }.also { ids ->
            logger.daoAccess(AccessType.FETCH, Split::class, ids)
        }
    }

    fun fetchChangeTime() = fetchLatestChangeTime(DbTable.PUBLICATION_SPLIT)

    fun fetchSplitIdByPublication(publicationId: IntId<Publication>): IntId<Split>? {
        val sql = """
            select id from publication.split where publication_id = :publication_id
        """.trimIndent()

        return jdbcTemplate.queryOptional(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            rs.getIntId<Split>("id")
        }.also { _ ->
            logger.daoAccess(AccessType.FETCH, Split::class, "publicationId" to publicationId)
        }
    }

    fun locationTracksPartOfAnyUnfinishedSplit(locationTrackIds: Collection<IntId<LocationTrack>>) =
        fetchUnfinishedSplits().filter { split ->
            locationTrackIds.any { lt -> split.containsLocationTrack(lt) }
        }
}
