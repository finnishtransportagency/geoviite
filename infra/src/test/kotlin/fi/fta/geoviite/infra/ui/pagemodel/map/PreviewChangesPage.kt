package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.DialogPopUp
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.Toaster
import getElementWhenClickable
import getElementWhenVisible
import org.openqa.selenium.By
import waitAndGetToasterElement

class PreviewChangesPage: PageModel(By.xpath("//div[@qa-id='preview-content']")) {

    //Page is unstable before preview change table is loaded
    init {
        getElementWhenVisible(By.cssSelector("table.table"))
    }

    fun julkaise(): Toaster {
        logger.info("Publish changes")
        logger.info("Check is publishing possible")
        val changesTable = changesTable()
        //Iterating rows is slow, so we quickly check if iterating is even necessary
        if (changesTable.hasErrors()) {
            //changesTable.changeRows().forEach{ row -> assertThat(row.muutokset()).withFailMessage { "${row.muutoskohde()} has changes '${row.muutokset()}' that prevent publishing" }.isBlank()}
            changesTable.changeRows().forEach{ row -> row.tila()}
            val errors = changesTable.errorRows()
            throw AssertionError("Following changes prevent publishing \n $errors")
        }

        clickButton("Julkaise")

        PreviewChangesSaveOrDiscardDialog().julkaise()
        return waitAndGetToasterElement()
    }

    fun lisaaMuutoksetJulkaisuun() {
        logger.info("Stage changes")
        changesTable().changeRows().forEach {row -> row.nuolinappi().click()}
    }

    fun hylkaaMuutos(name: String) {
        logger.info("Revert ${name}")
        val row = changesTable().changeRows().find { row -> row.muutoskohde().contains(name) }
            ?: stagedChangesTable().changeRows().find { row -> row.muutoskohde().contains(name) }
        row?.menu()?.click()
        getElementWhenClickable(By.xpath("//div[text() = 'Hylkää muutos']")).click()
        PreviewChangesSaveOrDiscardDialog().hylkaa()
        waitAndGetToasterElement()
    }

    fun hylkaaMuutokset() {
        val changes = changesTable().changeRows().map { row -> row.muutoskohde() }
        changes.forEach { change -> hylkaaMuutos(change) }
    }

    fun palaaLuonnostilaan() {
        logger.info("Return to draft view")
        getElementWhenVisible(By.xpath("//span[text() = 'Palaa luonnostilaan']")).click()
        // utter hack: Somehow the map doesn't update the visible items when clicking that in tests; so let's force
        // it to notice something changed by forcefully wiggling it
        MapPage().also { map -> map.scrollMap(1, 1) }.also { map -> map.scrollMap(-1, -1) }
    }

    fun changesTable(): ChangePreviewTable =
        ChangePreviewTable(getElementWhenVisible(By.cssSelector("section[qa-id=unstaged-changes] table.table")))

    fun stagedChangesTable(): ChangePreviewTable =
        ChangePreviewTable(getElementWhenVisible(By.cssSelector("section[qa-id=staged-changes] table.table")))

    fun logChanges() =
        changesTable().changeRows().forEach { logger.info(it.toString()) }

}

class PreviewChangesSaveOrDiscardDialog: DialogPopUp() {

    fun julkaise() {
        val messageBox = getElementWhenVisible(By.cssSelector("textarea[qa-id=publication-message]"))
        messageBox.click()
        messageBox.sendKeys("test")
        clickButtonByQaId("publication-confirm")
    }

    fun hylkaa() =
        clickButton("Hylkää")

}
