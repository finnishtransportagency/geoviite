package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/linking")
class LinkingController @Autowired constructor(
    private val linkingService: LinkingService,
    private val switchLinkingService: SwitchLinkingService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/reference-lines/geometry")
    fun saveReferenceLineLinking(
        @RequestBody linkingParameters: LinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        logger.apiCall("saveReferenceLineLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveReferenceLineLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/location-tracks/geometry")
    fun saveLocationTrackLinking(
        @RequestBody linkingParameters: LinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        logger.apiCall("saveLocationTrackLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveLocationTrackLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/reference-lines/empty-geometry")
    fun saveEmptyReferenceLineLinking(
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        logger.apiCall("saveEmptyReferenceLineLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveReferenceLineLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/location-tracks/empty-geometry")
    fun saveEmptyLocationTrackLinking(
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        logger.apiCall("saveEmptyLocationTrackLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveLocationTrackLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/location-tracks/{id}/geometry")
    fun updateLocationTrackGeometry(
        @PathVariable("id") alignmentId: IntId<LocationTrack>,
        @RequestBody mRange: Range<Double>,
    ): IntId<LocationTrack> {
        logger.apiCall("updateLocationTrackGeometry", "alignmentId" to alignmentId, "mRange" to mRange)
        return linkingService.updateLocationTrackGeometry(alignmentId, mRange)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/reference-lines/{id}/geometry")
    fun updateReferenceLineGeometry(
        @PathVariable("id") alignmentId: IntId<ReferenceLine>,
        @RequestBody mRange: Range<Double>,
    ): IntId<ReferenceLine> {
        logger.apiCall("updateReferenceLineGeometry", "alignmentId" to alignmentId, "mRange" to mRange)
        return linkingService.updateReferenceLineGeometry(alignmentId, mRange)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("{$PUBLISH_TYPE}/plans/{id}/status")
    fun getPlanLinkStatus(
        @PathVariable("id") planId: IntId<GeometryPlan>,
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
    ): GeometryPlanLinkStatus {
        logger.apiCall("getPlanLinkStatus", "planId" to planId, "$PUBLISH_TYPE" to publishType)
        return linkingService.getGeometryPlanLinkStatus(planId = planId, publishType = publishType)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("{$PUBLISH_TYPE}/plans/status")
    fun getManyPlanLinkStatuses(
        @RequestParam("ids") planIds: List<IntId<GeometryPlan>>,
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
    ): List<GeometryPlanLinkStatus> {
        logger.apiCall("getManyPlanLinkStatuses", "$PUBLISH_TYPE" to publishType)
        return linkingService.getGeometryPlanLinkStatuses(planIds = planIds, publishType = publishType)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/location-tracks/suggested")
    fun getSuggestedConnectedLocationTracks(
        @RequestParam("id") locationTrackId: IntId<LocationTrack>,
        @RequestParam("location") location: Point,
        @RequestParam("locationTrackPointUpdateType") locationTrackPointUpdateType: LocationTrackPointUpdateType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrack> {
        logger.apiCall(
            "getSuggestedConnectedLocationTracks",
            "locationTrackId" to locationTrackId,
            "location" to location,
            "locationTrackPointUpdateType" to locationTrackPointUpdateType,
            "bbox" to bbox,
        )
        return linkingService.getSuggestedAlignments(
            locationTrackId,
            location,
            locationTrackPointUpdateType,
            bbox,
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/switches/suggested", params = ["bbox"])
    fun getSuggestedSwitches(@RequestParam("bbox") bbox: BoundingBox): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitches", "bbox" to bbox)
        return switchLinkingService.getSuggestedSwitches(bbox)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/switches/suggested", params = ["location", "switchId"])
    fun getSuggestedSwitches(
        @RequestParam("location") location: Point,
        @RequestParam("switchId") switchId: IntId<TrackLayoutSwitch>,
    ): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitches", "location" to location, "switchId" to switchId)
        return listOfNotNull(switchLinkingService.getSuggestedSwitch(location, switchId))
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/switches/suggested")
    fun getSuggestedSwitch(@RequestBody createParams: SuggestedSwitchCreateParams): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitch", "createParams" to createParams)
        return listOfNotNull(switchLinkingService.getSuggestedSwitch(createParams))
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/switches/{switchId}/geometry")
    fun saveSwitchLinking(@RequestBody suggestedSwitch: SuggestedSwitch, @PathVariable switchId: IntId<TrackLayoutSwitch>): IntId<TrackLayoutSwitch> {
        logger.apiCall("saveSwitchLinking", "switchLinkingParameters" to suggestedSwitch, "switchId" to switchId)
        return switchLinkingService.saveSwitchLinking(suggestedSwitch, switchId).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/km-posts/geometry")
    fun saveKmPostLinking(@RequestBody linkingParameters: KmPostLinkingParameters): IntId<TrackLayoutKmPost> {
        logger.apiCall("saveKmPostLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveKmPostLinking(linkingParameters).id
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/validate-relinking-track/{id}")
    fun validateRelinkingTrack(@PathVariable("id") id: IntId<LocationTrack>): List<SwitchRelinkingValidationResult> {
        logger.apiCall("validateRelinkingTrack", "id" to id)
        return switchLinkingService.validateRelinkingTrack(id)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/relink-track-switches/{id}")
    fun relinkTrackSwitches(@PathVariable("id") id: IntId<LocationTrack>): List<TrackSwitchRelinkingResult> {
        logger.apiCall("relinkTrackSwitches", "id" to id)
        return switchLinkingService.relinkTrack(id)
    }
}
