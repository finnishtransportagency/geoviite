package fi.fta.geoviite.infra.projektivelho

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.inframodel.*
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class PVDocumentService
@Autowired
constructor(private val pvDao: PVDao, private val infraModelService: InfraModelService) {

    fun getDocumentHeaders(status: PVDocumentStatus?): List<PVDocumentHeader> {
        return pvDao.getDocumentHeaders(status)
    }

    fun updateDocumentStatus(id: IntId<PVDocument>, status: PVDocumentStatus): IntId<PVDocument> {
        return pvDao.updateDocumentsStatuses(listOf(id), status).first()
    }

    fun updateDocumentsStatuses(ids: List<IntId<PVDocument>>, status: PVDocumentStatus): List<IntId<PVDocument>> {
        return pvDao.updateDocumentsStatuses(ids, status)
    }

    fun getFile(id: IntId<PVDocument>): InfraModelFile? {
        return pvDao.getFileContent(id)
    }

    @Transactional
    fun importPVDocument(
        documentId: IntId<PVDocument>,
        overrides: OverrideParameters?,
        extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        val file =
            requireNotNull(getFile(documentId)) {
                "ProjektiVelho document has no file to import: documentId=$documentId"
            }
        val id = infraModelService.saveInfraModel(file, overrides, extraInfo).id
        updateDocumentStatus(documentId, PVDocumentStatus.ACCEPTED)
        return id
    }

    fun validatePVDocument(documentId: IntId<PVDocument>, overrides: OverrideParameters?): ValidationResponse {
        val file = getFile(documentId)
        return file
            ?.let { f -> infraModelService.validateInfraModelFile(f, overrides) }
            ?.let { r -> r.copy(geometryPlan = r.geometryPlan?.copy(pvDocumentId = documentId)) }
            ?: noFileValidationResponse(overrides)
    }

    fun getDocumentCounts(): PVDocumentCounts {
        return pvDao.getDocumentCounts()
    }

    fun getDocumentChangeTime(): Instant {
        return pvDao.fetchDocumentChangeTime()
    }

    fun getDocumentHeader(id: IntId<PVDocument>): PVDocumentHeader {
        return pvDao.getDocumentHeader(id)
    }
}
