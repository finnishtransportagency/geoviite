package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout/reference-lines")
class ReferenceLineController(private val trackNumberService: LayoutTrackNumberService) {

    //    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    //    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}")
    //    fun getReferenceLine(
    //        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
    //        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
    //        @PathVariable("id") id: IntId<ReferenceLine>,
    //    ): ResponseEntity<ReferenceLine> {
    //        val layoutContext = LayoutContext.of(branch, publicationState)
    //        return toResponse(referenceLineService.get(layoutContext, id))
    //    }
    //
    //    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    //    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["ids"])
    //    fun getReferenceLines(
    //        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
    //        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
    //        @RequestParam("ids", required = true) ids: List<IntId<ReferenceLine>>,
    //    ): List<ReferenceLine> {
    //        val layoutContext = LayoutContext.of(branch, publicationState)
    //        return referenceLineService.getMany(layoutContext, ids)
    //    }
    //
    //    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    //    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/by-track-number/{trackNumberId}")
    //    fun getTrackNumberReferenceLine(
    //        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
    //        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
    //        @PathVariable("trackNumberId") trackNumberId: IntId<LayoutTrackNumber>,
    //    ): ResponseEntity<ReferenceLine> {
    //        val layoutContext = LayoutContext.of(branch, publicationState)
    //        return toResponse(referenceLineService.getByTrackNumber(layoutContext, trackNumberId))
    //    }

    // TODO: GVT-3637 Move to track number controller
    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["bbox"])
    fun getTrackNumbersNear(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LayoutTrackNumber> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return trackNumberService.listNear(layoutContext, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/start-and-end")
    fun getReferenceLineStartAndEnd(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutTrackNumber>,
    ): ResponseEntity<AlignmentStartAndEnd<LayoutTrackNumber>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(trackNumberService.getStartAndEnd(layoutContext, id))
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/draft/non-linked")
    fun getNonLinkedReferenceLines(@PathVariable(LAYOUT_BRANCH) branch: LayoutBranch): List<LayoutTrackNumber> {
        return trackNumberService.listNonLinked(branch)
    }

    //    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    //    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/change-info")
    //    fun getReferenceLineChangeInfo(
    //        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
    //        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
    //        @PathVariable("id") id: IntId<ReferenceLine>,
    //    ): ResponseEntity<LayoutAssetChangeInfo> {
    //        val layoutContext = LayoutContext.of(branch, publicationState)
    //        return toResponse(referenceLineService.getLayoutAssetChangeInfo(layoutContext, id))
    //    }
    //
    //    @PreAuthorize(AUTH_EDIT_LAYOUT)
    //    @PostMapping("/{$LAYOUT_BRANCH}/{id}/cancel")
    //    fun cancelReferenceLine(
    //        @PathVariable(LAYOUT_BRANCH) branch: DesignBranch,
    //        @PathVariable("id") id: IntId<ReferenceLine>,
    //    ): ResponseEntity<IntId<ReferenceLine>> = toResponse(referenceLineService.cancel(branch, id)?.id)
}
