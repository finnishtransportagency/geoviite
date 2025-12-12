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
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.linking.switches.LayoutSwitchSaveRequest
import fi.fta.geoviite.infra.linking.switches.SwitchOidPresence
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
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

@GeoviiteController("/track-layout/switches")
class LayoutSwitchController(
    private val switchService: LayoutSwitchService,
    private val publicationValidationService: PublicationValidationService,
) {

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}")
    fun getSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox?,
        @RequestParam("namePart") namePart: String?,
        @RequestParam("exactName") exactName: SwitchName?,
        @RequestParam("offset") offset: Int?,
        @RequestParam("limit") limit: Int?,
        @RequestParam("comparisonPoint") comparisonPoint: Point?,
        @RequestParam("switchType") switchType: String?,
        @RequestParam("includeSwitchesWithNoJoints") includeSwitchesWithNoJoints: Boolean = false,
        @RequestParam("includeDeleted") includeDeleted: Boolean = false,
    ): List<LayoutSwitch> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        val filter = switchFilter(namePart, exactName, switchType, bbox, includeSwitchesWithNoJoints)
        val switches = switchService.listWithStructure(layoutContext, includeDeleted).filter(filter)
        return pageSwitches(switches, offset ?: 0, limit, comparisonPoint).items
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/{id}")
    fun getSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutSwitch>,
    ): ResponseEntity<LayoutSwitch> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(switchService.get(layoutContext, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}", params = ["ids"])
    fun getSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<LayoutSwitch>>,
    ): List<LayoutSwitch> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return switchService.getMany(layoutContext, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/{id}/joint-connections")
    fun getSwitchJointConnections(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LayoutSwitch>,
    ): List<LayoutSwitchJointConnection> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return switchService.getSwitchJointConnections(layoutContext, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/validation")
    fun validateSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<LayoutSwitch>>?,
        @RequestParam("bbox") bbox: BoundingBox?,
    ): List<ValidatedAsset<LayoutSwitch>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        val switches =
            if (ids != null) {
                switchService.getMany(layoutContext, ids)
            } else {
                switchService.list(layoutContext, false)
            }
        val switchIds =
            switches.filter { switch -> switchMatchesBbox(switch, bbox, false) }.map { sw -> sw.id as IntId }
        return publicationValidationService.validateSwitches(branch, publicationState, switchIds)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{${LAYOUT_BRANCH}}/draft")
    fun insertSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: LayoutSwitchSaveRequest,
    ): IntId<LayoutSwitch> {
        return switchService.insertSwitch(branch, request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{${LAYOUT_BRANCH}}/draft/{id}")
    fun updateSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") switchId: IntId<LayoutSwitch>,
        @RequestBody switch: LayoutSwitchSaveRequest,
    ): IntId<LayoutSwitch> {
        return switchService.updateSwitch(branch, switchId, switch)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/draft/unlink-from-operational-point/")
    fun unlinkSwitchesFromOperationalPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @RequestBody switchIds: List<IntId<LayoutSwitch>>,
    ): List<IntId<LayoutSwitch>> = switchService.unlinkFromOperationalPoint(layoutBranch, switchIds)

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/draft/link-to-operational-point/{operationalPointId}")
    fun linkSwitchesToOperationalPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("operationalPointId") operationalPointId: IntId<OperationalPoint>,
        @RequestBody switchIds: List<IntId<LayoutSwitch>>,
    ): List<IntId<LayoutSwitch>> = switchService.linkToOperationalPoint(layoutBranch, switchIds, operationalPointId)

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/{${LAYOUT_BRANCH}}/draft/{id}")
    fun deleteDraftSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") switchId: IntId<LayoutSwitch>,
    ): IntId<LayoutSwitch> {
        return switchService.deleteDraft(branch, switchId).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/{id}/cancel")
    fun cancelSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: DesignBranch,
        @PathVariable("id") id: IntId<LayoutSwitch>,
    ): ResponseEntity<IntId<LayoutSwitch>> = toResponse(switchService.cancel(branch, id)?.id)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/{id}/change-info")
    fun getSwitchChangeInfo(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") switchId: IntId<LayoutSwitch>,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(switchService.getLayoutAssetChangeInfo(layoutContext, switchId))
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/{id}/oids")
    fun getSwitchOids(@PathVariable("id") id: IntId<LayoutSwitch>): Map<LayoutBranch, Oid<LayoutSwitch>> {
        return switchService.getExternalIdsByBranch(id)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/oid_presence/{oid}")
    fun getSwitchOidPresence(@PathVariable("oid") oid: Oid<LayoutSwitch>): SwitchOidPresence =
        switchService.checkOidPresence(oid)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/by-operational-point/{operationalPointId}")
    fun findSwitchesWithinOperationalPointPolygon(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("operationalPointId") operationalPointId: IntId<OperationalPoint>,
    ): List<SwitchWithOperationalPointPolygonInclusions> =
        switchService.findSwitchesRelatedToOperationalPoint(
            LayoutContext.of(branch, publicationState),
            operationalPointId,
        )
}
