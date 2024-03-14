package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkedItems
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.projektivelho.*
import fi.fta.geoviite.infra.util.toFileDownloadResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/inframodel")
class InfraModelController @Autowired constructor(
    private val infraModelService: InfraModelService,
    private val geometryService: GeometryService,
    private val pvDocumentService: PVDocumentService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_EDIT_GEOMETRY)
    @PostMapping
    fun saveInfraModel(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        logger.apiCall(
            "saveInfraModel",
            "file.originalFilename" to file.originalFilename,
            "overrides" to overrides,
            "extraInfo" to extraInfo,
        )
        return infraModelService.saveInfraModel(file, overrides, extraInfo).id
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PostMapping("/validate")
    fun validateFile(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall(
            "validateFile",
            "file.originalFilename" to file.originalFilename,
            "overrides" to overrides,
        )
        return infraModelService.validateInfraModelFile(file, overrides)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PostMapping("/{planId}/validate")
    fun validateGeometryPlan(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall("validateGeometryPlan", "planId" to planId, "overrides" to overrides)
        return infraModelService.validateGeometryPlan(planId, overrides)
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY)
    @PutMapping("/{planId}")
    fun updateInfraModel(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        logger.apiCall("updateInfraModel", "overrides" to overrides, "extraInfo" to extraInfo)
        return infraModelService.updateInfraModel(planId, overrides, extraInfo).id
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY)
    @PutMapping("/{planId}/hidden")
    fun setInfraModelHidden(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestBody hidden: Boolean,
    ): IntId<GeometryPlan> {
        logger.apiCall("setInfraModelHidden", "planId" to planId, "hidden" to hidden)
        return geometryService.setPlanHidden(planId, hidden).id
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PutMapping("/{planId}/linked-items")
    fun getInfraModelLinkedItems(@PathVariable("planId") planId: IntId<GeometryPlan>): GeometryPlanLinkedItems {
        logger.apiCall("getInfraModelLinkedItems", "planId" to planId)
        return geometryService.getPlanLinkedItems(planId)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("{id}/file", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadFile(@PathVariable("id") id: IntId<GeometryPlan>): ResponseEntity<ByteArray> {
        logger.apiCall("downloadFile", "id" to id)
        return geometryService.getPlanFile(id).let(::toFileDownloadResponse)
    }

    @PreAuthorize(AUTH_VIEW_PV_DOCUMENTS)
    @GetMapping("/projektivelho/documents")
    fun getPVDocumentHeaders(@RequestParam("status") status: PVDocumentStatus?): List<PVDocumentHeader> {
        logger.apiCall("getPVDocumentHeaders", "status" to status)
        return pvDocumentService.getDocumentHeaders(status)
    }

    @PreAuthorize(AUTH_VIEW_PV_DOCUMENTS)
    @GetMapping("/projektivelho/documents/{id}")
    fun getPVDocumentHeaders(@PathVariable id: IntId<PVDocument>): PVDocumentHeader {
        logger.apiCall("getPVDocumentHeader", "id" to id)
        return pvDocumentService.getDocumentHeader(id)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/projektivelho/documents/count")
    fun getPVDocumentCounts(): PVDocumentCounts {
        logger.apiCall("getPVDocumentCounts")
        return pvDocumentService.getDocumentCounts()
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY)
    @PutMapping("/projektivelho/documents/{ids}/status")
    fun updatePVDocumentsStatuses(
        @PathVariable("ids") ids: List<IntId<PVDocument>>,
        @RequestBody status: PVDocumentStatus,
    ): List<IntId<PVDocument>> {
        logger.apiCall("updatePVDocumentsStatuses", "ids" to ids, "status" to status)
        return pvDocumentService.updateDocumentsStatuses(ids, status)
    }

    @PreAuthorize(AUTH_VIEW_PV_DOCUMENTS)
    @PostMapping("/projektivelho/documents/{documentId}/validate")
    fun validatePVDocument(
        @PathVariable("documentId") documentId: IntId<PVDocument>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall("validatePVDocument", "documentId" to documentId, "overrides" to overrides)
        return pvDocumentService.validatePVDocument(documentId, overrides)
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY)
    @PostMapping("/projektivelho/documents/{documentId}")
    fun importPVDocument(
        @PathVariable("documentId") documentId: IntId<PVDocument>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        logger.apiCall(
            "importPVDocument",
            "documentId" to documentId,
            "overrides" to overrides,
            "extraInfo" to extraInfo,
        )
        return pvDocumentService.importPVDocument(documentId, overrides, extraInfo)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/projektivelho/{documentId}", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadPVDocument(@PathVariable("documentId") documentId: IntId<PVDocument>): ResponseEntity<ByteArray> {
        logger.apiCall("downloadPVDocument", "documentId" to documentId)
        return pvDocumentService.getFile(documentId)
            ?.let(::toFileDownloadResponse)
            ?: throw NoSuchEntityException(PVDocument::class, documentId)
    }

}
