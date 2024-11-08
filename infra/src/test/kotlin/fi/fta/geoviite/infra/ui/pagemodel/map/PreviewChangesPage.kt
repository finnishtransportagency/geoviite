package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.util.byQaId
import java.time.Duration
import org.openqa.selenium.By

class E2EPreviewChangesPage : E2EViewFragment(byQaId("preview-content")) {

    val changesTable: E2EChangePreviewTable by lazy {
        waitUntilChildInvisible(By.className("preview-section__spinner-container"), Duration.ofSeconds(10L))
        E2EChangePreviewTable(childBy(By.cssSelector("[qa-id='unstaged-changes'] table")))
    }

    val stagedChangesTable: E2EChangePreviewTable by lazy {
        waitUntilChildInvisible(By.className("preview-section__spinner-container"), Duration.ofSeconds(10L))
        E2EChangePreviewTable(childBy(By.cssSelector("[qa-id='staged-changes'] table")))
    }

    fun publish(): E2ETrackLayoutPage {
        logger.info("Publish changes")

        clickChild(By.cssSelector(".preview-footer__action-buttons button"))
        E2EPreviewChangesSaveOrDiscardDialog().confirm()

        waitAndClearToast("publish-success")

        return E2ETrackLayoutPage()
    }

    fun stageChanges(): E2EPreviewChangesPage = apply {
        logger.info("Stage all changes")
        changesTable.rows.forEach { changesTable.stageChange(it) }
    }

    fun stageChange(name: String): E2EPreviewChangesPage = apply {
        logger.info("Stage change $name")
        changesTable.stageChange(changesTable.rows.first { it.name == name })
    }

    fun revertStagedChange(name: String): E2EPreviewChangesPage = apply {
        logger.info("Revert staged change $name")

        stagedChangesTable.rows.filter { it.name == name }.forEach { stagedChangesTable.revertChange(it) }
    }

    fun revertChange(name: String): E2EPreviewChangesPage = apply {
        logger.info("Revert change $name")
        changesTable.rows.filter { it.name == name }.forEach { changesTable.revertChange(it) }

        waitAndClearToast("revert-success")
    }

    fun goToTrackLayout(): E2ETrackLayoutPage {
        logger.info("Return to draft view")
        clickChild(byQaId("go-to-track-layout-view"))
        // utter hack: Somehow the map doesn't update the visible items when clicking that in tests;
        // so let's force
        // it to notice something changed by forcefully wiggling it
        return E2ETrackLayoutPage().also { map -> map.scrollMap(1, 1) }.also { map -> map.scrollMap(-1, -1) }
    }

    fun waitForAllTableValidationsToComplete() = apply {
        logger.info("Waiting for table validations to begin & complete")
        waitUntilChildVisible(byQaId("table-validation-in-progress"))
        waitUntilChildInvisible(byQaId("table-validation-in-progress"))

        return E2EPreviewChangesPage()
    }
}

class E2EPreviewChangesSaveOrDiscardDialog : E2EDialog() {

    fun confirm() {
        logger.info("Confirm preview changes")

        waitUntilClosed {
            childTextInput(byQaId("publication-message")).inputValue("test")
            clickChild(byQaId("publication-confirm"))
        }
    }

    fun reject() = waitUntilClosed {
        logger.info("Reject changes")

        clickWarningButton()
    }
}
