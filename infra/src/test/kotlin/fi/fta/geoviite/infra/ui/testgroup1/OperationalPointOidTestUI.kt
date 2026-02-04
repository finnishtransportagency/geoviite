package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.OperationalPointService
import fi.fta.geoviite.infra.tracklayout.operationalPoint
import fi.fta.geoviite.infra.ui.SeleniumTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class OperationalPointOidTestUI
@Autowired
constructor(
    private val operationalPointService: OperationalPointService,
    private val operationalPointDao: OperationalPointDao,
    private val trackNumberDao: LayoutTrackNumberDao,
) : SeleniumTest() {

    @BeforeEach
    fun setup() {
        testDBService.clearAllTables()
        startGeoviite()
    }

    @Test
    fun `operational point OID is displayed after publication`() {
        // Create a draft operational point at the default map location
        val centerPoint = Point(385782.89, 6672277.83)
        val operationalPointVersion =
            mainDraftContext.save(
                operationalPoint(
                    name = "Test Point",
                    location = centerPoint,
                    polygon =
                        Polygon(
                            Point(centerPoint.x - 10, centerPoint.y - 10),
                            Point(centerPoint.x + 10, centerPoint.y - 10),
                            Point(centerPoint.x + 10, centerPoint.y + 10),
                            Point(centerPoint.x - 10, centerPoint.y + 10),
                            Point(centerPoint.x - 10, centerPoint.y - 10),
                        ),
                    draft = true,
                )
            )
        val operationalPointId = operationalPointVersion.id

        // Navigate to map view and switch to draft mode
        val trackLayoutPage = goToMap().switchToDraftMode()

        // Verify the operational point does not have an OID yet in draft mode (UI verification)
        trackLayoutPage.selectionPanel.selectOperationalPoint("Test Point")
        val oidBeforePublicationUI = trackLayoutPage.toolPanel.operationalPointGeneralInfo.oid
        assertTrue(
            oidBeforePublicationUI == "Julkaisematon",
            "Operational point should not have an OID before publication (UI)",
        )

        val oidBeforePublication = operationalPointDao.fetchExternalId(LayoutBranch.main, operationalPointId)
        assertTrue(
            oidBeforePublication == null,
            "Operational point should not have an OID before publication (backend)",
        )

        // Navigate to preview and publish
        val previewPage = trackLayoutPage.goToPreview()
        previewPage.waitForAllTableValidationsToComplete()

        // Stage the operational point change
        previewPage.stageChange("Toiminnallinen piste Test Point")

        // Publish the changes
        previewPage.publish()

        // Navigate back to map and verify the OID is displayed in the UI
        goToMap().switchToOfficialMode().also { page ->
            page.selectionPanel.selectOperationalPoint("Test Point")
            val oidAfterPublicationUI = page.toolPanel.operationalPointGeneralInfo.oid
            assertNotNull(oidAfterPublicationUI, "Operational point OID should not be null (UI)")
            assertFalse(oidAfterPublicationUI.isEmpty(), "Operational point OID should not be empty (UI)")

            val oidAfterPublication = operationalPointDao.fetchExternalId(LayoutBranch.main, operationalPointId)
            assertNotNull(oidAfterPublication?.oid, "Operational point OID should exist in backend")
            assertTrue(
                oidAfterPublicationUI == oidAfterPublication?.oid.toString(),
                "UI OID ($oidAfterPublicationUI) should match backend OID (${oidAfterPublication?.oid})",
            )
        }
    }
}
