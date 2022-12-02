package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
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
    private val layoutKmPostService: LayoutKmPostService,
    private val switchService: LayoutSwitchService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/reference-lines/geometry")
    fun saveReferenceLineLinking(
        @RequestBody linkingParameters: LinkingParameters<ReferenceLine>,
    ): RowVersion<ReferenceLine> {
        logger.apiCall("saveReferenceLineLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveReferenceLineLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/location-tracks/geometry")
    fun saveLocationTrackLinking(
        @RequestBody linkingParameters: LinkingParameters<LocationTrack>,
    ): RowVersion<LocationTrack> {
        logger.apiCall("saveLocationTrackLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveLocationTrackLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/reference-lines/empty-geometry")
    fun saveEmptyReferenceLineLinking(
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<ReferenceLine>,
    ): RowVersion<ReferenceLine> {
        logger.apiCall("saveEmptyReferenceLineLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveReferenceLineLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/location-tracks/empty-geometry")
    fun saveEmptyLocationTrackLinking(
        @RequestBody linkingParameters: EmptyAlignmentLinkingParameters<LocationTrack>,
    ): RowVersion<LocationTrack> {
        logger.apiCall("saveEmptyLocationTrackLinking", "linkingParameters" to linkingParameters)
        return linkingService.saveLocationTrackLinking(linkingParameters)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/location-tracks/{id}/geometry")
    fun updateLocationTrackGeometry(
        @PathVariable("id") alignmentId: IntId<LocationTrack>,
        @RequestBody interval: LayoutInterval<LocationTrack>,
    ): RowVersion<LocationTrack> {
        logger.apiCall(
            "updateLocationTrackGeometry",
            "alignmentId" to alignmentId, "interval" to interval
        )
        return linkingService.updateLocationTrackGeometry(alignmentId, interval)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/reference-lines/{id}/geometry")
    fun updateReferenceLineGeometry(
        @PathVariable("id") alignmentId: IntId<ReferenceLine>,
        @RequestBody interval: LayoutInterval<ReferenceLine>,
    ): RowVersion<ReferenceLine> {
        logger.apiCall(
            "updateReferenceLineGeometry",
            "alignmentId" to alignmentId, "interval" to interval
        )
        return linkingService.updateReferenceLineGeometry(alignmentId, interval)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/plans/{id}/status")
    fun getPlanLinkStatus(
        @PathVariable("id") planId: IntId<GeometryPlan>,
        @PathVariable("publishType") publishType: PublishType
    ): GeometryPlanLinkStatus {
        logger.apiCall("getPlanLinkStatus", "planId" to planId, "publishType" to publishType)
        return linkingService.getGeometryPlanLinkStatus(planId = planId, publishType = publishType)
    }

    @PreAuthorize(AUTH_ALL_READ)
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/switches/suggested", params = ["bbox"])
    fun getSuggestedSwitches(@RequestParam("bbox") bbox: BoundingBox): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitches", "bbox" to bbox)
        return switchLinkingService.getSuggestedSwitches(bbox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/switches/suggested", params = ["location", "switchStructureId"])
    fun getSuggestedSwitches(
        @RequestParam("location") location: Point,
        @RequestParam("switchStructureId") switchStructureId: IntId<SwitchStructure>
    ): List<SuggestedSwitch?> {
        logger.apiCall("getSuggestedSwitche", "location" to location, "switchStructureId" to switchStructureId)
        return listOf(switchLinkingService.getSuggestedSwitch(location, switchStructureId))
    }


    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/switches/suggested")
    fun getSuggestedSwitch(@RequestBody createParams: SuggestedSwitchCreateParams): List<SuggestedSwitch?> {
        logger.apiCall("getSuggestedSwitch", "createParams" to createParams)
        return listOf(switchLinkingService.getSuggestedSwitch(createParams))
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/switches/geometry")
    fun saveSwitchLinking(@RequestBody linkingParameters: SwitchLinkingParameters): TrackLayoutSwitch {
        logger.apiCall("saveSwitchLinking", "linkingParameters" to linkingParameters)
        switchLinkingService.saveSwitchLinking(linkingParameters)
        return switchService.getDraft(linkingParameters.layoutSwitchId)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/km-posts/geometry")
    fun saveKmPostLinking(@RequestBody linkingParameters: KmPostLinkingParameters): TrackLayoutKmPost {
        logger.apiCall("saveKmPostLinking", "linkingParameters" to linkingParameters)
        linkingService.saveKmPostLinking(linkingParameters)
        return layoutKmPostService.getDraft(linkingParameters.layoutKmPostId)
    }
}
