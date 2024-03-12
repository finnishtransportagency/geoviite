package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE
import fi.fta.geoviite.infra.authorization.PUBLISH_TYPE
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}")
    fun getTrackLayoutSwitches(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
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
            "$PUBLISH_TYPE" to publishType,
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}")
    fun getTrackLayoutSwitch(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitch", "id" to id, "$PUBLISH_TYPE" to publishType)
        return toResponse(switchService.get(publishType, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["ids"])
    fun getTrackLayoutSwitches(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutSwitch>>,
    ): List<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitches", "ids" to ids, "$PUBLISH_TYPE" to publishType)
        return switchService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/joint-connections")
    fun getSwitchJointConnections(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        logger.apiCall("getSwitchJointConnections", "switchId" to id, "$PUBLISH_TYPE" to publishType)
        return switchService.getSwitchJointConnections(publishType, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("{$PUBLISH_TYPE}/validation")
    fun validateSwitches(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("ids") ids: List<IntId<TrackLayoutSwitch>>?,
        @RequestParam("bbox") bbox: BoundingBox?,
    ): List<ValidatedAsset<TrackLayoutSwitch>> {
        logger.apiCall("validateSwitches", "$PUBLISH_TYPE" to publishType, "ids" to ids, "bbox" to bbox)
        val switches = if (ids != null) {
            switchService.getMany(publishType, ids)
        } else {
            switchService.list(publishType, false)
        }
        val switchIds = switches
            .filter { switch -> switchMatchesBbox(switch, bbox, false) }
            .map { sw -> sw.id as IntId }
        return publicationService.validateSwitches(switchIds, publishType)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/draft")
    fun insertTrackLayoutSwitch(@RequestBody request: TrackLayoutSwitchSaveRequest): IntId<TrackLayoutSwitch> {
        logger.apiCall("insertTrackLayoutSwitch", "request" to request)
        return switchService.insertSwitch(request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/draft/{id}")
    fun updateSwitch(
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
        @RequestBody switch: TrackLayoutSwitchSaveRequest,
    ): IntId<TrackLayoutSwitch> {
        logger.apiCall("updateSwitch", "switchId" to switchId, "switch" to switch)
        return switchService.updateSwitch(switchId, switch)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/draft/{id}")
    fun deleteDraftSwitch(@PathVariable("id") switchId: IntId<TrackLayoutSwitch>): IntId<TrackLayoutSwitch> {
        logger.apiCall("deleteDraftSwitch", "switchId" to switchId)
        return switchService.deleteDraft(switchId).id
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/change-times")
    fun getSwitchChangeInfo(
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
    ): ResponseEntity<DraftableChangeInfo> {
        logger.apiCall("getSwitchChangeTimes", "id" to switchId, "$PUBLISH_TYPE" to publishType)
        return toResponse(switchService.getDraftableChangeInfo(switchId, publishType))
    }
}
