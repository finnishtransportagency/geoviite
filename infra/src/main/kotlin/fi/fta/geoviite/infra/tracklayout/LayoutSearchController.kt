package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_UI_READ
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.util.FreeText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

data class TrackLayoutSearchResult(
    val locationTracks: List<LocationTrack>,
    val switches: List<TrackLayoutSwitch>,
    val trackNumbers: List<TrackLayoutTrackNumber>,
)

@RestController
@RequestMapping("/track-layout/search")
class LayoutSearchController(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val trackNumberService: LayoutTrackNumberService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{publishType}", params = ["searchTerm", "limitPerResultType"])
    fun publishTypeSearch(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limitPerResultType", required = true) limitPerResultType: Int,
    ): TrackLayoutSearchResult {
        logger.apiCall(
            "publishTypeSearch",
            "publishType" to publishType,
            "searchTerm" to searchTerm,
            "limitPerResultType" to limitPerResultType,
        )

        return TrackLayoutSearchResult(
            switches = switchService.list(publishType, searchTerm, limitPerResultType),
            locationTracks = locationTrackService.list(publishType, searchTerm, limitPerResultType),
            trackNumbers = trackNumberService.list(publishType, searchTerm, limitPerResultType),
        )
    }
}
