package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.authorization.AUTH_UI_READ
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout/switches")
class LayoutSwitchController(
    private val switchService: LayoutSwitchService,
    private val publicationService: PublicationService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{publishType}")
    fun getTrackLayoutSwitches(
        @PathVariable("publishType") publishType: PublishType,
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
        logger.apiCall(
            "getTrackLayoutSwitches",
            "publishType" to publishType,
            "bbox" to bbox,
            "namePart" to namePart,
            "exactName" to exactName,
            "offset" to offset,
            "limit" to limit,
            "comparisonPoint" to comparisonPoint,
            "switchType" to switchType,
            "includeDeleted" to includeDeleted,
        )
        val filter = switchFilter(namePart, exactName, switchType, bbox, includeSwitchesWithNoJoints)
        val switches = switchService.listWithStructure(publishType, includeDeleted).filter(filter)
        return pageSwitches(switches, offset ?: 0, limit, comparisonPoint).items
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{publishType}/{id}")
    fun getTrackLayoutSwitch(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitch", "id" to id, "publishType" to publishType)
        return toResponse(switchService.get(publishType, id))
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{publishType}", params = ["ids"])
    fun getTrackLayoutSwitches(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutSwitch>>,
    ): List<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitches", "ids" to ids, "publishType" to publishType)
        return switchService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{publishType}/{id}/joint-connections")
    fun getSwitchJointConnections(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        logger.apiCall("getSwitchJointConnections", "switchId" to id, "publishType" to publishType)
        return switchService.getSwitchJointConnections(publishType, id)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("{publishType}/validation")
    fun validateSwitches(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids") ids: List<IntId<TrackLayoutSwitch>>,
    ): List<ValidatedAsset<TrackLayoutSwitch>> {
        logger.apiCall("validateSwitches", "publishType" to publishType, "ids" to ids)
        return publicationService.validateSwitches(ids, publishType)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/draft")
    fun insertTrackLayoutSwitch(@RequestBody request: TrackLayoutSwitchSaveRequest): IntId<TrackLayoutSwitch> {
        logger.apiCall("insertTrackLayoutSwitch", "request" to request)
        return switchService.insertSwitch(request)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/draft/{id}")
    fun updateSwitch(
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
        @RequestBody switch: TrackLayoutSwitchSaveRequest,
    ): IntId<TrackLayoutSwitch> {
        logger.apiCall("updateSwitch", "switchId" to switchId, "switch" to switch)
        return switchService.updateSwitch(switchId, switch)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/draft/{id}")
    fun deleteDraftSwitch(@PathVariable("id") switchId: IntId<TrackLayoutSwitch>): IntId<TrackLayoutSwitch> {
        logger.apiCall("deleteDraftSwitch", "switchId" to switchId)
        return switchService.deleteDraft(switchId).id
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{publishType}/{id}/change-times")
    fun getSwitchChangeInfo(
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
        @PathVariable("publishType") publishType: PublishType,
    ): ResponseEntity<DraftableChangeInfo> {
        logger.apiCall("getSwitchChangeTimes", "id" to switchId)
        return toResponse(switchService.getDraftableChangeInfo(switchId, publishType))
    }
}
