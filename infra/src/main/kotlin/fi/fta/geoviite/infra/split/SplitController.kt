package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/location-track-split")
class SplitController(val splitService: SplitService) {
    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping
    fun splitLocationTrack(@RequestBody request: SplitRequest): IntId<SplitSource> {
        return splitService.split(request)
    }
}
