package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.pagemodel.map.E2EChangePreviewRow
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.HKI_TRACK_NUMBER_1
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westLayoutKmPosts
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

    @Test
    fun `Design mode location track edit can be reverted`() {
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

        val page = goToMap().switchToDesignMode().also { it.addDesign("revert-test") }

        page.selectionPanel.selectLocationTrack("Original Name")
        page.toolPanel.locationTrackGeneralInfo.edit().setName("Design Name").save()

        waitAndClearToast("location-track-dialog.modified-successfully")
        assertEquals("Design Name", page.toolPanel.locationTrackGeneralInfo.name)

        val previewPage = page.goToPreview()
        previewPage.revertChange("Sijaintiraide Design Name")
        val mapPage = previewPage.goToTrackLayout()

        mapPage.selectionPanel.selectLocationTrack("Original Name")

        assertEquals("Original Name", mapPage.toolPanel.locationTrackGeneralInfo.name)
    }

    @Test
    fun `Design mode location track edit can be published`() {
        val trackNumberVersion =
            mainOfficialContext.createLayoutTrackNumber(
                trackNumber = HKI_TRACK_NUMBER_1,
                geometry = westReferenceLineGeometry(),
            )
        val (_, westGeometry) = westMainLocationTrack(trackNumberVersion.id)
        val ltId =
            mainOfficialContext
                .save(locationTrack(trackNumberId = trackNumberVersion.id, name = "Original Name"), westGeometry)
                .id
        testDBService.generateOid(trackNumberVersion.id, LayoutBranch.main)
        testDBService.generateOid(ltId, LayoutBranch.main)
        val designBranch = testDBService.createDesignBranch("publish-test")
        testDBService.generateOid(ltId, designBranch)
        westLayoutKmPosts(trackNumberVersion.id).forEach(testDBService.kmPostDao::save)

        startGeoviite()

        val page = goToMap().switchToDesignMode().also { it.selectDesign("publish-test") }

        page.selectionPanel.selectLocationTrack("Original Name")
        page.toolPanel.locationTrackGeneralInfo.edit().setName("Design Name").save()

        waitAndClearToast("location-track-dialog.modified-successfully")
        assertEquals("Design Name", page.toolPanel.locationTrackGeneralInfo.name)

        val previewPage = page.goToPreview().stageChange("Sijaintiraide Design Name")
        previewPage.waitForAllTableValidationsToComplete()
        val stagedRow = previewPage.stagedChangesTable.rows.first { it.name == "Sijaintiraide Design Name" }
        if (stagedRow.state == E2EChangePreviewRow.State.ERROR) {
            val errors = previewPage.stagedChangesTable.getRowErrorMessages(stagedRow)
            assertEquals(emptyList<String>(), errors, "Staged change has validation errors")
        }
        // PublicationInDesign: design_draft -> design_official
        val mapPageAfterDesignPublish = previewPage.publish()

        // MergeFromDesign: design_official -> main_draft
        val mergePreviewPage = mapPageAfterDesignPublish.goToPreview()
        mergePreviewPage.switchToMergeToMainMode()
        mergePreviewPage.stageChange("Sijaintiraide Design Name")
        mergePreviewPage.waitForAllTableValidationsToComplete()
        val mapPage = mergePreviewPage.mergeToMain()

        mapPage.switchToDraftMode()
        mapPage.selectionPanel.selectLocationTrack("Design Name")

        assertEquals("Design Name", mapPage.toolPanel.locationTrackGeneralInfo.name)
    }
}
