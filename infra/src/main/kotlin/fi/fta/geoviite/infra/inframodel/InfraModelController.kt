package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkedItems
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.projektivelho.*
import fi.fta.geoviite.infra.util.toFileDownloadResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@GeoviiteController("/inframodel")
class InfraModelController
@Autowired
constructor(
    private val infraModelService: InfraModelService,
    private val geometryService: GeometryService,
    private val pvDocumentService: PVDocumentService,
    private val localizationService: LocalizationService,
) {

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PostMapping
    fun saveInfraModel(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        return infraModelService.saveInfraModel(file, overrides, extraInfo).id
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PostMapping("/validate")
    fun validateFile(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        return infraModelService.validateInfraModelFile(file, overrides)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PostMapping("/{planId}/validate")
    fun validateGeometryPlan(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        return infraModelService.validateGeometryPlan(planId, overrides)
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PutMapping("/{planId}")
    fun updateInfraModel(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        return infraModelService.updateInfraModel(planId, overrides, extraInfo).id
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PutMapping("/{planId}/hidden")
    fun setInfraModelHidden(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestBody hidden: Boolean,
    ): IntId<GeometryPlan> {
        return geometryService.setPlanHidden(planId, hidden).id
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PutMapping("/{planId}/applicability")
    fun setInfraModelApplicability(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestBody applicability: PlanApplicability?,
    ): IntId<GeometryPlan> {
        return geometryService.setPlanApplicability(planId, applicability).id
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PutMapping("/{planId}/linked-items")
    fun getInfraModelLinkedItems(@PathVariable("planId") planId: IntId<GeometryPlan>): GeometryPlanLinkedItems {
        return geometryService.getPlanLinkedItems(planId)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("{id}/file", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadFile(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam(name = "lang", defaultValue = "fi") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val translation = localizationService.getLocalization(lang)

        return geometryService.getPlanFile(id, translation).let(::toFileDownloadResponse)
    }

    @PreAuthorize(AUTH_VIEW_PV_DOCUMENTS)
    @GetMapping("/projektivelho/documents")
    fun getPVDocumentHeaders(@RequestParam("status") status: PVDocumentStatus?): List<PVDocumentHeader> {
        return pvDocumentService.getDocumentHeaders(status)
    }

    @PreAuthorize(AUTH_VIEW_PV_DOCUMENTS)
    @GetMapping("/projektivelho/documents/{id}")
    fun getPVDocumentHeaders(@PathVariable id: IntId<PVDocument>): PVDocumentHeader {
        return pvDocumentService.getDocumentHeader(id)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/projektivelho/documents/count")
    fun getPVDocumentCounts(): PVDocumentCounts {
        return pvDocumentService.getDocumentCounts()
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PutMapping("/projektivelho/documents/{ids}/status")
    fun updatePVDocumentsStatuses(
        @PathVariable("ids") ids: List<IntId<PVDocument>>,
        @RequestBody status: PVDocumentStatus,
    ): List<IntId<PVDocument>> {
        return pvDocumentService.updateDocumentsStatuses(ids, status)
    }

    @PreAuthorize(AUTH_VIEW_PV_DOCUMENTS)
    @PostMapping("/projektivelho/documents/{documentId}/validate")
    fun validatePVDocument(
        @PathVariable("documentId") documentId: IntId<PVDocument>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
    ): ValidationResponse {
        return pvDocumentService.validatePVDocument(documentId, overrides)
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PostMapping("/projektivelho/documents/{documentId}")
    fun importPVDocument(
        @PathVariable("documentId") documentId: IntId<PVDocument>,
        @RequestPart(value = "override-parameters") overrides: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfo: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        return pvDocumentService.importPVDocument(documentId, overrides, extraInfo)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/projektivelho/{documentId}", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadPVDocument(@PathVariable("documentId") documentId: IntId<PVDocument>): ResponseEntity<ByteArray> {
        return pvDocumentService.getFile(documentId)?.let(::toFileDownloadResponse)
            ?: throw NoSuchEntityException(PVDocument::class, documentId)
    }
}
