package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

const val MAX_TRACK_SEEK_DISTANCE = 100.0

@GeoviiteController("/track-layout/route")
class RoutingController(private val routingService: RoutingService) {

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/closest-track-point")
    fun getClosestTrackPoint(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("x") x: Double,
        @RequestParam("y") y: Double,
        @RequestParam("maxDistance", required = false) maxDistance: Double?,
    ): ClosestTrackPoint? =
        routingService.getClosestTrackPoint(
            context = LayoutContext.of(layoutBranch, publicationState),
            location = Point(x, y),
            maxDistance = maxDistance ?: MAX_TRACK_SEEK_DISTANCE,
        )

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}")
    fun getRoute(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("startX") startX: Double,
        @RequestParam("startY") startY: Double,
        @RequestParam("endX") endX: Double,
        @RequestParam("endY") endY: Double,
        @RequestParam("maxDistance", required = false) maxDistance: Double?,
    ): RouteResult? =
        routingService.getRoute(
            context = LayoutContext.of(layoutBranch, publicationState),
            startLocation = Point(startX, startY),
            endLocation = Point(endX, endY),
            trackSeekDistance = maxDistance ?: MAX_TRACK_SEEK_DISTANCE,
        )
}
