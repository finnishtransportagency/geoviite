package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.EndPointType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.RowVersion
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
    private val locationTrackService: LocationTrackService,
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
    @PostMapping("/location-tracks")
    fun insertLocationTrack(
        @RequestBody request: LocationTrackSaveRequest,
    ): IntId<LocationTrack> {
        logger.apiCall("insertLocationTrack", "request" to request)
        return locationTrackService.insert(request).id
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/location-tracks/{id}")
    fun updateLocationTrack(
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestBody request: LocationTrackSaveRequest,
    ): IntId<LocationTrack> {
        logger.apiCall("updateLocationTrack", "locationTrackId" to locationTrackId, "request" to request)
        return locationTrackService.update(locationTrackId, request).id
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/location-tracks/{id}")
    fun deleteLocationTrack(
        @PathVariable("id") id: IntId<LocationTrack>,
    ): IntId<LocationTrack> {
        logger.apiCall("deleteLocationTrack", "id" to id)
        return locationTrackService.deleteUnpublishedDraft(id).id
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


    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/location-tracks/{id}/endpoint")
    fun updateEndPoint(
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestBody request: LocationTrackEndPointUpdateRequest,
    ): IntId<LocationTrack> {
        logger.apiCall(
            "updateEndPoint",
            "locationTrackId" to locationTrackId,
        )
        return linkingService.updateEndPoint(
            locationTrackId,
            EndPointType.ENDPOINT,
            request.updateType
        )
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/location-tracks/{id}/endpoint-location-track")
    fun updateEndPointConnectedLocationTrack(
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestBody request: LocationTrackEndPointConnectedUpdateRequest,
    ): IntId<LocationTrack> {
        logger.apiCall(
            "updateEndPointConnectedLocationTrack",
            "locationTrackId" to locationTrackId,
            "continuousLocationTrackId" to request.connectedLocationTrackId,
            "locationTrackPointUpdateType" to request.updateType,
        )

        return linkingService.updateEndPointLocationTrack(
            locationTrackId,
            request.connectedLocationTrackId,
            EndPointType.LOCATION_TRACK,
            request.updateType,
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/plans/{id}/status")
    fun getPlanLinkStatus(
        @PathVariable("id") planId: IntId<GeometryPlan>,
        @PathVariable("publishType") publishType: PublishType
    ): GeometryPlanLinkStatus {
        logger.apiCall(
            "getPlanLinkStatus",
            "planId" to planId,
            "publishType" to publishType
        )
        return linkingService.getGeometryPlanLinkStatus(planId = planId, publishType = publishType)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/switches/{id}")
    fun updateSwitch(
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
        @RequestBody switch: TrackLayoutSwitchSaveRequest,
    ): IntId<TrackLayoutSwitch> {
        logger.apiCall("updateSwitch", "switchId" to switchId, "switch" to switch)
        return switchLinkingService.updateSwitch(switchId, switch)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/switches")
    fun insertTrackLayoutSwitch(
        @RequestBody trackLayoutSwitch: TrackLayoutSwitchSaveRequest,
    ): IntId<TrackLayoutSwitch> {
        logger.apiCall("insertTrackLayoutSwitch", "trackLayoutSwitch" to trackLayoutSwitch)
        return switchLinkingService.insertSwitch(trackLayoutSwitch)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/suggested-alignments")
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
    @GetMapping("/suggested-switch", params = ["bbox"])
    fun getSuggestedSwitches(@RequestParam("bbox") bbox: BoundingBox): List<SuggestedSwitch> {
        logger.apiCall("getSuggestedSwitches", "bbox" to bbox)
        return switchLinkingService.getSuggestedSwitches(bbox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/suggested-switch", params = ["location", "switchStructureId"])
    fun getSuggestedSwitches(
        @RequestParam("location") location: Point,
        @RequestParam("switchStructureId") switchStructureId: IntId<SwitchStructure>
    ): List<SuggestedSwitch?> {
        logger.apiCall("getSuggestedSwitche", "location" to location, "switchStructureId" to switchStructureId)
        return listOf(switchLinkingService.getSuggestedSwitch(location, switchStructureId))
    }


    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/suggested-switch")
    fun getSuggestedSwitch(@RequestBody createParams: SuggestedSwitchCreateParams): List<SuggestedSwitch?> {
        logger.apiCall("getSuggestedSwitch", "createParams" to createParams)
        return listOf(switchLinkingService.getSuggestedSwitch(createParams))
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/switch-linking")
    fun saveSwitchLinking(
        @RequestBody linkingParameters: SwitchLinkingParameters,
    ): TrackLayoutSwitch {
        logger.apiCall("saveSwitchLinking", "linkingParameters" to linkingParameters)
        switchLinkingService.saveSwitchLinking(linkingParameters)
        return switchService.getDraft(linkingParameters.layoutSwitchId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/location-tracks/end-points")
    fun getLocationTrackAlignmentEndpoints(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrackEndpoint> {
        logger.apiCall("getLocationTrackAlignmentEndpoints", "bbox" to bbox)
        return linkingService.getLocationTrackEndpoints(bbox, publishType)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/switches/{id}")
    fun deleteDraftSwitch(@PathVariable("id") switchId: IntId<TrackLayoutSwitch>): IntId<TrackLayoutSwitch> {
        logger.apiCall("deleteDraftSwitch", "switchId" to switchId)
        return switchLinkingService.deleteDraftSwitch(switchId)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/km-post-linking")
    fun saveKmPostLinking(
        @RequestBody linkingParameters: KmPostLinkingParameters,
    ): TrackLayoutKmPost {
        logger.apiCall("saveKmPostLinking", "linkingParameters" to linkingParameters)
        linkingService.saveKmPostLinking(linkingParameters)
        return layoutKmPostService.getDraft(linkingParameters.layoutKmPostId)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/km-post")
    fun insertTrackLayoutKmPost(
        @RequestBody trackLayoutKmPost: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        logger.apiCall("insertTrackLayoutKmPost", "trackLayoutKmPost" to trackLayoutKmPost)
        return layoutKmPostService.insertKmPost(trackLayoutKmPost)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/km-posts/{id}")
    fun updateKmPost(
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @RequestBody kmPost: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        logger.apiCall("updateKmPost", "kmPostId" to kmPostId, "kmPost" to kmPost)
        return layoutKmPostService.updateKmPost(kmPostId, kmPost)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/km-posts/{id}")
    fun deleteDraftKmPost(@PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>): IntId<TrackLayoutKmPost> {
        logger.apiCall("deleteDraftKmPost", "kmPostId" to kmPostId)
        return layoutKmPostService.deleteDraft(kmPostId)
    }
}
