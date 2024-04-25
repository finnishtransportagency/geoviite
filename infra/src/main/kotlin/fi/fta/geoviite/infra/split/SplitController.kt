package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/location-track-split")
class SplitController(
    private val splitService: SplitService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping
    fun splitLocationTrack(@RequestBody request: SplitRequest): IntId<Split> {
        logger.apiCall("splitLocationTrack", "request" to request)
        return splitService.split(request)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{id}")
    fun getSplit(@PathVariable("id") id: IntId<Split>): ResponseEntity<Split> {
        logger.apiCall("getSplit", "id" to id)
        return toResponse(splitService.get(id))
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/{id}/bulk-transfer-state")
    fun updateSplitTransferState(
        @PathVariable("id") id: IntId<Split>,
        @RequestBody state: BulkTransferState,
    ): IntId<Split> {
        logger.apiCall("updateSplitTransferState", "id" to id, "state" to state)
        return splitService.updateSplitState(id, state).id
    }
}
