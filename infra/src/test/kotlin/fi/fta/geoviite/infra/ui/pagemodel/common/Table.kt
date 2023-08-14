package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndClick

open class E2ETable<T : E2ETableRow>(
    tableFetch: ElementFetch,
    getContent: (index: Int, child: WebElement) -> T,
    rowsBy: By = By.tagName("tr"),
) : E2EList<T>(tableFetch, rowsBy, getContent) {
    val rows: List<T> get() = items

    //Firefox doesn't handle tr clicks correctly, temporary fixed by clicking on the first td
    //https://bugzilla.mozilla.org/show_bug.cgi?id=1448825
    override fun select(index: Int): E2ETable<T> = apply {
        childElements(itemsBy)[index].let { e ->
            if (!isSelected(e)) e.findElement(By.tagName("td")).waitAndClick()
        }
    }

}

open class E2ETableRow(
    override val index: Int,
    row: WebElement,
    protected open val headers: List<String>,
) : E2EListItem {
    private val columnTexts = row.findElements(By.tagName("td")).map { it.text }

    private fun columnIndex(name: String) = columnIndex(headers, name)

    fun getColumnContent(name: String): String {
        return columnTexts[columnIndex(name)]
    }
}
