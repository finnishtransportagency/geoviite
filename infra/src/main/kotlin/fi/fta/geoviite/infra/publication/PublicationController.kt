package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_DOWNLOAD_PUBLICATION
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_PUBLICATION
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.DatabaseLock.PUBLICATION
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.util.Page
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.getCsvResponseEntity
import fi.fta.geoviite.infra.util.toResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

val publicationMaxDuration: Duration = Duration.ofMinutes(15)

@GeoviiteController("/publications")
class PublicationController
@Autowired
constructor(
    private val lockDao: LockDao,
    private val publicationService: PublicationService,
    private val publicationValidationService: PublicationValidationService,
    private val publicationLogService: PublicationLogService,
    private val calculatedChangesService: CalculatedChangesService,
    private val localizationService: LocalizationService,
) {

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/{from_state}/candidates")
    fun getPublicationCandidates(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("from_state") fromState: PublicationState,
    ): PublicationCandidates {
        return publicationService.collectPublicationCandidates(publicationInOrMergeFromBranch(branch, fromState))
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @PostMapping("/{$LAYOUT_BRANCH}/validate")
    fun validatePublicationCandidates(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: PublicationRequestIds,
    ): ValidatedPublicationCandidates {
        return publicationValidationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(LayoutContextTransition.publicationIn(branch)),
            request,
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @PostMapping("/{$LAYOUT_BRANCH}/validate-merge-to-main")
    fun validateMergeToMain(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: PublicationRequestIds,
    ): ValidatedPublicationCandidates {
        return publicationValidationService.validatePublicationCandidates(
            publicationService.collectPublicationCandidates(LayoutContextTransition.mergeToMainFrom(branch)),
            request,
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @PostMapping("/{$LAYOUT_BRANCH}/calculated-changes")
    fun getCalculatedChanges(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: PublicationRequestIds,
    ): CalculatedChanges {
        return calculatedChangesService.getCalculatedChanges(publicationService.getValidationVersions(branch, request))
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/{$LAYOUT_BRANCH}/candidates")
    fun revertPublicationCandidates(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody toDelete: PublicationRequestIds,
    ): PublicationResultSummary {
        return lockDao.runWithLock(PUBLICATION, publicationMaxDuration) {
            publicationService.revertPublicationCandidates(branch, toDelete)
        }
            ?: throw PublicationFailureException(
                message = "Could not reserve publication lock",
                localizedMessageKey = "lock-obtain-failed",
            )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @PostMapping("/{$LAYOUT_BRANCH}/candidates/revert-request-dependencies")
    fun getRevertRequestDependencies(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody toDelete: PublicationRequestIds,
    ): PublicationRequestIds {
        return publicationService.getRevertRequestDependencies(branch, toDelete)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}")
    fun publishChanges(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: PublicationRequest,
    ): PublicationResult {
        return lockDao.runWithLock(PUBLICATION, publicationMaxDuration) {
            publicationService.publishManualPublication(branch, request)
        }
            ?: throw PublicationFailureException(
                message = "Could not reserve publication lock",
                localizedMessageKey = "lock-obtain-failed",
            )
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/merge-to-main/{$LAYOUT_BRANCH}")
    fun mergeChangesToMain(
        @PathVariable(LAYOUT_BRANCH) branch: DesignBranch,
        @RequestBody request: PublicationRequestIds,
    ): PublicationResultSummary {
        return publicationService.mergeChangesToMain(branch, request)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping
    fun getPublicationsBetween(
        @RequestParam("layoutBranch", required = false) layoutBranch: LayoutBranch?,
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): Page<PublicationDetails> {
        val publications =
            publicationLogService.fetchPublicationDetailsBetweenInstants(layoutBranch ?: LayoutBranch.main, from, to)

        return Page(
            totalCount = publications.size,
            start = 0,
            items = publications.take(50), // Prevents frontend from going kaput
        )
    }

    @PreAuthorize(AUTH_VIEW_PUBLICATION)
    @GetMapping("latest/{branchType}")
    fun getLatestPublications(
        @PathVariable("branchType") branchType: LayoutBranchType,
        @RequestParam("count") count: Int,
    ): Page<PublicationDetails> = publicationLogService.fetchLatestPublicationDetails(branchType, count)

    @PreAuthorize(AUTH_DOWNLOAD_PUBLICATION)
    @GetMapping("csv")
    fun getPublicationsAsCsv(
        @RequestParam("layoutBranch", required = false) layoutBranch: LayoutBranch?,
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam("sortBy", required = false) sortBy: PublicationTableColumn?,
        @RequestParam("order", required = false) order: SortOrder?,
        @RequestParam("timeZone") timeZone: ZoneId?,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val translation = localizationService.getLocalization(lang)

        val publicationsAsCsv =
            publicationLogService.fetchPublicationsAsCsv(
                layoutBranch ?: LayoutBranch.main,
                from,
                to,
                sortBy,
                order,
                timeZone,
                translation,
            )

        val dateString = getDateStringForFileName(from, to, timeZone ?: ZoneId.of("UTC"))
        val fileName = translation.filename("publication-log", localizationParams("dateRange" to (dateString ?: "")))

        return getCsvResponseEntity(publicationsAsCsv, fileName)
    }

    @PreAuthorize(AUTH_VIEW_PUBLICATION)
    @GetMapping("/table-rows")
    fun getPublicationDetailsAsTableRows(
        @RequestParam("layoutBranch", required = false) layoutBranch: LayoutBranch?,
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam("sortBy", required = false) sortBy: PublicationTableColumn?,
        @RequestParam("order", required = false) order: SortOrder?,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): Page<PublicationTableItem> {
        val publications =
            publicationLogService.fetchPublicationDetails(
                layoutBranch = layoutBranch ?: LayoutBranch.main,
                from = from,
                to = to,
                sortBy = sortBy,
                order = order,
                translation = localizationService.getLocalization(lang),
            )

        return Page(
            totalCount = publications.size,
            start = 0,
            items = publications.take(500), // Prevents frontend from going kaput
        )
    }

    @PreAuthorize(AUTH_VIEW_PUBLICATION)
    @GetMapping("/{id}/table-rows")
    fun getPublicationDetailsAsTableRows(
        @PathVariable("id") id: IntId<Publication>,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): List<PublicationTableItem> {
        return publicationLogService.getPublicationDetailsAsTableItems(id, localizationService.getLocalization(lang))
    }

    @PreAuthorize(AUTH_VIEW_PUBLICATION)
    @GetMapping("/{id}")
    fun getPublicationDetails(@PathVariable("id") id: IntId<Publication>): PublicationDetails {
        return publicationLogService.getPublicationDetails(id)
    }

    @PreAuthorize(AUTH_VIEW_PUBLICATION)
    @GetMapping("/{id}/split-details")
    fun getSplitDetails(@PathVariable("id") id: IntId<Publication>): ResponseEntity<SplitInPublication> {
        return publicationLogService.getSplitInPublication(id).let(::toResponse)
    }

    @PreAuthorize(AUTH_DOWNLOAD_PUBLICATION)
    @GetMapping("/{id}/split-details/csv")
    fun getSplitDetailsAsCsv(
        @PathVariable("id") id: IntId<Publication>,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val translation = localizationService.getLocalization(lang)

        return publicationLogService.getSplitInPublicationCsv(id, lang).let { (csv, ltName) ->
            val filename = translation.filename("split-details", localizationParams("locationTrackName" to ltName))
            getCsvResponseEntity(csv, filename)
        }
    }
}
