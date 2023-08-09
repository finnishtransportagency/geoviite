package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement


// TODO: GVT-1936 The table handling should be refactored
//   This exposes elements directly to outside the class with a stale-risk
@Deprecated("Element risks staleness")
open class TableRow(private val headers: List<String>, private val row: WebElement) {
    fun clickRow() = row.findElement(By.tagName("td")).click()
    protected fun getColumnByName(name: String): WebElement = row.findElements(By.ByTagName("td"))[colIdx(name)]
    private fun colIdx(name: String): Int =
        headers.indexOf(name).apply { if (this == -1) throw RuntimeException("No such column $name") }
}

fun getColumnContent(headers: List<String>, rowElement: WebElement, columnName: String): String =
    getColumnContent(rowElement, columnIndex(headers, columnName))

fun getColumnContent(rowElement: WebElement, columnIndex: Int): String =
    rowElement.findElements(By.ByTagName("td"))[columnIndex].text

fun columnIndex(headers: List<String>, name: String): Int =
    headers.indexOf(name).apply { if (this == -1) throw RuntimeException("No such column $name") }
