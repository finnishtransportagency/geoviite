package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToastByContent
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import java.time.Duration

class E2EPreviewChangesPage : E2EViewFragment(byQaId("preview-content")) {

    val changesTable: E2EChangePreviewTable by lazy {
        waitChildNotVisible(By.className("preview-section__spinner-container"), Duration.ofSeconds(10L))
        E2EChangePreviewTable(fetch(elementFetch, By.cssSelector("[qa-id='unstaged-changes'] table")))
    }

    val stagedChangesTable: E2EChangePreviewTable by lazy {
        waitChildNotVisible(By.className("preview-section__spinner-container"), Duration.ofSeconds(10L))
        E2EChangePreviewTable(fetch(elementFetch, By.cssSelector("[qa-id='staged-changes'] table")))
    }

    fun publish(): E2ETrackLayoutPage {
        logger.info("Publishing changes")
        //Iterating rows is slow, so we quickly check if iterating is even necessary
        if (stagedChangesTable.hasErrors()) {
            throw AssertionError("Following changes prevent publishing \n ${changesTable.errorRows}")
        }

        clickChild(By.cssSelector(".preview-footer__action-buttons button"))

        E2EPreviewChangesSaveOrDiscardDialog().confirm()

        waitAndClearToastByContent("Muutokset julkaistu paikannuspohjaan")

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
        logger.info("Reverting staged change $name")
        stagedChangesTable.rows
            .filter { it.name == name }
            .forEach { stagedChangesTable.revertChange(it) }
    }

    fun revertChange(name: String): E2EPreviewChangesPage = apply {
        logger.info("Reverting change $name")
        changesTable.rows.filter { it.name == name }.forEach { changesTable.revertChange(it) }

        waitAndClearToastByContent("Luonnosmuutokset peruttu")
    }

    fun goToTrackLayout(): E2ETrackLayoutPage {
        logger.info("Return to draft view")
        clickChild(By.xpath("//span[text() = 'Palaa luonnostilaan']"))
        // utter hack: Somehow the map doesn't update the visible items when clicking that in tests; so let's force
        // it to notice something changed by forcefully wiggling it
        return E2ETrackLayoutPage()
            .also { map -> map.scrollMap(1, 1) }
            .also { map -> map.scrollMap(-1, -1) }
    }
}

class E2EPreviewChangesSaveOrDiscardDialog : E2EDialog() {

    fun confirm() {
        waitUntilClosed {
            childTextInput(byQaId("publication-message")).inputValue("test")
            clickButtonByQaId("publication-confirm")
        }
    }

    fun reject() = waitUntilClosed {
        clickWarningButton()
    }
}
