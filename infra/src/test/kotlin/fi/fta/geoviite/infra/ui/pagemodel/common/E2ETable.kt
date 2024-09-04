package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement

abstract class E2ETable<T>(
    tableBy: By = By.tagName("table"),
    rowsBy: By = By.tagName("tr"),
    protected open val headersBy: By = By.tagName("th"),
) : E2EList<T>(tableBy, rowsBy) {

    init {
        waitUntilReady()
    }

    val rows: List<T>
        get() = items

    protected val headerElements: List<WebElement>
        get() = childElements(headersBy)

    abstract fun getRowContent(row: WebElement): T

    override fun getItemContent(item: WebElement) = getRowContent(item)

    // Firefox doesn't handle tr clicks correctly, temporary fixed by clicking on the first td
    // https://bugzilla.mozilla.org/show_bug.cgi?id=1448825
    override fun select(item: T): E2ETable<T> = apply { selectBy(item, By.tagName("td")) }

    fun waitUntilReady(): E2ETable<T> = apply {
        logger.info("Wait until table has finished loading")

        waitUntilChildInvisible(By.className("table--loading"))
    }
}

fun getColumnIndex(qaId: String, headers: List<WebElement>) =
    headers
        .indexOfFirst { it.getAttribute("qa-id") == qaId }
        .also { idx ->
            check(idx != -1) { "No header found with qa-id $qaId. Header: ${headers.map { it.getAttribute("qa-id") }}" }
        }

fun getColumnContent(qaId: String, columns: List<WebElement>, headers: List<WebElement>): String {
    return columns[getColumnIndex(qaId, headers)].text
}
