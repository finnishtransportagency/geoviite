package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}")
    fun getKmPost(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "publishType" to publishType, "id" to id)
        return toResponse(kmPostService.get(publishType, id))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["ids"])
    fun getKmPosts(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutKmPost>>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "publishType" to publishType, "ids" to ids)
        return kmPostService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/on-track-number/{trackNumberId}")
    fun getKmPostsOnTrackNumber(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("trackNumberId") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPostsOnTrackNumber", "publishType" to publishType, "id" to id)
        return kmPostService.list(publishType, id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["bbox", "step"])
    fun findKmPosts(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("step") step: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPosts", "publishType" to publishType, "bbox" to bbox, "step" to step)
        return kmPostService.list(publishType, bbox, step)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["location", "offset", "limit"])
    fun findKmPosts(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>?,
        @RequestParam("location") location: Point,
        @RequestParam("offset") offset: Int,
        @RequestParam("limit") limit: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall(
            "getNearbyKmPostsOnTrack",
            "publishType" to publishType,
            "trackNumberId" to trackNumberId,
            "location" to location,
            "offset" to offset,
            "limit" to limit
        )
        return kmPostService.listNearbyOnTrackPaged(
            publicationState = publishType,
            location = location,
            trackNumberId = if (trackNumberId is IntId) trackNumberId else null,
            offset = offset,
            limit = limit,
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["trackNumberId", "kmNumber"])
    fun getKmPost(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("kmNumber") kmNumber: KmNumber,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall(
            "getKmPostOnTrack", "publishType" to publishType, "trackNumberId" to trackNumberId, "kmNumber" to kmNumber
        )
        return toResponse(kmPostService.getByKmNumber(publishType, trackNumberId, kmNumber))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/{id}/validation")
    fun validateKmPost(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ValidatedAsset<TrackLayoutKmPost> {
        logger.apiCall("validateKmPost", "publishType" to publishType, "id" to id)
        return publicationService.validateKmPost(id, publishType)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/draft")
    fun insertTrackLayoutKmPost(@RequestBody request: TrackLayoutKmPostSaveRequest): IntId<TrackLayoutKmPost> {
        logger.apiCall("insertTrackLayoutKmPost", "request" to request)
        return kmPostService.insertKmPost(request)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/draft/{id}")
    fun updateKmPost(
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @RequestBody request: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        logger.apiCall("updateKmPost", "kmPostId" to kmPostId, "request" to request)
        return kmPostService.updateKmPost(kmPostId, request)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/draft/{id}")
    fun deleteDraftKmPost(@PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>): IntId<TrackLayoutKmPost> {
        logger.apiCall("deleteDraftKmPost", "kmPostId" to kmPostId)
        return kmPostService.deleteUnpublishedDraft(kmPostId).id
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}/change-times")
    fun getKmPostChangeInfo(@PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>): DraftableChangeInfo {
        logger.apiCall("getKmPostChangeTimes", "id" to kmPostId)
        return kmPostService.getDraftableChangeInfo(kmPostId)
    }
}
