package fi.fta.geoviite.infra.velho

import PVDocument
import PVDocumentHeader
import PVDocumentStatus
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class VelhoDocumentService @Autowired constructor(
    private val velhoDao: VelhoDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getDocumentHeaders(status: PVDocumentStatus?): List<PVDocumentHeader> {
        logger.serviceCall("getDocumentHeaders", "status" to status)
        return velhoDao.getDocumentHeaders(status)
    }

    fun updateDocumentStatus(id: IntId<PVDocument>, status: PVDocumentStatus): IntId<PVDocument> {
        logger.serviceCall("updateDocumentStatus", "id" to id, "status" to status)
        return velhoDao.updateFileStatus(id, status)
    }

    fun getFile(id: IntId<PVDocument>): InfraModelFile? {
        logger.serviceCall("getFile", "id" to id)
        return velhoDao.getFileContent(id)
    }
}
