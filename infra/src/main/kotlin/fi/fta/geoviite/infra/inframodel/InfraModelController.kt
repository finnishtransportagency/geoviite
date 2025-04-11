package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkedItems
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.projektivelho.*
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.GvtZipEntry
import fi.fta.geoviite.infra.util.toFileDownloadResponse
import fi.fta.geoviite.infra.util.zip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

private val ZIP_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@GeoviiteController("/inframodel")
class InfraModelController
@Autowired
constructor(
    private val infraModelService: InfraModelService,
    private val geometryService: GeometryService,
    private val pvDocumentService: PVDocumentService,
    private val localizationService: LocalizationService,
    private val locationTrackService: LocationTrackService,
    private val trackNumberService: LayoutTrackNumberService,
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
        return infraModelService.setPlanApplicability(planId, applicability).id
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

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/batch", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadFiles(
        @RequestParam("ids") ids: List<IntId<GeometryPlan>>,
        @RequestParam("trackNumberId") trackNumberId: IntId<LayoutTrackNumber>?,
        @RequestParam("locationTrackId") locationTrackId: IntId<LocationTrack>?,
        @RequestParam("startKm") startKmNumber: KmNumber?,
        @RequestParam("endKm") endKmNumber: KmNumber?,
        @RequestParam("applicability") applicability: PlanApplicability?,
        @RequestParam(name = "lang", defaultValue = "fi") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val translation = localizationService.getLocalization(lang)
        val localizationParams =
            getInfraModelBatchFilenameLocalizationParams(
                applicability,
                locationTrackId?.let { locationTrackService.get(MainLayoutContext.official, it) },
                trackNumberId?.let { trackNumberService.get(MainLayoutContext.official, it) },
                startKmNumber,
                endKmNumber,
                translation,
            )

        val folderName = translation.filename("geometries-folder", LocalizationParams.empty)
        val summaryFilename =
            translation.filename(
                key =
                    if (startKmNumber != null || endKmNumber != null) "geometry-plans-summary-with-km-range-csv"
                    else "geometry-plans-summary-csv",
                params = localizationParams,
            )
        val zipFileName =
            translation.filename(
                key =
                    if (startKmNumber != null || endKmNumber != null) "geometry-plans-with-km-range-zip"
                    else "geometry-plans-zip",
                params = localizationParams,
            )

        val headers = geometryService.getPlanHeaders { header -> ids.contains(header.id) }
        val files = geometryService.getPlanFiles(ids, translation).let(::deduplicateBatchFiles)
        val summaryFile = infraModelService.getInfraModelBatchSummary(headers, summaryFilename, translation)

        return files
            .map { (_, file) -> GvtZipEntry(folderName, file.name, file.content.toByteArray()) }
            .plus(GvtZipEntry(folder = null, summaryFile.first, summaryFile.second.toByteArray()))
            .let { batch -> toFileDownloadResponse(zipFileName, zip(batch)) }
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

private fun getInfraModelBatchFilenameLocalizationParams(
    applicability: PlanApplicability?,
    locationTrack: LocationTrack?,
    trackNumber: LayoutTrackNumber?,
    startKmNumber: KmNumber?,
    endKmNumber: KmNumber?,
    translation: Translation,
): LocalizationParams {
    val alignmentName = requireNotNull(locationTrack?.name?.toString() ?: trackNumber?.number?.toString())
    return LocalizationParams(
        mutableMapOf(
                "applicability" to translation.t("enum.PlanApplicability.${applicability?.name ?: "UNKNOWN"}"),
                "alignmentName" to alignmentName,
                "date" to LocalDate.now().format(ZIP_TIMESTAMP_FORMATTER),
            )
            .let { paramMap ->
                startKmNumber?.let { paramMap.put(START_KM_PARAM_KEY, it.toString()) }
                endKmNumber?.let { paramMap.put(END_KM_PARAM_KEY, it.toString()) }
                paramMap
            }
    )
}

private fun deduplicateBatchFiles(
    originalFiles: Map<IntId<GeometryPlan>, InfraModelFile>
): Map<IntId<GeometryPlan>, InfraModelFile> {
    val deduplicatedFiles = mutableMapOf<IntId<GeometryPlan>, InfraModelFile>()

    originalFiles.entries
        .groupBy { (_, file) -> file.name.toString().lowercase() }
        .forEach { (_, files) ->
            if (files.size == 1) {
                val (id, file) = files.single()
                deduplicatedFiles.set(id, file)
            } else {
                files.forEachIndexed { index, (id, file) ->
                    val beforeSuffix = file.name.toString().substringBeforeLast('.')
                    val suffix = file.name.toString().substringAfterLast('.')

                    val fileWithDeduplicatedName =
                        if (index == 0) {
                            file
                        } else {
                            file.copy(name = FileName("${beforeSuffix} ($index).$suffix"))
                        }
                    deduplicatedFiles.set(id, fileWithDeduplicatedName)
                }
            }
        }
    return deduplicatedFiles
}
