package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout/layout-graph")
class LayoutGraphController(val graphService: LayoutGraphService) {

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}")
    fun getGraph(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("detailLevel", required = false) detailLevel: DetailLevel?,
    ): LayoutGraph {
        return graphService.getGraph(
            context = LayoutContext.of(layoutBranch, publicationState),
            detailLevel = detailLevel ?: DetailLevel.NANO,
            bbox = bbox,
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/route", params = ["start", "end"])
    fun getRoute(@RequestParam("start") start: Point, @RequestParam("end") end: Point): Route {
        return graphService.getRoute(start, end)
    }
}
