package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.geometry.TestGeometryPlanService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import fi.fta.geoviite.infra.ui.testdata.locationTrack
import fi.fta.geoviite.infra.ui.testdata.referenceLineGeometryFromPoints
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class PlanDownloadTestUI @Autowired constructor(private val testGeometryPlanService: TestGeometryPlanService) :
    SeleniumTest() {

    @BeforeEach
    fun setup() {
        testDBService.clearAllTables()
    }

    private fun setupTrackAndPlan(): String {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumber(
                    trackNumber,
                    referenceLineGeometryFromPoints(DEFAULT_BASE_POINT, listOf(Point(0.0, 100.0))),
                )
                .id
        mainOfficialContext.saveLocationTrack(
            locationTrack(
                name = "lt-download",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT,
                incrementPoints = listOf(Point(0.0, 50.0), Point(0.0, 50.0)),
            )
        )
        testGeometryPlanService
            .buildPlan(trackNumber)
            .alignment("test alignment", Point(0.0, 0.0), Point(0.0, 50.0), Point(0.0, 50.0))
            .save()
        return "lt-download"
    }

    private fun startGeoviiteAndGoToWork(): E2ETrackLayoutPage =
        startGeoviite().let { goToMap().zoomToScale(E2ETrackLayoutPage.MapScale.M_500) }

    @Test
    fun `Plan download popup opens and shows both sections`() {
        setupTrackAndPlan()

        val popup = startGeoviiteAndGoToWork().selectionPanel.openPlanDownloadPopup()

        assertTrue(popup.isAreaSectionToggleVisible)
        assertTrue(popup.isPlanSectionToggleVisible)
    }

    @Test
    fun `Selecting a location track populates the plan list`() {
        val locationTrackName = setupTrackAndPlan()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val popup =
            trackLayoutPage.selectionPanel
                .openPlanDownloadPopup()
                .openAreaSection()
                .selectAsset(locationTrackName)
                .openPlanSection()

        assertTrue(popup.planRows.isNotEmpty(), "Expected at least one plan in the list")
    }

    @Test
    fun `Download button disabled until a plan is selected`() {
        val locationTrackName = setupTrackAndPlan()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val popup =
            trackLayoutPage.selectionPanel
                .openPlanDownloadPopup()
                .openAreaSection()
                .selectAsset(locationTrackName)
                .openPlanSection()

        assertFalse(popup.isDownloadEnabled, "Download button should be disabled with nothing selected")

        val planName = popup.planRows.first()
        popup.togglePlan(planName)

        assertTrue(popup.isDownloadEnabled, "Download button should be enabled after selecting a plan")
    }

    @Test
    fun `Select all and unselect all work correctly`() {
        val locationTrackName = setupTrackAndPlan()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val popup =
            trackLayoutPage.selectionPanel
                .openPlanDownloadPopup()
                .openAreaSection()
                .selectAsset(locationTrackName)
                .openPlanSection()

        val planCount = popup.planRows.size
        assertTrue(planCount > 0)

        popup.selectAll()
        assertTrue(popup.isDownloadEnabled, "Download button should be enabled after select all")

        popup.unselectAll()
        assertFalse(popup.isDownloadEnabled, "Download button should be disabled after unselect all")
    }
}
