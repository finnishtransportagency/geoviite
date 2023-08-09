package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.projektivelho.*
import fi.fta.geoviite.infra.util.*
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

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping
    fun saveInfraModel(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): InsertResponse {
        logger.apiCall(
            "saveInfraModel",
            "file.originalFilename" to file.originalFilename,
            "overrides" to overrides,
            "extraInfo" to extraInfo,
        )
        val id = infraModelService.saveInfraModel(file, overrides, extraInfo).id
        return InsertResponse("New plan inserted successfully", id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/validate")
    fun validateFile(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall("validateFile",
            "file.originalFilename" to file.originalFilename,
            "overrides" to overrides,
        )
        return infraModelService.validateInfraModelFile(file, overrides)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/{planId}/validate")
    fun validateGeometryPlan(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall("validateGeometryPlan", "planId" to planId, "overrides" to overrides)
        return infraModelService.validateGeometryPlan(planId, overrides)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/{planId}")
    fun updateInfraModel(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): GeometryPlan {
        logger.apiCall("updateInfraModel", "overrides" to overrides, "extraInfo" to extraInfo)
        return infraModelService.updateInfraModel(planId, overrides, extraInfo)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{id}/file", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadFile(@PathVariable("id") id: IntId<GeometryPlan>): ResponseEntity<ByteArray> {
        logger.apiCall("downloadFile", "id" to id)
        return geometryService.getPlanFile(id).let(::toFileDownloadResponse)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/projektivelho/documents")
    fun getPVDocumentHeaders(@RequestParam("status") status: PVDocumentStatus?): List<PVDocumentHeader> {
        logger.apiCall("getPVDocumentHeaders", "status" to status)
        return pvDocumentService.getDocumentHeaders(status)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/projektivelho/documents/{id}")
    fun getPVDocumentHeaders(@PathVariable id: IntId<PVDocument>): PVDocumentHeader {
        logger.apiCall("getPVDocumentHeader", "id" to id)
        return pvDocumentService.getDocumentHeader(id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/projektivelho/documents/count")
    fun getPVDocumentCounts(): PVDocumentCounts {
        logger.apiCall("getPVDocumentCounts")
        return pvDocumentService.getDocumentCounts()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/projektivelho/redirect/{oid}")
    fun getPVRedirect(@PathVariable("oid") oid: Oid<PVApiRedirect>): HttpsUrl {
        logger.apiCall("getPVRedirect")
        return pvDocumentService.getLink(oid)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/projektivelho/documents/{id}/status")
    fun updatePVDocumentStatus(
        @PathVariable("id") id: IntId<PVDocument>,
        @RequestBody status: PVDocumentStatus,
    ): IntId<PVDocument> {
        logger.apiCall("updatePVDocumentStatus", "id" to id, "status" to status)
        return pvDocumentService.updateDocumentStatus(id, status)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/projektivelho/documents/{documentId}/validate")
    fun validatePVDocument(
        @PathVariable("documentId") documentId: IntId<PVDocument>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall("validatePVDocument", "documentId" to documentId, "overrides" to overrides)
        return pvDocumentService.validatePVDocument(documentId, overrides)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/projektivelho/{documentId}", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadPVDocument(@PathVariable("documentId") documentId: IntId<PVDocument>): ResponseEntity<ByteArray> {
        logger.apiCall("downloadPVDocument",  "documentId" to documentId)
        return pvDocumentService.getFile(documentId)
            ?.let(::toFileDownloadResponse)
            ?: throw NoSuchEntityException(PVDocument::class, documentId)
    }
}
