package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_DOWNLOAD_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationValidationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.publication.draftTransitionOrOfficialState
import fi.fta.geoviite.infra.util.getCsvResponseEntity
import fi.fta.geoviite.infra.util.toResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout/track-numbers")
class LayoutTrackNumberController(
    private val trackNumberService: LayoutTrackNumberService,
    private val publicationValidationService: PublicationValidationService,
    private val localizationService: LocalizationService,
) {

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}")
    fun getTrackNumbers(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("includeDeleted", defaultValue = "false") includeDeleted: Boolean,
    ): List<LayoutTrackNumber> {
        val context = LayoutContext.of(branch, publicationState)
        return trackNumberService.list(context, includeDeleted)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}")
    fun getTrackNumber(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
    ): ResponseEntity<LayoutTrackNumber> {
        val context = LayoutContext.of(branch, publicationState)
        return toResponse(trackNumberService.get(context, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/validation")
    fun validateTrackNumberAndReferenceLine(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
    ): ResponseEntity<ValidatedAsset<LayoutTrackNumber>> {
        return publicationValidationService
            .validateTrackNumbersAndReferenceLines(draftTransitionOrOfficialState(publicationState, branch), listOf(id))
            .firstOrNull()
            .let(::toResponse)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/draft")
    fun insertTrackNumber(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody saveRequest: TrackNumberSaveRequest,
    ): IntId<LayoutTrackNumber> {
        return trackNumberService.insert(branch, saveRequest).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{$LAYOUT_BRANCH}/draft/{id}")
    fun updateTrackNumber(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable id: IntId<LayoutTrackNumber>,
        @RequestBody saveRequest: TrackNumberSaveRequest,
    ): IntId<LayoutTrackNumber> {
        return trackNumberService.update(branch, id, saveRequest).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/{$LAYOUT_BRANCH}/draft/{id}")
    fun deleteDraftTrackNumber(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
    ): IntId<LayoutTrackNumber> {
        return trackNumberService.deleteDraftAndReferenceLine(branch, id)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/{id}/cancel")
    fun cancelTrackNumber(
        @PathVariable(LAYOUT_BRANCH) branch: DesignBranch,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
    ): ResponseEntity<IntId<LayoutTrackNumber>> = toResponse(trackNumberService.cancel(branch, id)?.id)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/plan-geometry")
    fun getTrackSectionsByPlan(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
        @RequestParam("bbox") boundingBox: BoundingBox? = null,
    ): List<AlignmentPlanSection> {
        val context = LayoutContext.of(branch, publicationState)
        return trackNumberService.getMetadataSections(context, id, boundingBox)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/km-lengths")
    fun getTrackNumberKmLengths(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
    ): List<LayoutKmLengthDetails> {
        val context = LayoutContext.of(branch, publicationState)
        return trackNumberService.getKmLengths(context, id) ?: emptyList()
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/km-lengths/as-csv")
    fun getTrackNumberKmLengthsAsCsv(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
        @RequestParam("startKmNumber") startKmNumber: KmNumber? = null,
        @RequestParam("endKmNumber") endKmNumber: KmNumber? = null,
        @RequestParam("precision") precision: KmLengthsLocationPrecision,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val context = LayoutContext.of(branch, publicationState)

        val csv =
            trackNumberService.getKmLengthsAsCsv(
                layoutContext = context,
                trackNumberId = id,
                startKmNumber = startKmNumber,
                endKmNumber = endKmNumber,
                precision = precision,
                lang = lang,
            )

        val trackNumber = trackNumberService.getOrThrow(context, id)

        val fileName =
            localizationService.getLocalization(lang).let { translation ->
                val params = localizationParams("trackNumber" to trackNumber.number)

                when (precision) {
                    KmLengthsLocationPrecision.PRECISE_LOCATION ->
                        translation.filename("km-lengths-csv-precise", params)

                    KmLengthsLocationPrecision.APPROXIMATION_IN_LAYOUT ->
                        translation.filename("km-lengths-csv-approximation", params)
                }
            }

        return getCsvResponseEntity(csv, fileName)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/rail-network/km-lengths/file")
    fun getEntireRailNetworkKmLengthsAsCsv(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam(name = "lang", defaultValue = "fi") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val layoutContext = LayoutContext.of(branch, publicationState)

        val csv =
            trackNumberService.getAllKmLengthsAsCsv(
                layoutContext = layoutContext,
                trackNumberIds = trackNumberService.list(layoutContext).map { tn -> tn.id as IntId },
                lang = lang,
            )

        val localization = localizationService.getLocalization(lang)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

        val filename =
            localization.filename(
                "km-lengths-entire-track-network",
                localizationParams("date" to dateFormatter.format(Instant.now())),
            )

        return getCsvResponseEntity(csv, filename)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/change-info")
    fun getTrackNumberChangeInfo(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        val context = LayoutContext.of(branch, publicationState)
        return toResponse(trackNumberService.getLayoutAssetChangeInfo(context, id))
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/{id}/oids")
    fun getTrackNumberOids(
        @PathVariable("id") id: IntId<LayoutTrackNumber>
    ): Map<LayoutBranch, Oid<LayoutTrackNumber>> {
        return trackNumberService.getExternalIdsByBranch(id)
    }
}
