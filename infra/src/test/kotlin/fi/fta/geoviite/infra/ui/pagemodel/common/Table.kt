package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement


// TODO: This exposes elements directly to outside the class with a stale-risk
// TODO: The table columns should be listed as an enum, typing the row with it TableRow<MyTableColumnsEnum>
//  Each enum value can than hold the qa-id for that column and those can be used to pick the data
@Deprecated("Element risks staleness")
open class TableRow (val headers: List<String>, val row: WebElement) {
    fun clickRow() = row.findElement(By.tagName("td")).click()
    protected fun getColumnByName(name: String): WebElement = row.findElements(By.ByTagName("td"))[colIndx(name)]
    private fun colIndx(name: String): Int = headers.indexOf(name).apply{ if (this == -1) throw RuntimeException("No such column $name")}
}

// TODO: The columns should not be indexed by text-content, but by qa-id (need to be added)
fun getColumnContent(headers: List<String>, rowElement: WebElement, columnName: String): String =
    getColumnContent(rowElement, columnIndex(headers, columnName))

fun getColumnContent(rowElement: WebElement, columnIndex: Int): String =
    rowElement.findElements(By.ByTagName("td"))[columnIndex].text

fun columnIndex(headers: List<String>, name: String): Int =
    headers.indexOf(name).apply{ if (this == -1) throw RuntimeException("No such column $name")}
