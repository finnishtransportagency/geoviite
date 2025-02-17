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

        // Prevent the cancel button click from triggering the location track name field’s onBlur
        // event handler generating a “required field” error when the name is empty. The addition of
        // this error causes a re-rendering of the dialog, which blocks the cancel button’s onClick
        // event handler from triggering and keeps the dialog open, which leads to the test not
        // passing. \o/
        firstLoad.setName("test")

        assertEquals("Väylävirasto", firstLoad.ownerDropdown.value)
        firstLoad.cancel()

        assertEquals("Väylävirasto", page.toolBar.createNewLocationTrack().ownerDropdown.value)
    }
}
