package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/track-layout/km-posts")
class LayoutKmPostController(
    private val kmPostService: LayoutKmPostService,
    private val publicationService: PublicationService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private fun assertMainBranch(branch: LayoutBranch) = require(branch == LayoutBranch.main) {
        // TODO: GVT-2401: DAO support missing for fetching design branch data
        "Design branch use is not yet supported"
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}/{id}")
    fun getKmPost(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "branch" to branch, PUBLICATION_STATE to publicationState, "id" to id)
        assertMainBranch(branch)
        return toResponse(kmPostService.get(publicationState, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}", params = ["ids"])
    fun getKmPosts(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutKmPost>>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPost", "branch" to branch, PUBLICATION_STATE to publicationState, "ids" to ids)
        assertMainBranch(branch)
        return kmPostService.getMany(publicationState, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}/on-track-number/{trackNumberId}")
    fun getKmPostsOnTrackNumber(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("trackNumberId") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("getKmPostsOnTrackNumber", "branch" to branch, PUBLICATION_STATE to publicationState, "id" to id)
        assertMainBranch(branch)
        return kmPostService.list(publicationState, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}", params = ["bbox", "step"])
    fun findKmPosts(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("step") step: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall("findKmPosts", "branch" to branch, PUBLICATION_STATE to publicationState, "bbox" to bbox, "step" to step)
        assertMainBranch(branch)
        return kmPostService.list(publicationState, bbox, step)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}", params = ["location", "offset", "limit"])
    fun findKmPosts(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>?,
        @RequestParam("location") location: Point,
        @RequestParam("offset") offset: Int,
        @RequestParam("limit") limit: Int,
    ): List<TrackLayoutKmPost> {
        logger.apiCall(
            "getNearbyKmPostsOnTrack",
            "branch" to branch,
            PUBLICATION_STATE to publicationState,
            "trackNumberId" to trackNumberId,
            "location" to location,
            "offset" to offset,
            "limit" to limit
        )
        assertMainBranch(branch)
        return kmPostService.listNearbyOnTrackPaged(
            publicationState = publicationState,
            location = location,
            trackNumberId = trackNumberId,
            offset = offset,
            limit = limit,
        )
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}", params = ["trackNumberId", "kmNumber"])
    fun getKmPost(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("kmNumber") kmNumber: KmNumber,
        @RequestParam("includeDeleted") includeDeleted: Boolean,
    ): ResponseEntity<TrackLayoutKmPost> {
        logger.apiCall(
            "getKmPostOnTrack",
            "branch" to branch,
            PUBLICATION_STATE to publicationState,
            "trackNumberId" to trackNumberId,
            "kmNumber" to kmNumber,
            "includeDeleted" to includeDeleted,
        )
        assertMainBranch(branch)
        return toResponse(kmPostService.getByKmNumber(publicationState, trackNumberId, kmNumber, includeDeleted))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}/{id}/validation")
    fun validateKmPost(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<ValidatedAsset<TrackLayoutKmPost>> {
        logger.apiCall("validateKmPost", "branch" to branch, PUBLICATION_STATE to publicationState, "id" to id)
        assertMainBranch(branch)
        return publicationService.validateKmPosts(listOf(id), publicationState).firstOrNull().let(::toResponse)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{layout_branch}/draft")
    fun insertTrackLayoutKmPost(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @RequestBody request: TrackLayoutKmPostSaveRequest,
     ): IntId<TrackLayoutKmPost> {
        logger.apiCall("insertTrackLayoutKmPost", "branch" to branch, "request" to request)
        return kmPostService.insertKmPost(branch, request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{layout_branch}/draft/{id}")
    fun updateKmPost(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @RequestBody request: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        logger.apiCall("updateKmPost", "branch" to branch, "kmPostId" to kmPostId, "request" to request)
        return kmPostService.updateKmPost(branch, kmPostId, request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/{layout_branch}/draft/{id}")
    fun deleteDraftKmPost(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
    ): IntId<TrackLayoutKmPost> {
        logger.apiCall("deleteDraftKmPost", "branch" to branch, "kmPostId" to kmPostId)
        assertMainBranch(branch)
        return kmPostService.deleteDraft(kmPostId).id
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}/{id}/change-info")
    fun getKmPostChangeInfo(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        logger.apiCall("getKmPostChangeInfo", "id" to kmPostId, "branch" to branch, PUBLICATION_STATE to publicationState)
        assertMainBranch(branch)
        return toResponse(kmPostService.getLayoutAssetChangeInfo(kmPostId, publicationState))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{layout_branch}/{$PUBLICATION_STATE}/{id}/km-length")
    fun getKmLength(
        @PathVariable("layout_branch") branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<Double> {
        logger.apiCall("getKmLength", "id" to kmPostId, "branch" to branch, PUBLICATION_STATE to publicationState)
        assertMainBranch(branch)
        return toResponse(kmPostService.getSingleKmPostLength(publicationState, kmPostId))
    }
}
