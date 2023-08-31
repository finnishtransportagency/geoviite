package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

abstract class E2ETable<T>(
    tableFetch: ElementFetch,
    rowsBy: By = By.tagName("tr"),
    private val headersBy: By = By.tagName("th"),
) : E2EList<T>(tableFetch, rowsBy) {
    val rows: List<T> get() = items

    protected val headerElements: List<WebElement> get() = childElements(headersBy)

    abstract fun getRowContent(row: WebElement): T

    override fun getItemContent(item: WebElement) = getRowContent(item)
    
    
    //Firefox doesn't handle tr clicks correctly, temporary fixed by clicking on the first td
    //https://bugzilla.mozilla.org/show_bug.cgi?id=1448825
    override fun select(item: T): E2ETable<T> = apply {
        selectBy(item, By.tagName("td"))
    }

    fun waitUntilReady(): E2ETable<T> = apply {
        waitChildNotVisible(By.className("table--loading"))
    }
}

fun getColumnIndex(
    columnName: String,
    headers: List<WebElement>,
) = headers.indexOfFirst { it.text == columnName }
    .also { check(it != -1) { "No header with text $columnName" } }

fun getColumnIndexByAttr(
    attrValue: String,
    headers: List<WebElement>,
    attrName: String = "qa-id",
) = headers.indexOfFirst { it.getAttribute(attrName) == attrValue }
    .also { check(it != -1) { "No header attribute ($attrName) matched with $attrValue" } }

fun getColumnContentByAttr(
    attrValue: String,
    columns: List<WebElement>,
    headers: List<WebElement>,
    attrName: String = "qa-id",
): String {
    return columns[getColumnIndexByAttr(attrValue, headers, attrName)].text
}

fun getColumnContent(columnName: String, columns: List<WebElement>, headers: List<WebElement>): String =
    columns[getColumnIndex(columnName, headers)].text
