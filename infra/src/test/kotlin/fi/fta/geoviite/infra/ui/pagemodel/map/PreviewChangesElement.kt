package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.TableRow
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ChangePreviewTable(val table: WebElement) {
    private val logger: Logger = LoggerFactory.getLogger(ChangePreviewTable::class.java)

    fun changeRows(): List<ChangePreviewRow> {
        val header = header()
        return rowElements().map { rowElement -> ChangePreviewRow(header, rowElement) }
    }

    fun errorRows(): List<String> =
        table.findElements(By.cssSelector("div.preview-table-item__msg-group--errors"))
            .map { errorRow ->
                errorRow.findElements(By.cssSelector("div.preview-table-item__msg"))
                    .joinToString { it.text + "\n" }
            }

    fun hasErrors():Boolean {
        if(rowElements().isEmpty()) return false

        return try {
            table.findElement(By.cssSelector("span.preview-table-item__error-status"))
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

class ChangePreviewRow(header: List<String>, row: WebElement): TableRow(header, row) {
    enum class Tila {OK, ERRORS}

    fun muutoskohde(): String = getColumnByName("Muutoskohde").text
    fun ratanumero(): String = getColumnByName("Ratanro").text
    fun tila(openErros: Boolean = true): Tila{
        val col = getColumnByName("Tila")

        //Check if row has errors and in that case click Tila-col to open errors into view
        return try {
            col.findElement(By.cssSelector("span.preview-table-item__error-status"))
            if (openErros) col.click()
            Tila.ERRORS

        } catch (ex: NoSuchElementException) {
            Tila.OK
        }
    }
}



