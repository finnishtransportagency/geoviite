package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@GeoviiteController("/location-track-split")
class SplitController(private val splitService: SplitService) {

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/{$LAYOUT_BRANCH}")
    fun splitLocationTrack(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @RequestBody request: SplitRequest,
    ): IntId<Split> {
        return splitService.split(branch, request)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{id}")
    fun getSplit(@PathVariable("id") id: IntId<Split>): ResponseEntity<Split> {
        return toResponse(splitService.get(id))
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{id}/bulk-transfer-state")
    fun updateSplitTransferState(
        @PathVariable("id") id: IntId<Split>,
        @RequestBody state: BulkTransferState,
    ): IntId<Split> {
        return splitService.updateSplit(id, state).id
    }
}
