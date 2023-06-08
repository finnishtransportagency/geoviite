package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement


// TODO: row element is directly remembered. Change to fetch-function.
//  This is a little trickier since the use-places tend to fetch list of children only once - it can get stale
//  If target is to just get data, collect it to a no-element structure instead of this component
//  If you want to act on the component, you'll need to add something on the element row to identify it by (a qa-id or row id)
open class TableRow (val headers: List<String>, val row: WebElement) {
    fun clickRow() = row.findElement(By.tagName("td")).click()
    protected fun getColumnByName(name: String): WebElement = row.findElements(By.ByTagName("td"))[colIndx(name)]
    private fun colIndx(name: String): Int = headers.indexOf(name).apply{ if (this == -1) throw RuntimeException("No such column $name")}
}

fun getColumnContent(headers: List<String>, rowElement: WebElement, columnName: String): String =
    getColumnContent(rowElement, columnIndex(headers, columnName))

fun getColumnContent(rowElement: WebElement, columnIndex: Int): String =
    rowElement.findElements(By.ByTagName("td"))[columnIndex].text

fun columnIndex(headers: List<String>, name: String): Int =
    headers.indexOf(name).apply{ if (this == -1) throw RuntimeException("No such column $name")}
