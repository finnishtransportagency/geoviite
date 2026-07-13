package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.HKI_TRACK_NUMBER_1
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westMainLocationTrack
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westReferenceLineGeometry
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class DesignModeTestUI : SeleniumTest() {

    @BeforeEach
    fun beforeEach() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Design mode location track edit is isolated from main draft`() {
        val trackNumberVersion =
            mainOfficialContext.createLayoutTrackNumber(
                trackNumber = HKI_TRACK_NUMBER_1,
                geometry = westReferenceLineGeometry(),
            )
        val (_, westGeometry) = westMainLocationTrack(trackNumberVersion.id)
        mainOfficialContext.save(
            locationTrack(trackNumberId = trackNumberVersion.id, name = "Original Name"),
            westGeometry,
        )

        startGeoviite()

        val page = goToMap().switchToDesignMode().also { it.addDesign("isolation-test") }

        page.selectionPanel.selectLocationTrack("Original Name")
        page.toolPanel.locationTrackGeneralInfo.edit().setName("Design Name").save()

        assertEquals("Design Name", page.toolPanel.locationTrackGeneralInfo.name)

        page.switchToDraftMode()
        page.selectionPanel.selectLocationTrack("Original Name")

        assertEquals("Original Name", page.toolPanel.locationTrackGeneralInfo.name)
    }
}
