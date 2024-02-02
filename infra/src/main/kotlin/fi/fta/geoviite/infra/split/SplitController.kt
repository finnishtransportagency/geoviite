package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.authorization.AUTH_UI_READ
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/location-track-split")
class SplitController(val splitService: SplitService) {
    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping
    fun splitLocationTrack(@RequestBody request: SplitRequest): IntId<Split> {
        return splitService.split(request)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{id}")
    fun getSplit(@PathVariable("id") id: IntId<Split>): ResponseEntity<Split> = toResponse(splitService.get(id))

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/{id}/bulk-transfer-state")
    fun updateSplitTransferState(
        @PathVariable("id") id: IntId<Split>,
        @RequestBody state: BulkTransferState,
    ): ResponseEntity<Unit> = splitService.updateSplitState(id, state).let(::toResponse)
}
