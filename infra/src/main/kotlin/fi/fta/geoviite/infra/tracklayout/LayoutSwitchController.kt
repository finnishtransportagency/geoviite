package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.util.FreeText
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}")
    fun getTrackLayoutSwitches(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox?,
        @RequestParam("name") name: String?,
        @RequestParam("offset") offset: Int?,
        @RequestParam("limit") limit: Int?,
        @RequestParam("comparisonPoint") comparisonPoint: Point?,
        @RequestParam("switchType") switchType: String?,
        @RequestParam("includeSwitchesWithNoJoints") includeSwitchesWithNoJoints: Boolean = false,
    ): List<TrackLayoutSwitch> {
        logger.apiCall(
            "getTrackLayoutSwitches",
            "publishType" to publishType, "bbox" to bbox, "name" to name, "offset" to offset,
            "limit" to limit, "comparisonPoint" to comparisonPoint, "switchType" to switchType
        )
        val filter = switchService.switchFilter(name, switchType, bbox, includeSwitchesWithNoJoints)
        val switches = switchService.list(publishType, filter)
        return switchService.pageSwitches(switches, offset ?: 0, limit, comparisonPoint)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["searchTerm", "limit"])
    fun searchSwitches(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limit", required = true) limit: Int,
    ): List<TrackLayoutSwitch> {
        logger.apiCall("searchSwitches", "searchTerm" to searchTerm, "limit" to limit)
        return switchService.list(publishType, searchTerm, limit)
    }


    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}")
    fun getTrackLayoutSwitch(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitch", "id" to id, "publishType" to publishType)
        return toResponse(switchService.get(publishType, id))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["ids"])
    fun getTrackLayoutSwitches(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutSwitch>>,
    ): List<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitches", "ids" to ids, "publishType" to publishType)
        return switchService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/joint-connections")
    fun getSwitchJointConnections(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        logger.apiCall("getSwitchJointConnections", "switchId" to id, "publishType" to publishType)
        return switchService.getSwitchJointConnections(publishType, id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/{id}/validation")
    fun validateSwitch(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ValidatedAsset<TrackLayoutSwitch> {
        logger.apiCall("validateSwitch", "publishType" to publishType, "id" to id)
        return publicationService.validateSwitch(id, publishType)
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
        return switchService.deleteDraftSwitch(switchId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}/change-times")
    fun getSwitchChangeTimes(@PathVariable("id") switchId: IntId<TrackLayoutSwitch>): ChangeTimes {
        logger.apiCall("getSwitchChangeTimes", "id" to switchId)
        return switchService.getChangeTimes(switchId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}/location-track-changes")
    fun getSwitchLocationTrackChanges(@PathVariable("id") switchId: IntId<TrackLayoutSwitch>): List<SwitchLocationTrackConnectionChange> {
        logger.apiCall("getSwitchLocationTrackChanges", "switchId" to switchId)
        return switchService.getSwitchLocationTrackChanges(switchId)
    }
}
