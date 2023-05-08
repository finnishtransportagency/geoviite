package fi.fta.geoviite.infra.inframodel

import VelhoDocument
import VelhoDocumentHeader
import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.velho.FileStatus
import fi.fta.geoviite.infra.velho.VelhoDocumentService
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
    private val velhoDocumentService: VelhoDocumentService,
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
        val infraModelFileAndSource = geometryService.getPlanFile(id)
        return toFileDownloadResponse(
            fileNameWithSuffix(infraModelFileAndSource.name),
            infraModelFileAndSource.content.toByteArray(),
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/velho-import/documents")
    fun getVelhoDocumentHeaders(@RequestParam("status") status: FileStatus?): List<VelhoDocumentHeader> {
        logger.apiCall("getVelhoDocumentHeaders", "status" to status)
        return velhoDocumentService.getDocumentHeaders(status)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/velho-import/documents/{id}/status")
    fun updateVelhoFileStatus(
        @PathVariable("id") id: IntId<VelhoDocument>,
        @RequestBody status: FileStatus,
    ): IntId<VelhoDocument> {
        logger.apiCall("updateVelhoFileStatus", "id" to id, "status" to status)
        return velhoDocumentService.updateDocumentStatus(id, status)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/velho-import/{documentId}/validate")
    fun validateVelhoDocument(
        @PathVariable("documentId") documentId: IntId<VelhoDocument>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall("validateVelhoDocument", "documentId" to documentId, "overrides" to overrides)
        val file = velhoDocumentService.getFile(documentId)
        return file?.let { f -> infraModelService.validateInfraModelFile(f, overrides) }
            ?: noFileValidationResponse(overrides)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/velho-import/{documentId}")
    fun importVelhoDocument(
        documentId: IntId<VelhoDocument>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        logger.apiCall(
            "importVelhoDocument",
            "documentId" to documentId,
            "overrides" to overrides,
            "extraInfo" to extraInfo,
        )
        val file = requireNotNull(velhoDocumentService.getFile(documentId)) {
            "Velho document has no file to import: documentId=$documentId"
        }
        return infraModelService.saveInfraModel(file, overrides, extraInfo).id
    }
}

private fun fileNameWithSuffix(fileName: FileName): String =
    if (fileName.endsWith(".xml", true)) fileName.toString()
    else "$fileName.xml"
