package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import java.time.Instant

enum class BulkTransferState {
    PENDING,
    CREATED,
    IN_PROGRESS,
    DONE,
    FAILED,
}

data class BulkTransfer(
    val splitId: IntId<Split>,
    val state: BulkTransferState,
    val expeditedStart: Boolean,
    val temporaryFailure: Boolean,
    val ratkoBulkTransferId: IntId<BulkTransfer>?,
    val ratkoStartTime: Instant?,
    val ratkoEndTime: Instant?,
    val assetsTotal: Int?,
    val assetsMoved: Int?,
    val trexAssetsTotal: Int?,
    val trexAssetsRemaining: Int?,
)
