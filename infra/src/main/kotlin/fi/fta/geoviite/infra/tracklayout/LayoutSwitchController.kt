package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}")
    fun getTrackLayoutSwitches(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
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
            "$PUBLICATION_STATE" to publicationState,
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
        val switches = switchService.listWithStructure(publicationState, includeDeleted).filter(filter)
        return pageSwitches(switches, offset ?: 0, limit, comparisonPoint).items
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}")
    fun getTrackLayoutSwitch(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitch", "id" to id, "$PUBLICATION_STATE" to publicationState)
        return toResponse(switchService.get(publicationState, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["ids"])
    fun getTrackLayoutSwitches(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutSwitch>>,
    ): List<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitches", "ids" to ids, "$PUBLICATION_STATE" to publicationState)
        return switchService.getMany(publicationState, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/joint-connections")
    fun getSwitchJointConnections(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        logger.apiCall("getSwitchJointConnections", "switchId" to id, "$PUBLICATION_STATE" to publicationState)
        return switchService.getSwitchJointConnections(publicationState, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("{$PUBLICATION_STATE}/validation")
    fun validateSwitches(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<TrackLayoutSwitch>>?,
        @RequestParam("bbox") bbox: BoundingBox?,
    ): List<ValidatedAsset<TrackLayoutSwitch>> {
        logger.apiCall("validateSwitches", "$PUBLICATION_STATE" to publicationState, "ids" to ids, "bbox" to bbox)
        val switches = if (ids != null) {
            switchService.getMany(publicationState, ids)
        } else {
            switchService.list(publicationState, false)
        }
        val switchIds = switches
            .filter { switch -> switchMatchesBbox(switch, bbox, false) }
            .map { sw -> sw.id as IntId }
        return publicationService.validateSwitches(switchIds, publicationState)
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/change-times")
    fun getSwitchChangeInfo(
        @PathVariable("id") switchId: IntId<TrackLayoutSwitch>,
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        logger.apiCall("getSwitchChangeInfo", "id" to switchId, "$PUBLICATION_STATE" to publicationState)
        return toResponse(switchService.getLayoutAssetChangeInfo(switchId, publicationState))
    }
}
