package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private fun toSplit(rs: ResultSet, targetLocationTracks: List<SplitTarget>) =
    Split(
        id = rs.getIntId("id"),
        rowVersion = rs.getRowVersion("id", "version"),
        sourceLocationTrackId = rs.getIntId("source_location_track_id"),
        sourceLocationTrackCacheKey = throw NotImplementedError(), // GVT-3080: Todo
        /*rs.getLayoutRowVersion(
            "source_location_track_id",
            "source_location_track_design_id",
            "source_location_track_draft",
            "source_location_track_version",
        ),*/
        bulkTransferState = rs.getEnum("bulk_transfer_state"),
        bulkTransferId = rs.getIntIdOrNull("bulk_transfer_id"),
        publicationId = rs.getIntIdOrNull("publication_id"),
        publicationTime = rs.getInstantOrNull("publication_time"),
        targetLocationTracks = targetLocationTracks,
        relinkedSwitches = rs.getIntIdArray("switch_ids"),
        updatedDuplicates = rs.getIntIdArray("updated_duplicate_ids"),
    )

@Transactional(readOnly = true)
@Component
class SplitDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun saveSplit(
        sourceLocationTrackVersion: LayoutRowVersion<LocationTrack>,
        splitTargets: Collection<SplitTarget>,
        relinkedSwitches: Collection<IntId<LayoutSwitch>>,
        updatedDuplicates: Collection<IntId<LocationTrack>>,
    ): IntId<Split> {
        val sql =
            """
            insert into publication.split(
              source_location_track_id,
              layout_context_id,
              source_location_track_version,
              bulk_transfer_state,
              bulk_transfer_id,
              publication_id
            ) 
            values (
              :source_location_track_id,
              :layout_context_id,
              :source_location_track_version,
              'PENDING',
              null,
              null
            )
            returning id
        """
                .trimIndent()

        jdbcTemplate.setUser()
        val params =
            mapOf(
                "source_location_track_id" to sourceLocationTrackVersion.id.intValue,
                "layout_context_id" to sourceLocationTrackVersion.context.toSqlString(),
                "source_location_track_version" to sourceLocationTrackVersion.version,
            )
        val splitId =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getIntId<Split>("id") }
                ?: error("Failed to save split for location track: version=$sourceLocationTrackVersion")

        logger.daoAccess(AccessType.INSERT, Split::class, splitId)

        saveSplitTargets(splitId, splitTargets)
        saveRelinkedSwitches(splitId, relinkedSwitches)
        saveUpdatedDuplicates(splitId, updatedDuplicates)

        return splitId
    }

    private fun saveRelinkedSwitches(splitId: IntId<Split>, relinkedSwitches: Collection<IntId<LayoutSwitch>>) {
        val sql =
            """
            insert into publication.split_relinked_switch(split_id, switch_id)
            values (:splitId, :switchId)
        """
                .trimIndent()

        val params =
            relinkedSwitches.map { switchId -> mapOf("splitId" to splitId.intValue, "switchId" to switchId.intValue) }

        jdbcTemplate.batchUpdate(sql, params.toTypedArray())
    }

    private fun saveUpdatedDuplicates(splitId: IntId<Split>, updatedDuplicates: Collection<IntId<LocationTrack>>) {
        val sql =
            """
            insert into publication.split_updated_duplicate(split_id, duplicate_location_track_id)
            values (:splitId, :duplicateLocationTrackId)
        """
                .trimIndent()

        val params =
            updatedDuplicates.map { duplicateId ->
                mapOf("splitId" to splitId.intValue, "duplicateLocationTrackId" to duplicateId.intValue)
            }

        jdbcTemplate.batchUpdate(sql, params.toTypedArray())
    }

    @Transactional
    fun deleteSplit(splitId: IntId<Split>) {
        val sql = "delete from publication.split where id = :id"

        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, mapOf("id" to splitId.intValue)).also {
            logger.daoAccess(AccessType.DELETE, Split::class, splitId)
        }
    }

    private fun saveSplitTargets(splitId: IntId<Split>, splitTargets: Collection<SplitTarget>) {
        val sql =
            """
            insert into publication.split_target_location_track(
                split_id,
                location_track_id,
                source_start_edge_index,
                source_end_edge_index,
                operation
            )
            values (
                :splitId,
                :trackId,
                :edgeStart,
                :edgeEnd,
                :operation::publication.split_target_operaton
            )
        """
                .trimIndent()

        val params =
            splitTargets.map { st ->
                mapOf(
                    "splitId" to splitId.intValue,
                    "trackId" to st.locationTrackId.intValue,
                    "edgeStart" to st.edgeIndices.first,
                    "edgeEnd" to st.edgeIndices.last,
                    "operation" to st.operation.name,
                )
            }

        jdbcTemplate.batchUpdate(sql, params.toTypedArray())
    }

    fun getOrThrow(splitId: IntId<Split>): Split = get(splitId) ?: throw NoSuchEntityException(Split::class, splitId)

    fun get(splitId: IntId<Split>): Split? {
        val sql =
            """
          select
              split.id,
              split.version,
              split.bulk_transfer_state,
              split.bulk_transfer_id,
              split.publication_id,
              publication.publication_time,
              source_track.id as source_location_track_id,
              split.source_location_track_id,
              source_track.design_id as source_location_track_design_id,
              source_track.draft as source_location_track_draft,
              split.source_location_track_version,
              (select coalesce(array_agg(split_relinked_switch.switch_id), '{}')
               from publication.split_relinked_switch where split.id = split_relinked_switch.split_id) as switch_ids,
              (select coalesce(array_agg(split_updated_duplicate.duplicate_location_track_id), '{}')
               from publication.split_updated_duplicate
               where split.id = split_updated_duplicate.split_id) as updated_duplicate_ids
          from publication.split 
              inner join layout.location_track_version source_track 
                  on split.source_location_track_id = source_track.id
                   and split.layout_context_id = source_track.layout_context_id
                   and split.source_location_track_version = source_track.version
              left join publication.publication on split.publication_id = publication.id
          where split.id = :id
        """
                .trimIndent()

        return getOptional(
                splitId,
                jdbcTemplate.query(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
                    toSplit(rs, getSplitTargets(splitId))
                },
            )
            .also { logger.daoAccess(AccessType.FETCH, Split::class, splitId) }
    }

    fun getSplitHeader(splitId: IntId<Split>): SplitHeader {
        val sql =
            """
          select
              split.id,
              split.bulk_transfer_state,
              split.publication_id,
              ltv.id as source_location_track_id
          from publication.split 
              inner join layout.location_track_version ltv 
                  on split.source_location_track_id = ltv.id
                   and split.layout_context_id = ltv.layout_context_id
                   and split.source_location_track_version = ltv.version
          where split.id = :id
        """
                .trimIndent()

        return getOne(
                splitId,
                jdbcTemplate.query(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
                    SplitHeader(
                        id = splitId,
                        locationTrackId = rs.getIntId("source_location_track_id"),
                        bulkTransferState = rs.getEnum("bulk_transfer_state"),
                        publicationId = rs.getIntIdOrNull("publication_id"),
                    )
                },
            )
            .also { logger.daoAccess(AccessType.FETCH, SplitHeader::class, splitId) }
    }

    @Transactional
    fun updateSplit(
        splitId: IntId<Split>,
        bulkTransferState: BulkTransferState? = null,
        bulkTransferId: IntId<BulkTransfer>? = null,
        publicationId: IntId<Publication>? = null,
        sourceTrackVersion: LayoutRowVersion<LocationTrack>? = null,
    ): RowVersion<Split> {
        val sql =
            """
            update publication.split
            set 
                bulk_transfer_state = coalesce(:bulk_transfer_state::publication.bulk_transfer_state, bulk_transfer_state),
                bulk_transfer_id = coalesce(:bulk_transfer_id, bulk_transfer_id),
                publication_id = coalesce(:publication_id, publication_id),
                source_location_track_id = coalesce(:source_track_id, source_location_track_id),
                layout_context_id =
                  coalesce(:source_track_layout_context_id, layout_context_id),
                source_location_track_version = coalesce(:source_track_version, source_location_track_version)
            where id = :split_id
            returning id, version
        """
                .trimIndent()

        val params =
            mapOf(
                "bulk_transfer_state" to bulkTransferState?.name,
                "bulk_transfer_id" to bulkTransferId?.intValue,
                "publication_id" to publicationId?.intValue,
                "split_id" to splitId.intValue,
                "source_track_id" to sourceTrackVersion?.id?.intValue,
                "source_track_layout_context_id" to sourceTrackVersion?.context?.toSqlString(),
                "source_track_version" to sourceTrackVersion?.version,
            )

        jdbcTemplate.setUser()
        return getOne(splitId, jdbcTemplate.query(sql, params) { rs, _ -> rs.getRowVersion<Split>("id", "version") })
            .also { logger.daoAccess(AccessType.UPDATE, Split::class, splitId) }
    }

    private fun getSplitTargets(splitId: IntId<Split>): List<SplitTarget> {
        val sql =
            """
          select
              location_track_id,
              source_start_edge_index,
              source_end_edge_index,
              operation
          from publication.split_target_location_track
          where split_id = :id
        """
                .trimIndent()

        return jdbcTemplate.query(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
            SplitTarget(
                locationTrackId = rs.getIntId("location_track_id"),
                edgeIndices = rs.getInt("source_start_edge_index")..rs.getInt("source_end_edge_index"),
                operation = rs.getEnum("operation"),
            )
        }
    }

    fun fetchUnfinishedSplits(branch: LayoutBranch): List<Split> {
        val sql =
            """
          select
              split.id,
              split.version,
              split.bulk_transfer_state,
              split.bulk_transfer_id,
              split.publication_id,
              publication.publication_time,
              array_agg(split_relinked_switch.switch_id) as switch_ids,
              array_agg(split_updated_duplicate.duplicate_location_track_id) as updated_duplicate_ids,
              ltv.id as source_location_track_id,
              split.source_location_track_id,
              ltv.design_id as source_location_track_design_id,
              ltv.draft as source_location_track_draft,
              split.source_location_track_version
          from publication.split 
              left join publication.publication on split.publication_id = publication.id
              left join publication.split_relinked_switch on split.id = split_relinked_switch.split_id
              left join publication.split_updated_duplicate on split.id = split_updated_duplicate.split_id
              inner join layout.location_track_version ltv 
                  on split.source_location_track_id = ltv.id
                   and split.layout_context_id = ltv.layout_context_id
                   and split.source_location_track_version = ltv.version
          where split.bulk_transfer_state != 'DONE'
            and (ltv.design_id is null or ltv.design_id = :design_id)
          group by split.id, ltv.id, ltv.design_id, ltv.draft, ltv.version, publication.publication_time
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("design_id" to branch.designId?.intValue)) { rs, _ ->
                val splitId = rs.getIntId<Split>("id")
                toSplit(rs, getSplitTargets(splitId))
            }
            .also { ids -> logger.daoAccess(AccessType.FETCH, SplitTarget::class, ids.map { it.id }) }
    }

    fun fetchChangeTime() = fetchLatestChangeTime(DbTable.PUBLICATION_SPLIT)

    fun fetchSplitIdByPublication(publicationId: IntId<Publication>): IntId<Split>? {
        val sql = "select id from publication.split where publication_id = :publication_id"
        val params = mapOf("publication_id" to publicationId.intValue)

        return getOptional(publicationId, jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<Split>("id") }).also {
            _ ->
            logger.daoAccess(AccessType.FETCH, Split::class, "publicationId" to publicationId)
        }
    }

    fun locationTracksPartOfAnyUnfinishedSplit(
        branch: LayoutBranch,
        locationTrackIds: Collection<IntId<LocationTrack>>,
    ) = fetchUnfinishedSplits(branch).filter { split -> locationTrackIds.any { lt -> split.containsLocationTrack(lt) } }
}
