package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@GeoviiteController("/track-layout")
class TrackBoundaryMoveController(private val trackBoundaryMoveService: TrackBoundaryMoveService) {
    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/track-boundary-move/{$LAYOUT_BRANCH}/")
    fun saveTrackBoundaryMove(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @RequestBody request: TrackBoundaryMoveRequest,
    ): IntId<TrackBoundaryMove> =
        trackBoundaryMoveService.saveTrackBoundaryMove(
            layoutBranch,
            shorteningTrackId = request.shorteningTrackId,
            lengtheningTrackId = request.lengtheningTrackId,
            upToSwitchJoint = request.upToSwitchJoint,
            boundaryMoveDirection = request.boundaryMoveDirection,
            deleteShorteningTrack = request.deleteShorteningTrack,
        )

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/track-boundary-move/{$LAYOUT_BRANCH}/counterpart-options/{id}")
    fun getTrackBoundaryMoveCounterpartOptions(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<BoundaryMoveCounterpart> {
        return trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(layoutBranch.draft, id)
    }
}
