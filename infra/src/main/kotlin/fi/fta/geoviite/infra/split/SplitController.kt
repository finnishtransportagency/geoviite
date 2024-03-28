package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/location-track-split")
class SplitController(val splitService: SplitService) {
    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping
    fun splitLocationTrack(@RequestBody request: SplitRequest): IntId<Split> {
        return splitService.split(request)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{id}")
    fun getSplit(@PathVariable("id") id: IntId<Split>): ResponseEntity<Split> = toResponse(splitService.get(id))

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{id}/bulk-transfer-state")
    fun updateSplitTransferState(
        @PathVariable("id") id: IntId<Split>,
        @RequestBody state: BulkTransferState,
    ): IntId<Split> = splitService.updateSplitState(id, state)
}
