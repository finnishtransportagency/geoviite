package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.PublicationState
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["searchTerm", "limitPerResultType"])
    fun searchAssets(
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limitPerResultType", required = true) limitPerResultType: Int,
    ): TrackLayoutSearchResult {
        logger.apiCall(
            "searchAssets",
            PUBLICATION_STATE to publicationState,
            "searchTerm" to searchTerm,
            "limitPerResultType" to limitPerResultType,
        )

        return TrackLayoutSearchResult(
            switches = switchService.list(publicationState, searchTerm, limitPerResultType),
            locationTracks = locationTrackService.list(publicationState, searchTerm, limitPerResultType),
            trackNumbers = trackNumberService.list(publicationState, searchTerm, limitPerResultType),
        )
    }
}
