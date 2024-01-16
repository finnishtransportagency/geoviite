package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import org.springframework.stereotype.Controller

@Controller
class SplitController(val splitService: SplitService) {
    fun splitLocationTrack(request: SplitRequest): IntId<SplitSource> {
        return splitService.split(request)
    }
}
