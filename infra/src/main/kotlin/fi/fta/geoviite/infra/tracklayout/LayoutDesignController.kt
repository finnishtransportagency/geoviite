package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DESIGN
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.integration.DatabaseLock.PUBLICATION
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.publication.publicationMaxDuration
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout/layout-design")
class LayoutDesignController(val layoutDesignService: LayoutDesignService, val lockDao: LockDao) {

    @PreAuthorize(AUTH_VIEW_DESIGN)
    @GetMapping("/")
    fun getLayoutDesigns(
        @RequestParam("includeDeleted") includeDeleted: Boolean = false,
        @RequestParam("includeCompleted") includeCompleted: Boolean = false,
    ): List<LayoutDesign> {
        return layoutDesignService.list(includeCompleted = includeCompleted, includeDeleted = includeDeleted)
    }

    @PreAuthorize(AUTH_VIEW_DESIGN)
    @GetMapping("/{id}")
    fun getLayoutDesign(@PathVariable id: IntId<LayoutDesign>): LayoutDesign {
        return layoutDesignService.getOrThrow(id)
    }

    @PreAuthorize(AUTH_VIEW_DESIGN)
    @GetMapping("/{id}/merged-items-exist")
    fun getLayoutDesignMergedItemsExist(@PathVariable id: IntId<LayoutDesign>): Boolean {
        return layoutDesignService.hasMergedItems(id)
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
        // Design updates create publications and must not interleave with a running one
        return lockDao.runWithLock(PUBLICATION, publicationMaxDuration) {
            layoutDesignService.update(id, request)
            id
        }
            ?: throw PublicationFailureException(
                message = "Could not reserve publication lock",
                localizedMessageKey = "lock-obtain-failed",
            )
    }
}
