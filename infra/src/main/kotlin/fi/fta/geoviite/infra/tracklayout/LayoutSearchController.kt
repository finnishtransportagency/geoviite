package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.util.FreeText
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

data class SearchLocation(val coordinates: IPoint, val description: String)

data class TrackLayoutSearchResult(
    val locationTracks: List<LocationTrack>,
    val switches: List<LayoutSwitch>,
    val trackNumbers: List<LayoutTrackNumber>,
    val operatingPoints: List<RatkoOperatingPoint>,
    val locations: List<SearchLocation>,
)

@GeoviiteController("/track-layout/search")
class LayoutSearchController(private val searchService: LayoutSearchService) {

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["searchTerm", "limitPerResultType"])
    fun searchAssets(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limitPerResultType", required = true) limitPerResultType: Int,
        @RequestParam("locationTrackSearchScope", required = false) locationTrackSearchScope: IntId<LocationTrack>?,
    ): TrackLayoutSearchResult {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return searchService.searchAssets(layoutContext, searchTerm, limitPerResultType, locationTrackSearchScope)
    }
}
