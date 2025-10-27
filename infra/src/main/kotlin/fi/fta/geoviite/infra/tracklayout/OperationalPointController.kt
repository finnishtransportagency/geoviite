package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.publication.PublicationValidationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout")
class OperationalPointController(
    private val operationalPointService: OperationalPointService,
    private val publicationValidationService: PublicationValidationService,
) {
    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/operational-points/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}")
    fun getSingleOperatingPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<OperationalPoint>,
    ): ResponseEntity<OperationalPoint> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return toResponse(operationalPointService.list(context, bbox = null, ids = listOf(id)).firstOrNull())
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/operational-points/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}")
    fun getOperatingPoints(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox", required = false) bbox: BoundingBox?,
        @RequestParam("ids", required = false) ids: List<IntId<OperationalPoint>>?,
    ): List<OperationalPoint> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return operationalPointService.list(context, bbox, ids)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/operational-points/internal/{$LAYOUT_BRANCH}/draft/{id}")
    fun updateInternalOperatingPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") id: IntId<OperationalPoint>,
        @RequestBody request: InternalOperationalPointSaveRequest,
    ): IntId<OperationalPoint> {
        return operationalPointService.update(layoutBranch, id, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/operational-points/internal/{$LAYOUT_BRANCH}/draft/{id}/location")
    fun updateOperatingPointLocation(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") id: IntId<OperationalPoint>,
        @RequestBody request: Point,
    ): IntId<OperationalPoint> {
        return operationalPointService.updateLocation(layoutBranch, id, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/operational-points/{$LAYOUT_BRANCH}/draft/{id}/location")
    fun updateOperatingPointPolygon(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") id: IntId<OperationalPoint>,
        @RequestBody request: Polygon,
    ): IntId<OperationalPoint> {
        return operationalPointService.updatePolygon(layoutBranch, id, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/operational-points/internal/{$LAYOUT_BRANCH}/draft")
    fun insertOperatingPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @RequestBody request: InternalOperationalPointSaveRequest,
    ): IntId<OperationalPoint> {
        return operationalPointService.insert(layoutBranch, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/operational-points/external/{$LAYOUT_BRANCH}/draft/{id}")
    fun updateExternalOperatingPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") id: IntId<OperationalPoint>,
        @RequestBody request: ExternalOperationalPointSaveRequest,
    ): IntId<OperationalPoint> {
        return operationalPointService.update(layoutBranch, id, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/operational-points/{$LAYOUT_BRANCH}/draft/{id}")
    fun deleteInternalOperatingPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") id: IntId<OperationalPoint>,
    ): IntId<OperationalPoint> {
        return operationalPointService.deleteDraft(layoutBranch, id).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/operational-points/{$LAYOUT_BRANCH}/{id}/cancel")
    fun cancelOperationalPoint(
        @PathVariable(LAYOUT_BRANCH) branch: DesignBranch,
        @PathVariable("id") id: IntId<OperationalPoint>,
    ): ResponseEntity<IntId<OperationalPoint>> = toResponse(operationalPointService.cancel(branch, id)?.id)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/operational-points/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/change-info")
    fun getOperationalPointChangeInfo(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<OperationalPoint>,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return toResponse(operationalPointService.getLayoutAssetChangeInfo(context, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/operational-points/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/validation")
    fun validateOperationalPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<OperationalPoint>,
    ): ResponseEntity<ValidatedAsset<OperationalPoint>> {
        return publicationValidationService
            .validateOperationalPoints(layoutBranch, publicationState, listOf(id))
            .firstOrNull()
            .let(::toResponse)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/operational-points/{id}/oids")
    fun getOperationalPointOids(
        @PathVariable("id") id: IntId<OperationalPoint>
    ): Map<LayoutBranch, Oid<OperationalPoint>> {
        return operationalPointService.getExternalIdsByBranch(id)
    }
}
