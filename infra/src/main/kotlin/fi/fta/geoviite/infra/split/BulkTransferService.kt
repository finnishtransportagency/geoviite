package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId

@GeoviiteService
class BulkTransferService(private val bulkTransferDao: BulkTransferDao) {
    fun updateState(splitId: IntId<Split>, state: BulkTransferState) {
        bulkTransferDao.update(splitId = splitId, state = state)
    }

    fun updateExpeditedStart(splitId: IntId<Split>, expeditedStart: Boolean) {
        bulkTransferDao.update(splitId = splitId, expeditedStart = expeditedStart)
    }
}
