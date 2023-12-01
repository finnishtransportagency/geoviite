package fi.fta.geoviite.infra.projektivelho

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.inframodel.*
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

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
        return pvDao.updateDocumentsStatuses(listOf(id), status).first()
    }

    fun updateDocumentsStatuses(ids: List<IntId<PVDocument>>, status: PVDocumentStatus): List<IntId<PVDocument>> {
        logger.serviceCall("updateDocumentsStatuses", "ids" to ids, "status" to status)
        return pvDao.updateDocumentsStatuses(ids, status)
    }

    fun getFile(id: IntId<PVDocument>): InfraModelFile? {
        logger.serviceCall("getFile", "id" to id)
        return pvDao.getFileContent(id)
    }

    @Transactional
    fun importPVDocument(
        documentId: IntId<PVDocument>,
        overrides: OverrideParameters?,
        extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        logger.serviceCall(
            "importPVDocument",
            "documentId" to documentId,
            "overrides" to overrides,
            "extraInfo" to extraInfo,
        )
        val file = requireNotNull(getFile(documentId)) {
            "ProjektiVelho document has no file to import: documentId=$documentId"
        }
        val id = infraModelService.saveInfraModel(file, overrides, extraInfo).id
        updateDocumentStatus(documentId, PVDocumentStatus.ACCEPTED)
        return id
    }

    fun validatePVDocument(documentId: IntId<PVDocument>, overrides: OverrideParameters?): ValidationResponse {
        logger.serviceCall("validatePVDocument", "documentId" to documentId, "overrides" to overrides)
        val file = getFile(documentId)
        return file
            ?.let { f -> infraModelService.validateInfraModelFile(f, overrides) }
            ?.let { r -> r.copy(geometryPlan = r.geometryPlan?.copy(pvDocumentId = documentId)) }
            ?: noFileValidationResponse(overrides)
    }

    fun getDocumentCounts(): PVDocumentCounts {
        logger.serviceCall("getDocumentCounts")
        return pvDao.getDocumentCounts()
    }

    fun getDocumentChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return pvDao.fetchDocumentChangeTime()
    }

    fun getDocumentHeader(id: IntId<PVDocument>): PVDocumentHeader {
        logger.serviceCall("getDocumentHeader")
        return pvDao.getDocumentHeader(id)
    }
}
