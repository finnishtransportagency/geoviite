package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.MapToolPanel
import fi.fta.geoviite.infra.ui.pagemodel.map.SearchBox
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class SearchTestUI @Autowired constructor() : SeleniumTest() {

    @BeforeEach
    fun goToMapPage() {
        clearAllTestData()
    }

    @Test
    fun `Narrow search results`() {
        val tnId = insertDraftTrackNumber()
        val ltNames = listOf(
            "test-lt A1" to "test-desc-1",
            "test-lt B2" to "test-desc-2",
            "test-lt B3" to "test-desc-3",
        )
        ltNames.forEach { (name, desc) ->
            locationTrackAndAlignment(trackNumberId = tnId, name = name, description = desc).let(::insertLocationTrack)
        }

        startGeoviite()
        val mapPage = goToMap()
        val searchBox = mapPage.searchBox()

        searchBox.addToSearchInput("test-lt")
        val searchResultsAll = searchBox.searchResults().map(SearchBox.SearchResult::value)
        assertEquals(ltNames.map { (n, d) -> "$n, $d" }, searchResultsAll)

        searchBox.addToSearchInput(" b")
        val searchResultsBs = searchBox.searchResults().map(SearchBox.SearchResult::value)
        assertEquals(listOf("test-lt B2, test-desc-2", "test-lt B3, test-desc-3"), searchResultsBs)

        searchBox.addToSearchInput("2")
        val searchResultsB2 = searchBox.searchResults().map(SearchBox.SearchResult::value)
        assertEquals(listOf("test-lt B2, test-desc-2"), searchResultsB2)
    }

    @Test
    fun `Search opens specific location track`() {
        val trackNumber = getOrCreateTrackNumber(getUnusedTrackNumber())
        val (track, alignment) = locationTrackAndAlignment(
            trackNumberId = trackNumber.id as IntId,
            name = "test-lt specific 001",
            description = "specific track selection test track 001"
        )
        insertLocationTrack(track, alignment)

        startGeoviite()
        val mapPage = goToMap()
        val searchBox = mapPage.searchBox()

        searchBox.search(track.name.toString())
        searchBox.selectResult(track.name.toString())

        val locationTrackGeneralInfoBox = MapToolPanel().locationTrackGeneralInfo()
        assertEquals(track.name.toString(), locationTrackGeneralInfoBox.sijainteraidetunnus())
        assertEquals(track.description.toString(), locationTrackGeneralInfoBox.kuvaus())
        assertEquals(trackNumber.number.toString(), locationTrackGeneralInfoBox.ratanumero())

    }
}
