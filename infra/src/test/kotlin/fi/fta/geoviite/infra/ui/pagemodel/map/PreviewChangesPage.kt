package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.DialogPopUp
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.Toaster
import org.openqa.selenium.By

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

    fun hylkaaMuutokset(): Toaster {
        logger.info("Discard changes")
        getElementWhenClickable(By.xpath("//span[text() = 'Hylkää muutokset']")).click()
        PreviewChangesSaveOrDiscardDialog().hylkaaKaikki()
        return waitAndGetToasterElement()
    }

    fun palaaLuonnostilaan() {
        logger.info("Return to draft view")
        getElementWhenVisible(By.xpath("//span[text() = 'Palaa luonnostilaan']")).click()
    }

    fun changesTable(): ChangePreviewTable =
        ChangePreviewTable(getElementWhenVisible(By.cssSelector("div.preview-view__changes table.table")))

    fun logChanges() =
        changesTable().changeRows().forEach { logger.info(it.toString()) }

}

class PreviewChangesSaveOrDiscardDialog(): DialogPopUp() {

    fun julkaise() =
        clickButton("Julkaise")

    fun hylkaaKaikki() =
        clickButton("Hylkää kaikki")

}
