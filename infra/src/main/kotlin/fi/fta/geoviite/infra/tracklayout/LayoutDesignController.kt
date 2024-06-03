package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.logging.apiCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/track-layout/layout-design")
class LayoutDesignController(
    val layoutDesignService: LayoutDesignService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/")
    fun getLayoutDesigns(): List<LayoutDesign> {
        logger.apiCall("getLayoutDesigns")
        return layoutDesignService.list()
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{id}")
    fun getLayoutDesign(@PathVariable id: IntId<LayoutDesign>): LayoutDesign {
        logger.apiCall("getLayoutDesign", "id" to id)
        return layoutDesignService.getOrThrow(id)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/")
    fun insertLayoutDesign(@RequestBody request: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        logger.apiCall("insertLayoutDesign", "request" to request)
        return layoutDesignService.insert(request)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{id}")
    fun updateLayoutDesign(
        @PathVariable id: IntId<LayoutDesign>,
        @RequestBody request: LayoutDesignSaveRequest,
    ): IntId<LayoutDesign> {
        logger.apiCall("updateLayoutDesign", "id" to id, "request" to request)
        return layoutDesignService.update(id, request)
    }
}
