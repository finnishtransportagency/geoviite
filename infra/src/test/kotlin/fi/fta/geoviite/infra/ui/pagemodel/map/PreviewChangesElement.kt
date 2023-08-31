package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.OldTableRow
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class E2EChangePreviewTable(private val table: WebElement) {
    private val logger: Logger = LoggerFactory.getLogger(E2EChangePreviewTable::class.java)

    // TODO: GVT-1935 These row elements hold a reference to the WebElement, risking staleness. Use TableModel to replace this.
    @Deprecated("Element risks staleness")
    fun changeRows(): List<E2EChangePreviewRow> {
        val header = header()
        return rowElements().map { rowElement -> E2EChangePreviewRow(header, rowElement) }
    }

    fun errorRows(): List<String> =
        table.findElements(By.className("preview-table-item__msg-group--errors")).map { errorRow ->
            errorRow.findElements(By.className("preview-table-item__msg")).joinToString { it.text + "\n" }
        }

    fun hasErrors(): Boolean {
        if (rowElements().isEmpty()) return false

        return try {
            table.findElement(By.className("preview-table-item__error-status"))
            logger.warn("Table has errors")
            true
        } catch (ex: NoSuchElementException) {
            logger.info("No errors")
            false
        }
    }

    private fun rowElements() = table.findElements(By.cssSelector("table tbody tr"))
    private fun header() = table.findElements(By.cssSelector("table thead tr th")).map { it.text }
}

// TODO: GVT-1935 These row elements hold a reference to the WebElement, risking staleness. Use TableModel to replace this.
class E2EChangePreviewRow(header: List<String>, val row: WebElement) : OldTableRow(header, row) {
    enum class State { OK, ERRORS }

    val name: String get() = getColumnByName("Muutoskohde").text
    val trackNumber: String get() = getColumnByName("Ratanro").text
    fun getState(openErrors: Boolean = true): State {
        val col = getColumnByName("Tila")

        //Check if row has errors and in that case click Tila-col to open errors into view
        return try {
            col.findElement(By.className("preview-table-item__error-status"))
            if (openErrors) col.click()
            State.ERRORS
        } catch (ex: NoSuchElementException) {
            State.OK
        }
    }

    fun stage(): E2EChangePreviewRow = apply {
        row.findElement(byQaId("stage-change-button")).click()
    }

    fun openMenu(): E2EChangePreviewRow = apply {
        row.findElement(byQaId("menu-button")).click()
    }
}
