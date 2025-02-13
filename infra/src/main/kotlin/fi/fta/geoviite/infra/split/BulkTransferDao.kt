package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getIntOrNull
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.setUser
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class BulkTransferDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun create(splitId: IntId<Split>): RowVersion<BulkTransfer> {
        val sql =
            """
            insert into integrations.ratko_bulk_transfer (
                split_id, 
                state, 
                expedited_start,
                temporary_failure
            ) values (
                :split_id,
                'PENDING',
                'false',
                'false'
            )
            returning split_id, version
        """
                .trimIndent()

        jdbcTemplate.setUser()
        return getOne(
                splitId,
                jdbcTemplate.query(sql, mapOf("split_id" to splitId.intValue)) { rs, _ ->
                    rs.getRowVersion<BulkTransfer>("split_id", "version")
                },
            )
            .also { logger.daoAccess(AccessType.INSERT, BulkTransfer::class, splitId) }
    }

    @Transactional
    fun update(
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
    ): RowVersion<BulkTransfer> {
        val sql =
            """
            update integrations.ratko_bulk_transfer set
                state = coalesce(:state::integrations.bulk_transfer_state, state),
                expedited_start = coalesce(:expedited_start, expedited_start),
                temporary_failure = coalesce(:temporary_failure, temporary_failure),
                
                ratko_bulk_transfer_id = coalesce(:ratko_bulk_transfer_id, ratko_bulk_transfer_id),
                ratko_start_time = coalesce(:ratko_start_time, ratko_start_time),
                ratko_end_time = coalesce(:ratko_end_time, ratko_end_time),
                
                assets_total = coalesce(:assets_total, assets_total),
                assets_moved = coalesce(:assets_moved, assets_moved),
                
                trex_assets_total = coalesce(:trex_assets_total, trex_assets_total),
                trex_assets_remaining = coalesce(:trex_assets_remaining, trex_assets_remaining)
            where 
                split_id = :split_id -- TODO This is missing cases where the update is unnecessary, eg. no actual values are modified (so no need to create a new version, should probably use on conflict update or something)
            returning 
                split_id, version
            """
                .trimIndent()

        val params =
            mapOf(
                "state" to state?.name,
                "expedited_start" to expeditedStart,
                "temporary_failure" to temporaryFailure,
                "ratko_bulk_transfer_id" to ratkoBulkTransferId?.intValue,
                "ratko_start_time" to ratkoStartTime?.let { instant -> Timestamp.from(instant) },
                "ratko_end_time" to ratkoEndTime?.let { instant -> Timestamp.from(instant) },
                "assets_total" to assetsTotal,
                "assets_moved" to assetsMoved,
                "trex_assets_total" to trexAssetsTotal,
                "trex_assets_remaining" to trexAssetsRemaining,
                "split_id" to splitId.intValue,
            )

        jdbcTemplate.setUser()
        return getOne(
                splitId,
                jdbcTemplate.query(sql, params) { rs, _ -> rs.getRowVersion<BulkTransfer>("split_id", "version") },
            )
            .also { logger.daoAccess(AccessType.UPDATE, BulkTransfer::class, splitId) }
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
