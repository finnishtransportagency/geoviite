package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.ui.SeleniumTest
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

        val searchResults = mapPage.toolBar.search("test-lt").searchResults.map { it.text }
        assertEquals(ltNames.map { (n, d) -> "$n, $d" }, searchResults)

        val searchResultsBs = mapPage.toolBar.search(" b", false).searchResults.map { it.text }
        assertEquals(listOf("test-lt B2, test-desc-2", "test-lt B3, test-desc-3"), searchResultsBs)

        val searchResultsB2 = mapPage.toolBar.search("2", false).searchResults.map { it.text }
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

        mapPage.toolBar.search(track.name.toString()).selectSearchResult(track.name.toString())

        val locationTrackGeneralInfoBox = mapPage.toolPanel.locationTrackGeneralInfo
        assertEquals(track.name.toString(), locationTrackGeneralInfoBox.name)
        //assertEquals(track.descriptionBase.toString(), locationTrackGeneralInfoBox.description)
        assertEquals(trackNumber.number.toString(), locationTrackGeneralInfoBox.trackNumber)

    }
}
