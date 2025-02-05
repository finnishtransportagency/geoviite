package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.ui.SeleniumTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class SearchTestUI @Autowired constructor() : SeleniumTest() {

    @BeforeEach
    fun goToMapPage() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Narrow search results`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val ltNames =
            listOf("test-lt A1" to "test-desc-1", "test-lt B2" to "test-desc-2", "test-lt B3" to "test-desc-3")
        ltNames.forEach { (name, desc) ->
            mainOfficialContext.save(locationTrackAndGeometry(trackNumberId = tnId, name = name, description = desc))
        }

        startGeoviite()
        val mapPage = goToMap()

        val searchResults = mapPage.toolBar.search("test-lt").searchResults.map { it.name }
        assertEquals(ltNames.map { (n, d) -> "$n, $d" }, searchResults)

        val searchResultsBs = mapPage.toolBar.search(" b", false).searchResults.map { it.name }
        assertEquals(listOf("test-lt B2, test-desc-2", "test-lt B3, test-desc-3"), searchResultsBs)

        val searchResultsB2 = mapPage.toolBar.search("2", false).searchResults.map { it.name }
        assertEquals(listOf("test-lt B2, test-desc-2"), searchResultsB2)
    }

    @Test
    fun `Search opens specific location track`() {
        val (trackNumber, trackNumberId) = mainOfficialContext.createTrackNumberAndId()
        val (track, _) =
            mainOfficialContext.saveAndFetch(
                locationTrackAndGeometry(
                    trackNumberId = trackNumberId,
                    name = "test-lt specific 001",
                    description = "specific track selection test track 001",
                )
            )

        startGeoviite()
        val mapPage = goToMap()

        mapPage.toolBar.search(track.name.toString()).selectSearchResult(track.name.toString())

        val locationTrackGeneralInfoBox = mapPage.toolPanel.locationTrackGeneralInfo
        assertEquals(track.name.toString(), locationTrackGeneralInfoBox.name)
        assertEquals(trackNumber.toString(), locationTrackGeneralInfoBox.trackNumber)
    }
}
