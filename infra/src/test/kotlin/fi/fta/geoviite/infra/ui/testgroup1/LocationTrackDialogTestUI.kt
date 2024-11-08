package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.ui.SeleniumTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class LocationTrackDialogTestUI @Autowired constructor() : SeleniumTest() {

    @Test
    fun `New location track owner is set to vayla regardless of caching`() {
        startGeoviite()
        val page = goToMap().switchToDraftMode()
        val firstLoad = page.toolBar.createNewLocationTrack()
        assertEquals("Väylävirasto", firstLoad.ownerDropdown.value)

        firstLoad.cancel()

        assertEquals("Väylävirasto", page.toolBar.createNewLocationTrack().ownerDropdown.value)
    }
}
