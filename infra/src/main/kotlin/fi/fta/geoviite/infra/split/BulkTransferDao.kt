package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getIntOrNull
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.setUser
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

val DEFAULT_BULK_TRANSFER_STATE = BulkTransferState.PENDING
const val DEFAULT_EXPEDITED_START = false
const val DEFAULT_TEMPORARY_FAILURE = false

@Transactional(readOnly = true)
@Component
class BulkTransferDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun create(splitId: IntId<Split>) {
        upsert(splitId)
    }

    @Transactional
    fun upsert(
        splitId: IntId<Split>,
        state: BulkTransferState? = null,
        expeditedStart: Boolean? = null,
        temporaryFailure: Boolean? = null,
        ratkoBulkTransferId: IntId<BulkTransfer>? = null,
        ratkoStartTime: Instant? = null,
        ratkoEndTime: Instant? = null,
        assetsTotal: Int? = null,
        assetsMoved: Int? = null,
        trexAssetsTotal: Int? = null,
        trexAssetsRemaining: Int? = null,
    ) {
        val savedBulkTransfer = getBySplitId(splitId)

        val updatedBulkTransfer =
            BulkTransfer(
                splitId = splitId,
                state = state ?: savedBulkTransfer?.state ?: DEFAULT_BULK_TRANSFER_STATE,
                expeditedStart = expeditedStart ?: savedBulkTransfer?.expeditedStart ?: DEFAULT_EXPEDITED_START,
                temporaryFailure = temporaryFailure ?: savedBulkTransfer?.temporaryFailure ?: DEFAULT_TEMPORARY_FAILURE,
                ratkoBulkTransferId = ratkoBulkTransferId ?: savedBulkTransfer?.ratkoBulkTransferId,
                ratkoStartTime = ratkoStartTime ?: savedBulkTransfer?.ratkoStartTime,
                ratkoEndTime = ratkoEndTime ?: savedBulkTransfer?.ratkoEndTime,
                assetsTotal = assetsTotal ?: savedBulkTransfer?.assetsTotal,
                assetsMoved = assetsMoved ?: savedBulkTransfer?.assetsMoved,
                trexAssetsTotal = trexAssetsTotal ?: savedBulkTransfer?.trexAssetsTotal,
                trexAssetsRemaining = trexAssetsRemaining ?: savedBulkTransfer?.trexAssetsRemaining,
            )

        if (savedBulkTransfer == updatedBulkTransfer) {
            logger.info("There was nothing to update for the bulk transfer of splitId=${splitId}")
        } else {
            internalUpsert(updatedBulkTransfer)
        }
    }

    private fun internalUpsert(bulkTransfer: BulkTransfer) {
        val sql =
            """
            insert into integrations.ratko_bulk_transfer (
                split_id,
                state,
                expedited_start,
                temporary_failure,
                ratko_bulk_transfer_id,
                ratko_start_time,
                ratko_end_time,
                assets_total,
                assets_moved,
                trex_assets_total,
                trex_assets_remaining
            ) values (
                :split_id,
                :state::integrations.bulk_transfer_state,
                :expedited_start,
                :temporary_failure,
                :ratko_bulk_transfer_id,
                :ratko_start_time,
                :ratko_end_time,
                :assets_total,
                :assets_moved,
                :trex_assets_total,
                :trex_assets_remaining
            ) on conflict (split_id) do update set
                state = excluded.state,
                expedited_start = excluded.expedited_start,
                temporary_failure = excluded.temporary_failure,
                ratko_bulk_transfer_id = excluded.ratko_bulk_transfer_id,
                ratko_start_time = excluded.ratko_start_time,
                ratko_end_time = excluded.ratko_end_time,
                assets_total = excluded.assets_total,
                assets_moved = excluded.assets_moved,
                trex_assets_total = excluded.trex_assets_total,
                trex_assets_remaining = excluded.trex_assets_remaining
        """
                .trimIndent()

        val params =
            mapOf(
                "state" to bulkTransfer.state.name,
                "expedited_start" to bulkTransfer.expeditedStart,
                "temporary_failure" to bulkTransfer.temporaryFailure,
                "ratko_bulk_transfer_id" to bulkTransfer.ratkoBulkTransferId?.intValue,
                "ratko_start_time" to bulkTransfer.ratkoStartTime?.let { instant -> Timestamp.from(instant) },
                "ratko_end_time" to bulkTransfer.ratkoEndTime?.let { instant -> Timestamp.from(instant) },
                "assets_total" to bulkTransfer.assetsTotal,
                "assets_moved" to bulkTransfer.assetsMoved,
                "trex_assets_total" to bulkTransfer.trexAssetsTotal,
                "trex_assets_remaining" to bulkTransfer.trexAssetsRemaining,
                "split_id" to bulkTransfer.splitId.intValue,
            )

        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, params).also {
            logger.daoAccess(AccessType.UPSERT, BulkTransfer::class, bulkTransfer.splitId)
        }
    }

    fun getBySplitId(splitId: IntId<Split>): BulkTransfer? {
        val sql =
            """
          select
              split_id,
              state,
              expedited_start,
              temporary_failure,
              ratko_bulk_transfer_id,
              ratko_start_time,
              ratko_end_time,
              assets_total,
              assets_moved,
              trex_assets_total,
              trex_assets_remaining
          from integrations.ratko_bulk_transfer
          where split_id = :split_id
            """
                .trimIndent()

        return getOptional(
            splitId,
            jdbcTemplate.query(sql, mapOf("split_id" to splitId.intValue)) { rs, _ ->
                BulkTransfer(
                    splitId = rs.getIntId("split_id"),
                    state = rs.getEnum("state"),
                    expeditedStart = rs.getBoolean("expedited_start"),
                    temporaryFailure = rs.getBoolean("temporary_failure"),
                    ratkoBulkTransferId = rs.getIntIdOrNull("ratko_bulk_transfer_id"),
                    ratkoStartTime = rs.getInstantOrNull("ratko_start_time"),
                    ratkoEndTime = rs.getInstantOrNull("ratko_end_time"),
                    assetsTotal = rs.getIntOrNull("assets_total"),
                    assetsMoved = rs.getIntOrNull("assets_moved"),
                    trexAssetsTotal = rs.getIntOrNull("trex_assets_total"),
                    trexAssetsRemaining = rs.getIntOrNull("trex_assets_remaining"),
                )
            },
        )
    }
}
