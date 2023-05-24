package fi.fta.geoviite.infra.projektivelho

import PVDocument
import PVDocumentHeader
import PVDocumentStatus
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.inframodel.*
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PVDocumentService @Autowired constructor(
    private val pvDao: PVDao,
    private val infraModelService: InfraModelService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getDocumentHeaders(status: PVDocumentStatus?): List<PVDocumentHeader> {
        logger.serviceCall("getDocumentHeaders", "status" to status)
        return pvDao.getDocumentHeaders(status)
    }

    fun updateDocumentStatus(id: IntId<PVDocument>, status: PVDocumentStatus): IntId<PVDocument> {
        logger.serviceCall("updateDocumentStatus", "id" to id, "status" to status)
        return pvDao.updateFileStatus(id, status)
    }

    fun getFile(id: IntId<PVDocument>): InfraModelFile? {
        logger.serviceCall("getFile", "id" to id)
        return pvDao.getFileContent(id)
    }

    @Transactional
    fun importVelhoDocument(
        documentId: IntId<PVDocument>,
        overrides: OverrideParameters?,
        extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        logger.serviceCall(
            "importVelhoDocument",
            "documentId" to documentId,
            "overrides" to overrides,
            "extraInfo" to extraInfo,
        )
        val file = requireNotNull(getFile(documentId)) {
            "Velho document has no file to import: documentId=$documentId"
        }
        val id = infraModelService.saveInfraModel(file, overrides, extraInfo).id
        updateDocumentStatus(documentId, PVDocumentStatus.ACCEPTED)
        return id
    }

    fun validateVelhoDocument(documentId: IntId<PVDocument>, overrides: OverrideParameters?): ValidationResponse {
        logger.serviceCall("validateVelhoDocument", "documentId" to documentId, "overrides" to overrides)
        val file = getFile(documentId)
        return file?.let { f -> infraModelService.validateInfraModelFile(f, overrides) }
            ?: noFileValidationResponse(overrides)
    }
}
