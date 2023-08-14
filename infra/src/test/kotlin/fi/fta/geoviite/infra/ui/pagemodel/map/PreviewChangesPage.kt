package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EToaster
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.byText
import getElementWhenClickable
import getElementWhenVisible
import org.openqa.selenium.By
import waitAndGetToasterElement

class E2EPreviewChangesPage : E2EViewFragment(byQaId("preview-content")) {

    //Page is unstable before preview change table is loaded
    init {
        waitChildVisible(By.cssSelector("table.table"))
    }

    fun publish(): E2EToaster {
        logger.info("Publishing changes")
        val changesTable = changesTable()
        //Iterating rows is slow, so we quickly check if iterating is even necessary
        if (changesTable.hasErrors()) {
            //changesTable.changeRows().forEach{ row -> assertThat(row.muutokset()).withFailMessage { "${row.muutoskohde()} has changes '${row.muutokset()}' that prevent publishing" }.isBlank()}
            changesTable.changeRows().forEach { row -> row.getState() }
            val errors = changesTable.errorRows()
            throw AssertionError("Following changes prevent publishing \n $errors")
        }

        clickButtonByText("Julkaise")

        E2EPreviewChangesSaveOrDiscardDialog().confirm()
        return waitAndGetToasterElement()
    }

    fun stageChanges() {
        logger.info("Stage changes")
        changesTable().changeRows().forEach { row -> row.stage() }
    }

    fun revertChanges(name: String) {
        logger.info("Revert $name")
        val row = changesTable().changeRows().find { row -> row.name.contains(name) }
            ?: stagedChangesTable().changeRows().find { row -> row.name.contains(name) }
        row?.openMenu()
        getElementWhenClickable(By.xpath("//div[text() = 'Hylkää muutos']")).click()
        E2EPreviewChangesSaveOrDiscardDialog().reject()
    }

    fun revertChanges() {
        val changes = changesTable().changeRows().map { row -> row.name }
        changes.forEach { change -> revertChanges(change) }
    }

    fun goToTrackLayout() {
        logger.info("Return to draft view")
        getElementWhenVisible(By.xpath("//span[text() = 'Palaa luonnostilaan']")).click()
        // utter hack: Somehow the map doesn't update the visible items when clicking that in tests; so let's force
        // it to notice something changed by forcefully wiggling it
        E2ETrackLayoutPage().also { map -> map.scrollMap(1, 1) }.also { map -> map.scrollMap(-1, -1) }
    }

    fun changesTable(): E2EChangePreviewTable =
        E2EChangePreviewTable(getElementWhenVisible(By.cssSelector("section[qa-id=unstaged-changes] table.table")))

    fun stagedChangesTable(): E2EChangePreviewTable =
        E2EChangePreviewTable(getElementWhenVisible(By.cssSelector("section[qa-id=staged-changes] table.table")))

    fun logChanges() = changesTable().changeRows().forEach { logger.info(it.toString()) }

}

class E2EPreviewChangesSaveOrDiscardDialog : E2EDialog() {

    fun confirm() {
        val messageBox = getElementWhenVisible(By.cssSelector("textarea[qa-id=publication-message]"))
        messageBox.click()
        messageBox.sendKeys("test")
        clickButtonByQaId("publication-confirm")
    }

    fun reject() = childButton(byText("Hylkää")).clickAndWaitToDisappear()
}
