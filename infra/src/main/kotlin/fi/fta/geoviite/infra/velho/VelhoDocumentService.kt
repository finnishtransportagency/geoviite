package fi.fta.geoviite.infra.velho

import VelhoDocument
import VelhoDocumentHeader
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

    fun getDocumentHeaders(status: FileStatus?): List<VelhoDocumentHeader> {
        logger.serviceCall("getDocumentHeaders", "status" to status)
        return velhoDao.getDocumentHeaders(status)
    }

    fun updateDocumentStatus(id: IntId<VelhoDocument>, status: FileStatus): IntId<VelhoDocument> {
        logger.serviceCall("updateDocumentStatus", "id" to id, "status" to status)
        return velhoDao.updateFileStatus(id, status)
    }

    fun getFile(id: IntId<VelhoDocument>): InfraModelFile? {
        logger.serviceCall("getFile", "id" to id)
        return velhoDao.getFileContent(id)
    }
}
