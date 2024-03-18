package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.PUBLISH_TYPE
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["searchTerm", "limitPerResultType"])
    fun publishTypeSearch(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limitPerResultType", required = true) limitPerResultType: Int,
    ): TrackLayoutSearchResult {
        logger.apiCall(
            "publishTypeSearch",
            "$PUBLISH_TYPE" to publishType,
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
