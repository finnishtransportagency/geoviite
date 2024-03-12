package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
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
@RequestMapping("/track-layout/km-posts")
class LayoutKmPostController(
    private val kmPostService: LayoutKmPostService,
    private val publicationService: PublicationService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}")
    fun getKmPost(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "$PUBLISH_TYPE" to publishType, "id" to id)
        return toResponse(kmPostService.get(publishType, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["ids"])
    fun getKmPosts(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutKmPost>>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "$PUBLISH_TYPE" to publishType, "ids" to ids)
        return kmPostService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/on-track-number/{trackNumberId}")
    fun getKmPostsOnTrackNumber(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("trackNumberId") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPostsOnTrackNumber", "$PUBLISH_TYPE" to publishType, "id" to id)
        return kmPostService.list(publishType, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["bbox", "step"])
    fun findKmPosts(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("step") step: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPosts", "$PUBLISH_TYPE" to publishType, "bbox" to bbox, "step" to step)
        return kmPostService.list(publishType, bbox, step)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["location", "offset", "limit"])
    fun findKmPosts(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>?,
        @RequestParam("location") location: Point,
        @RequestParam("offset") offset: Int,
        @RequestParam("limit") limit: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall(
            "getNearbyKmPostsOnTrack",
            "$PUBLISH_TYPE" to publishType,
            "trackNumberId" to trackNumberId,
            "location" to location,
            "offset" to offset,
            "limit" to limit
        )
        return kmPostService.listNearbyOnTrackPaged(
            publicationState = publishType,
            location = location,
            trackNumberId = trackNumberId,
            offset = offset,
            limit = limit,
        )
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["trackNumberId", "kmNumber"])
    fun getKmPost(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("kmNumber") kmNumber: KmNumber,
        @RequestParam("includeDeleted") includeDeleted: Boolean,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall(
            "getKmPostOnTrack",
            "$PUBLISH_TYPE" to publishType,
            "trackNumberId" to trackNumberId,
            "kmNumber" to kmNumber,
            "includeDeleted" to includeDeleted,
        )
        return toResponse(kmPostService.getByKmNumber(publishType, trackNumberId, kmNumber, includeDeleted))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("{$PUBLISH_TYPE}/{id}/validation")
    fun validateKmPost(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<ValidatedAsset<TrackLayoutKmPost>> {
        logger.apiCall("validateKmPost", "$PUBLISH_TYPE" to publishType, "id" to id)
        return publicationService.validateKmPosts(listOf(id), publishType).firstOrNull().let(::toResponse)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/draft")
    fun insertTrackLayoutKmPost(@RequestBody request: TrackLayoutKmPostSaveRequest): IntId<TrackLayoutKmPost> {
        logger.apiCall("insertTrackLayoutKmPost", "request" to request)
        return kmPostService.insertKmPost(request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/draft/{id}")
    fun updateKmPost(
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @RequestBody request: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        logger.apiCall("updateKmPost", "kmPostId" to kmPostId, "request" to request)
        return kmPostService.updateKmPost(kmPostId, request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/draft/{id}")
    fun deleteDraftKmPost(@PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>): IntId<TrackLayoutKmPost> {
        logger.apiCall("deleteDraftKmPost", "kmPostId" to kmPostId)
        return kmPostService.deleteDraft(kmPostId).id
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/change-times")
    fun getKmPostChangeInfo(
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
    ): ResponseEntity<DraftableChangeInfo> {
        logger.apiCall("getKmPostChangeTimes", "id" to kmPostId)
        return toResponse(kmPostService.getDraftableChangeInfo(kmPostId, publishType))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/km-length")
    fun getKmLength(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<Double> {
        logger.apiCall("getKmLength", "id" to kmPostId, "$PUBLISH_TYPE" to publishType)
        return toResponse(kmPostService.getSingleKmPostLength(publishType, kmPostId))
    }
}
