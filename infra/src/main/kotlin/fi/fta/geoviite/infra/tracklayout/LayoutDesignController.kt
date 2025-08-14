package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.common.IntId
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout/layout-design")
class LayoutDesignController(val layoutDesignService: LayoutDesignService) {

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/")
    fun getLayoutDesigns(
        @RequestParam("includeDeleted") includeDeleted: Boolean = false,
        @RequestParam("includeCompleted") includeCompleted: Boolean = false,
    ): List<LayoutDesign> {
        return layoutDesignService.list(includeCompleted = includeCompleted, includeDeleted = includeDeleted)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{id}")
    fun getLayoutDesign(@PathVariable id: IntId<LayoutDesign>): LayoutDesign {
        return layoutDesignService.getOrThrow(id)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/")
    fun insertLayoutDesign(@RequestBody request: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        return layoutDesignService.insert(request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{id}")
    fun updateLayoutDesign(
        @PathVariable id: IntId<LayoutDesign>,
        @RequestBody request: LayoutDesignSaveRequest,
    ): IntId<LayoutDesign> {
        return layoutDesignService.update(id, request)
    }
}
