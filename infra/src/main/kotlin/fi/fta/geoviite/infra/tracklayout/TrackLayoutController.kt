package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.SwitchLinkingService
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.AlignmentFetchType.ALL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout")
class TrackLayoutController(
    private val switchService: LayoutSwitchService,
    private val trackNumberService: LayoutTrackNumberService,
    private val kmPostService: LayoutKmPostService,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
    private val mapAlignmentService: MapAlignmentService,
    private val switchLinkingService: SwitchLinkingService
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/track-numbers")
    fun getTrackNumbers(@PathVariable("publishType") publishType: PublishType): List<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumbers", "publishType" to publishType)
        return trackNumberService.list(publishType)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/track-numbers/{id}")
    fun getTrackNumber(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumber", "publishType" to publishType, "id" to id)
        return trackNumberService.get(publishType, id)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/draft/track-numbers")
    fun insertTrackNumber(
        @RequestBody saveRequest: TrackNumberSaveRequest,
    ): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("insertTrackNumber", "trackNumber" to saveRequest)
        return trackNumberService.insert(saveRequest)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/draft/track-numbers/{id}")
    fun updateTrackNumber(
        @PathVariable id: IntId<TrackLayoutTrackNumber>,
        @RequestBody saveRequest: TrackNumberSaveRequest,
    ): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("updateTrackNumber", "id" to id, "trackNumber" to saveRequest)
        return trackNumberService.update(id, saveRequest)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/draft/track-numbers/{id}")
    fun deleteDraftTrackNumber(@PathVariable("id") trackNumberId: IntId<TrackLayoutTrackNumber>):
            IntId<TrackLayoutTrackNumber> {
        logger.apiCall("deleteDraftTrackNumber", "trackNumberId" to trackNumberId)
        return trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(trackNumberId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/track-numbers/{id}/reference-line")
    fun getTrackNumberReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall(
            "getTrackNumberReferenceLine",
            "publishType" to publishType, "trackNumberId" to trackNumberId
        )
        return referenceLineService.getByTrackNumber(publishType, trackNumberId)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/track-numbers/{id}/km-posts-near")
    fun getNearbyKmPostsOnTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("location") location: Point,
        @RequestParam("offset") offset: Int?,
        @RequestParam("limit") limit: Int?,
    ): List<TrackLayoutKmPost> {
        logger.apiCall(
            "getNearbyKmPostsOnTrack",
            "publishType" to publishType, "trackNumberId" to trackNumberId,
            "location" to location, "offset" to offset, "limit" to limit
        )
        return kmPostService.listNearbyOnTrackPaged(
            publishType = publishType,
            location = location,
            trackNumberId = trackNumberId,
            offset = offset ?: 0,
            limit = limit
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/track-numbers/{trackNumberId}/km-post-at/{kmNumber}")
    fun getKmPostExistsOnTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @PathVariable("kmNumber") kmNumber: KmNumber,
    ): Boolean {
        logger.apiCall(
            "getKmPostOnTrack",
            "publishType" to publishType, "trackNumberId" to trackNumberId, "kmNumber" to kmNumber
        )
        return kmPostService.getKmPostExistsAtKmNumber(publishType, trackNumberId, kmNumber)
    }


    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/draft/reference-lines/non-linked")
    fun getNonLinkedReferenceLines(): List<ReferenceLine> {
        logger.apiCall("getNonLinkedReferenceLines")
        return referenceLineService.listNonLinked()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/draft/location-tracks/non-linked")
    fun getNonLinkedLocationTracks(): List<LocationTrack> {
        logger.apiCall("getNonLinkedLocationTracks")
        return locationTrackService.listNonLinked()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/reference-lines", params = ["bbox"])
    fun getRerenceLinesNear(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<ReferenceLine> {
        logger.apiCall("getReferenceLinesNear", "publishType" to publishType, "bbox" to bbox)
        return referenceLineService.listNear(publishType, bbox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/location-tracks", params = ["bbox"])
    fun getLocationTracksNear(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracksNear", "publishType" to publishType, "bbox" to bbox)
        return locationTrackService.listNear(publishType, bbox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/location-tracks", params = ["searchTerm", "limit"])
    fun searchLocationTracks(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("searchTerm", required = true) searchTerm: String,
        @RequestParam("limit", required = true) limit: Int,
    ): List<LocationTrack> {
        logger.apiCall("searchLocationTracks", "searchTerm" to searchTerm, "limit" to limit)
        return locationTrackService.list(publishType, searchTerm, limit)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/reference-lines/{id}")
    fun getReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall("getReferenceLine", "publishType" to publishType, "id" to id)
        return referenceLineService.get(publishType, id)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/location-tracks/{id}")
    fun getLocationTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrack> {
        logger.apiCall("getLocationTrack", "publishType" to publishType, "id" to id)
        return locationTrackService.get(publishType, id)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/location-tracks", params = ["ids"])
    fun getLocationTracks(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<LocationTrack>>,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracks", "publishType" to publishType, "ids" to ids)
        return ids.mapNotNull { id -> locationTrackService.get(publishType, id) }
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/location-tracks/{id}/duplicate-of")
    fun getLocationTrackDuplicateOfList(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<LocationTrackDuplicate> {
        logger.apiCall("getLocationTrack", "id" to id, "publishType" to publishType, "id" to id)
        return locationTrackService.getDuplicates(id, publishType)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/map/alignments")
    fun getMapAlignments(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
        @RequestParam("type") type: AlignmentFetchType? = null,
        @RequestParam("selectedId") selectedId: IntId<LocationTrack>? = null,
    ): List<MapAlignment<*>> {
        logger.apiCall(
            "getMapAlignments",
            "publishType" to publishType,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
            "selectedId" to selectedId,
        )
        return mapAlignmentService.getMapAlignments(publishType, bbox, resolution, type ?: ALL, selectedId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/map/reference-lines/{id}")
    fun getMapReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<MapAlignment<ReferenceLine>> {
        logger.apiCall("getMapReferenceLine", "publishType" to publishType, "id" to id)
        return mapAlignmentService.getMapReferenceLine(publishType, id)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/map/location-tracks/{id}")
    fun getMapLocationTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<MapAlignment<LocationTrack>> {
        logger.apiCall("getMapLocationTrack", "publishType" to publishType, "id" to id)
        return mapAlignmentService.getMapLocationTrack(publishType, id)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/official/reference-lines/{id}/change-times")
    fun getReferenceLineChangeInfo(@PathVariable("id") id: IntId<ReferenceLine>): ChangeTimes {
        logger.apiCall("getReferenceLineChangeInfo", "id" to id)
        return referenceLineService.getChangeTimes(id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/official/location-tracks/{id}/change-times")
    fun getLocationTrackChangeInfo(@PathVariable("id") id: IntId<LocationTrack>): ChangeTimes {
        logger.apiCall("getLocationTrackChangeInfo", "id" to id)
        return locationTrackService.getChangeTimes(id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/reference-lines/{id}/start-and-end")
    fun getReferenceLineStartAndEnd(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<ReferenceLineStartAndEnd> {
        logger.apiCall("getReferenceLineStartAndEnd", "publishType" to publishType, "id" to id)
        return referenceLineService.getStartAndEnd(publishType, id)?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/location-tracks/{id}/start-and-end")
    fun getLocationTrackStartAndEnd(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrackStartAndEnd> {
        logger.apiCall("getLocationTrackStartAndEnd", "publishType" to publishType, "id" to id)
        return locationTrackService.getStartAndEnd(publishType, id)?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/km-posts")
    fun getTrackLayoutKmPosts(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("step") step: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall(
            "getTrackLayoutKmPosts",
            "publishType" to publishType, "bbox" to bbox, "step" to step
        )
        return kmPostService.list(publishType, bbox, step)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/km-posts/{id}")
    fun getTrackLayoutKmPosts(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall("getTrackLayoutKmPosts", "publishType" to publishType, "id" to id)
        return kmPostService.get(publishType, id)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/km-posts/{id}")
    fun officialKmPostExists(
        @PathVariable("id") id: IntId<TrackLayoutKmPost>
    ): Boolean {
        logger.apiCall("officialKmPostExists", "id" to id)
        return kmPostService.officialKmPostExists(id)
    }


    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/switches")
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
    @GetMapping("/{publishType}/switches/{id}")
    fun getTrackLayoutSwitch(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitch", "id" to id, "publishType" to publishType)
        return switchService.get(publishType, id)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/switches/{id}/topology-track-meters")
    fun getTrackLayoutSwitchTrackMeters(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): ResponseEntity<List<LocationTrackMeter>> {
        logger.apiCall("getTrackLayoutSwitchTrackMeters", "id" to id, "publishType" to publishType)
        return ResponseEntity(switchLinkingService.getTopologySwitchTrackMeters(publishType, id), HttpStatus.OK)
    }


    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/switches", params = ["ids"])
    fun getTrackLayoutSwitches(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutSwitch>>,
    ): List<TrackLayoutSwitch> {
        logger.apiCall("getTrackLayoutSwitches", "ids" to ids, "publishType" to publishType)
        return ids.mapNotNull { id -> switchService.get(publishType, id) }
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/switches/{id}/joint-connections")
    fun getSwitchJointConnections(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        logger.apiCall("getSwitchJointConnections", "switchId" to id, "publishType" to publishType)
        return switchService.getSwitchJointConnections(publishType, id)

    }
}
