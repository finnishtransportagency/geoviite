package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
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
    private val publicationService: PublicationService,
) {

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}")
    fun getTrackLayoutSwitches(
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
    ): List<TrackLayoutSwitch> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        val filter = switchFilter(namePart, exactName, switchType, bbox, includeSwitchesWithNoJoints)
        val switches = switchService.listWithStructure(layoutContext, includeDeleted).filter(filter)
        return pageSwitches(switches, offset ?: 0, limit, comparisonPoint).items
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/{id}")
    fun getTrackLayoutSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(switchService.get(layoutContext, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}", params = ["ids"])
    fun getTrackLayoutSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutSwitch>>,
    ): List<TrackLayoutSwitch> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return switchService.getMany(layoutContext, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/{id}/joint-connections")
    fun getSwitchJointConnections(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return switchService.getSwitchJointConnections(layoutContext, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/validation")
    fun validateSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<TrackLayoutSwitch>>?,
        @RequestParam("bbox") bbox: BoundingBox?,
    ): List<ValidatedAsset<TrackLayoutSwitch>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        val switches =
            if (ids != null) {
                switchService.getMany(layoutContext, ids)
            } else {
                switchService.list(layoutContext, false)
            }
        val switchIds =
            switches.filter { switch -> switchMatchesBbox(switch, bbox, false) }.map { sw -> sw.id as IntId }
        return publicationService.validateSwitches(layoutContext, switchIds)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{${LAYOUT_BRANCH}}/draft")
    fun insertTrackLayoutSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: TrackLayoutSwitchSaveRequest,
    ): IntId<TrackLayoutSwitch> {
        return switchService.insertSwitch(branch, request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{${LAYOUT_BRANCH}}/draft/{id}")
    fun updateSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
        @RequestBody switch: TrackLayoutSwitchSaveRequest,
    ): IntId<TrackLayoutSwitch> {
        return switchService.updateSwitch(branch, switchId, switch)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/{${LAYOUT_BRANCH}}/draft/{id}")
    fun deleteDraftSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
    ): IntId<TrackLayoutSwitch> {
        return switchService.deleteDraft(branch, switchId).id
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{${LAYOUT_BRANCH}}/{$PUBLICATION_STATE}/{id}/change-info")
    fun getSwitchChangeInfo(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(switchService.getLayoutAssetChangeInfo(layoutContext, switchId))
    }
}
