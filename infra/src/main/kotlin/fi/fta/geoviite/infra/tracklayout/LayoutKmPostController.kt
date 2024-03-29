package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublicationState
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}")
    fun getKmPost(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return toResponse(kmPostService.get(publicationState, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["ids"])
    fun getKmPosts(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutKmPost>>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "$PUBLICATION_STATE" to publicationState, "ids" to ids)
        return kmPostService.getMany(publicationState, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/on-track-number/{trackNumberId}")
    fun getKmPostsOnTrackNumber(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("trackNumberId") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPostsOnTrackNumber", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return kmPostService.list(publicationState, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["bbox", "step"])
    fun findKmPosts(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("step") step: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPosts", "$PUBLICATION_STATE" to publicationState, "bbox" to bbox, "step" to step)
        return kmPostService.list(publicationState, bbox, step)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["location", "offset", "limit"])
    fun findKmPosts(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>?,
        @RequestParam("location") location: Point,
        @RequestParam("offset") offset: Int,
        @RequestParam("limit") limit: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall(
            "getNearbyKmPostsOnTrack",
            "$PUBLICATION_STATE" to publicationState,
            "trackNumberId" to trackNumberId,
            "location" to location,
            "offset" to offset,
            "limit" to limit
        )
        return kmPostService.listNearbyOnTrackPaged(
            publicationState = publicationState,
            location = location,
            trackNumberId = trackNumberId,
            offset = offset,
            limit = limit,
        )
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["trackNumberId", "kmNumber"])
    fun getKmPost(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("kmNumber") kmNumber: KmNumber,
        @RequestParam("includeDeleted") includeDeleted: Boolean,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall(
            "getKmPostOnTrack",
            "$PUBLICATION_STATE" to publicationState,
            "trackNumberId" to trackNumberId,
            "kmNumber" to kmNumber,
            "includeDeleted" to includeDeleted,
        )
        return toResponse(kmPostService.getByKmNumber(publicationState, trackNumberId, kmNumber, includeDeleted))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("{$PUBLICATION_STATE}/{id}/validation")
    fun validateKmPost(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<ValidatedAsset<TrackLayoutKmPost>> {
        logger.apiCall("validateKmPost", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return publicationService.validateKmPosts(listOf(id), publicationState).firstOrNull().let(::toResponse)
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/change-times")
    fun getKmPostChangeInfo(
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        logger.apiCall("getKmPostChangeInfo", "id" to kmPostId, "publicationState" to publicationState)
        return toResponse(kmPostService.getLayoutAssetChangeInfo(kmPostId, publicationState))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/km-length")
    fun getKmLength(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<Double> {
        logger.apiCall("getKmLength", "id" to kmPostId, "$PUBLICATION_STATE" to publicationState)
        return toResponse(kmPostService.getSingleKmPostLength(publicationState, kmPostId))
    }
}
