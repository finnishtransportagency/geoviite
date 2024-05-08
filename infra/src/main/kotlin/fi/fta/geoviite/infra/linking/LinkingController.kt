package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/linking")
class LinkingController @Autowired constructor(
    private val linkingService: LinkingService,
    private val switchLinkingService: SwitchLinkingService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/reference-lines/geometry")
    fun saveReferenceLineLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: LinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        logger.apiCall("saveReferenceLineLinking", "branch" to branch, "linkingParameters" to linkingParameters)
        return linkingService.saveReferenceLineLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/location-tracks/geometry")
    fun saveLocationTrackLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: LinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        logger.apiCall("saveLocationTrackLinking", "branch" to branch, "linkingParameters" to linkingParameters)
        return linkingService.saveLocationTrackLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/reference-lines/empty-geometry")
    fun saveEmptyReferenceLineLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        logger.apiCall("saveEmptyReferenceLineLinking", "branch" to branch, "linkingParameters" to linkingParameters)
        return linkingService.saveReferenceLineLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/location-tracks/empty-geometry")
    fun saveEmptyLocationTrackLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        logger.apiCall("saveEmptyLocationTrackLinking", "branch" to branch, "linkingParameters" to linkingParameters)
        return linkingService.saveLocationTrackLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{$LAYOUT_BRANCH}/location-tracks/{id}/geometry")
    fun updateLocationTrackGeometry(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") alignmentId: IntId<LocationTrack>,
        @RequestBody mRange: Range<Double>,
    ): IntId<LocationTrack> {
        logger.apiCall(
            "updateLocationTrackGeometry",
            "branch" to branch,
            "alignmentId" to alignmentId,
            "mRange" to mRange,
        )
        return linkingService.updateLocationTrackGeometry(branch, alignmentId, mRange)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{$LAYOUT_BRANCH}/reference-lines/{id}/geometry")
    fun updateReferenceLineGeometry(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") alignmentId: IntId<ReferenceLine>,
        @RequestBody mRange: Range<Double>,
    ): IntId<ReferenceLine> {
        logger.apiCall(
            "updateReferenceLineGeometry",
            "branch" to branch,
            "alignmentId" to alignmentId,
            "mRange" to mRange,
        )
        return linkingService.updateReferenceLineGeometry(branch, alignmentId, mRange)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/plans/{id}/status")
    fun getPlanLinkStatus(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") planId: IntId<GeometryPlan>,
    ): GeometryPlanLinkStatus {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getPlanLinkStatus", "layoutContext" to layoutContext, "planId" to planId)
        return linkingService.getGeometryPlanLinkStatus(layoutContext, planId)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/plans/status")
    fun getManyPlanLinkStatuses(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids") planIds: List<IntId<GeometryPlan>>,
    ): List<GeometryPlanLinkStatus> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getManyPlanLinkStatuses", "layoutContext" to layoutContext)
        return linkingService.getGeometryPlanLinkStatuses(layoutContext, planIds)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/location-tracks/suggested")
    fun getSuggestedConnectedLocationTracks(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestParam("id") locationTrackId: IntId<LocationTrack>,
        @RequestParam("location") location: Point,
        @RequestParam("locationTrackPointUpdateType") locationTrackPointUpdateType: LocationTrackPointUpdateType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrack> {
        logger.apiCall(
            "getSuggestedConnectedLocationTracks",
            "branch" to branch,
            "branch" to branch,
            "locationTrackId" to locationTrackId,
            "location" to location,
            "locationTrackPointUpdateType" to locationTrackPointUpdateType,
            "bbox" to bbox,
        )
        return linkingService.getSuggestedAlignments(
            branch,
            locationTrackId,
            location,
            locationTrackPointUpdateType,
            bbox,
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/switches/suggested", params = ["bbox"])
    fun getSuggestedSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitches", "branch" to branch, "bbox" to bbox)
        return switchLinkingService.getSuggestedSwitches(branch, bbox)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/switches/suggested", params = ["location", "switchId"])
    fun getSuggestedSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestParam("location") location: Point,
        @RequestParam("switchId") switchId: IntId<TrackLayoutSwitch>,
    ): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitches", "branch" to branch, "location" to location, "switchId" to switchId)
        return listOfNotNull(switchLinkingService.getSuggestedSwitch(branch, location, switchId))
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/switches/suggested")
    fun getSuggestedSwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody createParams: SuggestedSwitchCreateParams,
    ): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitch", "branch" to branch, "createParams" to createParams)
        return listOfNotNull(switchLinkingService.getSuggestedSwitch(branch, createParams))
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/switches/{switchId}/geometry")
    fun saveSwitchLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable switchId: IntId<TrackLayoutSwitch>,
        @RequestBody suggestedSwitch: SuggestedSwitch,
    ): IntId<TrackLayoutSwitch> {
        logger.apiCall(
            "saveSwitchLinking",
            "branch" to branch,
            "switchLinkingParameters" to suggestedSwitch,
            "switchId" to switchId,
        )
        return switchLinkingService.saveSwitchLinking(branch, suggestedSwitch, switchId).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/km-posts/geometry")
    fun saveKmPostLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: KmPostLinkingParameters,
    ): IntId<TrackLayoutKmPost> {
        logger.apiCall("saveKmPostLinking", "branch" to branch, "linkingParameters" to linkingParameters)
        return linkingService.saveKmPostLinking(branch, linkingParameters).id
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/location-tracks/{id}/validate-relinking")
    fun validateRelinkingTrack(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<SwitchRelinkingValidationResult> {
        logger.apiCall("validateRelinkingTrack", "branch" to branch, "id" to id)
        return switchLinkingService.validateRelinkingTrack(branch, id)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/location-tracks/{id}/relink-switches")
    fun relinkTrackSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<TrackSwitchRelinkingResult> {
        logger.apiCall("relinkTrackSwitches", "branch" to branch, "id" to id)
        return switchLinkingService.relinkTrack(branch, id)
    }
}
