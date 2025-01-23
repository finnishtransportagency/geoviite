package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.aspects.GeoviiteController
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
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.switches.GeometrySwitchSuggestionResult
import fi.fta.geoviite.infra.linking.switches.SamplingGridPoints
import fi.fta.geoviite.infra.linking.switches.SuggestedSwitch
import fi.fta.geoviite.infra.linking.switches.SuggestedSwitchesAtGridPoints
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.linking.switches.SwitchPlacingRequest
import fi.fta.geoviite.infra.linking.switches.SwitchRelinkingValidationResult
import fi.fta.geoviite.infra.linking.switches.SwitchTrackRelinkingValidationService
import fi.fta.geoviite.infra.linking.switches.matchSamplingGridToQueryPoints
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/linking")
class LinkingController
@Autowired
constructor(
    private val linkingService: LinkingService,
    private val switchLinkingService: SwitchLinkingService,
    private val switchTrackRelinkingValidationService: SwitchTrackRelinkingValidationService,
) {

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/reference-lines/geometry")
    fun saveReferenceLineLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: LinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        return linkingService.saveReferenceLineLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/location-tracks/geometry")
    fun saveLocationTrackLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: LinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        return linkingService.saveLocationTrackLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/reference-lines/empty-geometry")
    fun saveEmptyReferenceLineLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        return linkingService.saveReferenceLineLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/location-tracks/empty-geometry")
    fun saveEmptyLocationTrackLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        return linkingService.saveLocationTrackLinking(branch, linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{$LAYOUT_BRANCH}/location-tracks/{id}/geometry")
    fun shortenLocationTrackGeometry(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") alignmentId: IntId<LocationTrack>,
        @RequestBody mRange: Range<Double>,
    ): IntId<LocationTrack> {
        return linkingService.shortenLocationTrackGeometry(branch, alignmentId, mRange)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{$LAYOUT_BRANCH}/reference-lines/{id}/geometry")
    fun shortenReferenceLineGeometry(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") alignmentId: IntId<ReferenceLine>,
        @RequestBody mRange: Range<Double>,
    ): IntId<ReferenceLine> {
        return linkingService.shortenReferenceLineGeometry(branch, alignmentId, mRange)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/plans/{id}/status")
    fun getPlanLinkStatus(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") planId: IntId<GeometryPlan>,
    ): GeometryPlanLinkStatus {
        val layoutContext = LayoutContext.of(branch, publicationState)
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
        return linkingService.getSuggestedAlignments(
            branch,
            locationTrackId,
            location,
            locationTrackPointUpdateType,
            bbox,
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/switches/suggested", params = ["geometrySwitchId"])
    fun getSuggestedSwitchForGeometrySwitch(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestParam("geometrySwitchId") geometrySwitchId: IntId<GeometrySwitch>,
    ): GeometrySwitchSuggestionResult = switchLinkingService.getSuggestedSwitch(branch, geometrySwitchId)

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/switches/suggested", params = ["location", "layoutSwitchId"])
    fun getSingleSuggestedSwitchForLayoutSwitchPlacing(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestParam("location") location: Point,
        @RequestParam("layoutSwitchId") layoutSwitchId: IntId<LayoutSwitch>,
    ): ResponseEntity<SuggestedSwitch> =
        toResponse(switchLinkingService.getSuggestedSwitch(branch, location, layoutSwitchId))

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/switches/suggested", params = ["points", "switchId"])
    fun getSuggestedSwitchesForLayoutSwitchPlacing(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestParam("points") points: List<Point>,
        @RequestParam("switchId") switchId: IntId<LayoutSwitch>,
    ): SuggestedSwitchesAtGridPoints {
        val suggestedSwitches =
            switchLinkingService
                .getSuggestedSwitches(branch, listOf(SwitchPlacingRequest(SamplingGridPoints(points), switchId)))
                .first()
        return matchSamplingGridToQueryPoints(suggestedSwitches, points)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/switches/{switchId}/geometry")
    fun saveSwitchLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable switchId: IntId<LayoutSwitch>,
        @RequestBody suggestedSwitch: SuggestedSwitch,
    ): IntId<LayoutSwitch> {
        return switchLinkingService.saveSwitchLinking(branch, suggestedSwitch, switchId).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/km-posts/geometry")
    fun saveKmPostLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody linkingParameters: KmPostLinkingParameters,
    ): IntId<LayoutKmPost> {
        return linkingService.saveKmPostLinking(branch, linkingParameters).id
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/location-tracks/{id}/validate-relinking")
    fun validateRelinkingTrack(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<SwitchRelinkingValidationResult> {
        return switchTrackRelinkingValidationService.validateRelinkingTrack(branch, id)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/location-tracks/{id}/relink-switches")
    fun relinkTrackSwitches(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<TrackSwitchRelinkingResult> {
        return switchLinkingService.relinkTrack(branch, id)
    }
}
