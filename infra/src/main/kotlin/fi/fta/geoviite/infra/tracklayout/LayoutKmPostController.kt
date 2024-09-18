package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.publication.draftTransitionOrOfficialState
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout/km-posts")
class LayoutKmPostController(
    private val kmPostService: LayoutKmPostService,
    private val publicationService: PublicationService,
) {

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}")
    fun getKmPost(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<TrackLayoutKmPost> {
        val context = LayoutContext.of(branch, publicationState)
        return toResponse(kmPostService.get(context, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["ids"])
    fun getKmPosts(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<TrackLayoutKmPost>>,
    ): List<TrackLayoutKmPost> {
        val context = LayoutContext.of(branch, publicationState)
        return kmPostService.getMany(context, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/on-track-number/{trackNumberId}")
    fun getKmPostsOnTrackNumber(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("trackNumberId") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        val context = LayoutContext.of(branch, publicationState)
        return kmPostService.list(context, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["bbox", "step"])
    fun findKmPosts(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("step") step: Int,
    ): List<TrackLayoutKmPost> {
        val context = LayoutContext.of(branch, publicationState)
        return kmPostService.list(context, bbox, step)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["location", "offset", "limit"])
    fun findKmPosts(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>?,
        @RequestParam("location") location: Point,
        @RequestParam("offset") offset: Int,
        @RequestParam("limit") limit: Int,
    ): List<TrackLayoutKmPost> {
        val context = LayoutContext.of(branch, publicationState)
        return kmPostService.listNearbyOnTrackPaged(
            layoutContext = context,
            location = location,
            trackNumberId = trackNumberId,
            offset = offset,
            limit = limit,
        )
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["trackNumberId", "kmNumber"])
    fun getKmPost(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("kmNumber") kmNumber: KmNumber,
        @RequestParam("includeDeleted") includeDeleted: Boolean,
    ): ResponseEntity<TrackLayoutKmPost> {
        val context = LayoutContext.of(branch, publicationState)
        return kmPostService.getByKmNumber(context, trackNumberId, kmNumber, includeDeleted).let(::toResponse)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/validation")
    fun validateKmPost(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<ValidatedAsset<TrackLayoutKmPost>> {
        return publicationService
            .validateKmPosts(draftTransitionOrOfficialState(publicationState, branch), listOf(id))
            .firstOrNull()
            .let(::toResponse)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}/draft")
    fun insertTrackLayoutKmPost(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        return kmPostService.insertKmPost(branch, request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{$LAYOUT_BRANCH}/draft/{id}")
    fun updateKmPost(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @RequestBody request: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        return kmPostService.updateKmPost(branch, kmPostId, request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/{$LAYOUT_BRANCH}/draft/{id}")
    fun deleteDraftKmPost(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
    ): IntId<TrackLayoutKmPost> {
        return kmPostService.deleteDraft(branch, kmPostId).id
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/change-info")
    fun getKmPostChangeInfo(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        val context = LayoutContext.of(branch, publicationState)
        return toResponse(kmPostService.getLayoutAssetChangeInfo(context, kmPostId))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/infobox-extras")
    fun getKmPostInfoboxExtras(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") kmPostId: IntId<TrackLayoutKmPost>,
    ): ResponseEntity<KmPostInfoboxExtras> {
        val context = LayoutContext.of(branch, publicationState)
        return toResponse(kmPostService.getKmPostInfoboxExtras(context, kmPostId))
    }
}
