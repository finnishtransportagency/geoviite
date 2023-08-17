package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

open class TableModel<T: TableRowItem>(
    listFetch: () -> WebElement,
    itemsBy: By = By.tagName("tr"),
    getContent: (index: Int, child: WebElement) -> T,
) : ListModel<T>(listFetch, itemsBy, getContent) {

    constructor(
        listBy: By,
        itemsBy: By,
        getContent: (index: Int, child: WebElement) -> T,
    ) : this(fetch(listBy), itemsBy, getContent)

}

open class TableRowItem(
    override val index: Int,
    row: WebElement,
    protected open val headers: List<String>,
) : ListContentItem {
    private val columnTexts = row.findElements(By.tagName("td")).map { column -> column.text }

    private fun columnIndex(name: String): Int = columnIndex(headers, name)

    fun getColumnContent(name: String): String {
        return columnTexts[columnIndex(name)]
    }
}
