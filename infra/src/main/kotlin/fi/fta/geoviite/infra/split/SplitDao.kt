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
import fi.fta.geoviite.infra.util.getBooleanOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private fun toSplit(rs: ResultSet, targetLocationTracks: List<SplitTarget>, bulkTransfer: BulkTransfer?): Split {
    val id = rs.getIntId<Split>("id")
    val rowVersion = rs.getRowVersion<Split>("id", "version")
    val sourceLocationTrackId = rs.getIntId<LocationTrack>("source_location_track_id")
    val sourceLocationTrackVersion =
        rs.getLayoutRowVersion<LocationTrack>(
            "source_location_track_id",
            "source_location_track_design_id",
            "source_location_track_draft",
            "source_location_track_version",
        )
    val publicationId = rs.getIntIdOrNull<Publication>("publication_id")
    val relinkedSwitches = rs.getIntIdArray<LayoutSwitch>("switch_ids")
    val updatedDuplicates = rs.getIntIdArray<LocationTrack>("updated_duplicate_ids")

    return if (publicationId != null) {
        PublishedSplit(
            id,
            rowVersion,
            sourceLocationTrackId,
            sourceLocationTrackVersion,
            targetLocationTracks,
            relinkedSwitches,
            updatedDuplicates,
            publicationId,
            rs.getInstant("publication_time"),
            requireNotNull(bulkTransfer) { "splitId=$id must have a non-null bulk transfer state when published" },
        )
    } else {
        require(bulkTransfer == null) { "Split bulk transfer must be null if the split is not published" }

        UnpublishedSplit(
            id,
            rowVersion,
            sourceLocationTrackId,
            sourceLocationTrackVersion,
            targetLocationTracks,
            relinkedSwitches,
            updatedDuplicates,
        )
    }
}

@Transactional(readOnly = true)
@Component
class SplitDao(
    private val bulkTransferDao: BulkTransferDao, // TODO This should not be here
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

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
              publication_id
            ) 
            values (
              :source_location_track_id,
              :layout_context_id,
              :source_location_track_version,
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
        """
                .trimIndent()

        val params =
            splitTargets.map { st ->
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

    fun getOrThrow(splitId: IntId<Split>): Split = get(splitId) ?: throw NoSuchEntityException(Split::class, splitId)

    fun getPublishedSplitOrThrow(splitId: IntId<Split>): PublishedSplit = getOrThrow(splitId) as PublishedSplit

    fun get(splitId: IntId<Split>): Split? {
        val sql =
            """
          select
              split.id,
              split.version,
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
                    toSplit(rs, getSplitTargets(splitId), bulkTransferDao.getBySplitId(splitId))
                },
            )
            .also { logger.daoAccess(AccessType.FETCH, Split::class, splitId) }
    }

    fun getSplitHeader(splitId: IntId<Split>): SplitHeader {
        val sql =
            """
          select
              split.id,
              split.publication_id,
              ltv.id as source_location_track_id,
              bulk_transfer.state as bulk_transfer_state,
              bulk_transfer.expedited_start as bulk_transfer_expedited_start
          from publication.split 
              inner join layout.location_track_version ltv 
                  on split.source_location_track_id = ltv.id
                   and split.layout_context_id = ltv.layout_context_id
                   and split.source_location_track_version = ltv.version
              left join integrations.ratko_bulk_transfer bulk_transfer on split.id = bulk_transfer.split_id
          where split.id = :id
        """
                .trimIndent()

        return getOne(
                splitId,
                jdbcTemplate.query(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
                    SplitHeader(
                        id = splitId,
                        locationTrackId = rs.getIntId("source_location_track_id"),
                        bulkTransferState = rs.getEnumOrNull<BulkTransferState>("bulk_transfer_state"),
                        bulkTransferExpeditedStart = rs.getBooleanOrNull("bulk_transfer_expedited_start"),
                        publicationId = rs.getIntIdOrNull("publication_id"),
                    )
                },
            )
            .also { logger.daoAccess(AccessType.FETCH, SplitHeader::class, splitId) }
    }

    @Transactional
    fun updateSplit(
        splitId: IntId<Split>,
        publicationId: IntId<Publication>? = null,
        sourceTrackVersion: LayoutRowVersion<LocationTrack>? = null,
    ): RowVersion<Split> {
        val sql =
            """
            update publication.split
            set 
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
              source_start_segment_index,
              source_end_segment_index,
              operation
          from publication.split_target_location_track
          where split_id = :id
        """
                .trimIndent()

        return jdbcTemplate.query(sql, mapOf("id" to splitId.intValue)) { rs, _ ->
            SplitTarget(
                locationTrackId = rs.getIntId("location_track_id"),
                segmentIndices = rs.getInt("source_start_segment_index")..rs.getInt("source_end_segment_index"),
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
              left join integrations.ratko_bulk_transfer bulk_transfer on split.id = bulk_transfer.split_id
          where (bulk_transfer.split_id is null or bulk_transfer.state != 'DONE')
            and (ltv.design_id is null or ltv.design_id = :design_id)
          group by split.id, ltv.id, ltv.design_id, ltv.draft, ltv.version, publication.publication_time
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("design_id" to branch.designId?.intValue)) { rs, _ ->
                val splitId = rs.getIntId<Split>("id")
                toSplit(rs, getSplitTargets(splitId), bulkTransferDao.getBySplitId(splitId))
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
