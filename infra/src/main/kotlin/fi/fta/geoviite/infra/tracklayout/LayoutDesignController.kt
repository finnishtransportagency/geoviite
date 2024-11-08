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

@GeoviiteController("/track-layout/layout-design")
class LayoutDesignController(val layoutDesignService: LayoutDesignService) {

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/")
    fun getLayoutDesigns(): List<LayoutDesign> {
        return layoutDesignService.list()
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
