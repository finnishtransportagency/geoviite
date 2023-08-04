package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndClick

val byLiTag: By = By.tagName("li")

interface ListContentItem {
    val index: Int
}

data class TextListItem(val text: String, override val index: Int) : ListContentItem {
    constructor(element: WebElement, index: Int) : this(element.text, index)
}

class TextList(listFetch: () -> WebElement, itemsBy: By = byLiTag) : ListModel<TextListItem>(
    listFetch = listFetch,
    itemsBy = itemsBy,
    getContent = { i, e -> TextListItem(e, i) },
) {
    constructor(parent: () -> WebElement, listBy: By, itemsBy: By = byLiTag) : this(fetch(parent, listBy), itemsBy)
    constructor(listBy: By, itemsBy: By = byLiTag) : this(fetch(listBy), itemsBy)

    fun selectByTextWhenMatches(text: String) = selectItemWhenMatches { i -> i.text == text }
}

open class ListModel<T : ListContentItem>(
    listFetch: () -> WebElement,
    val itemsBy: By,
    val getContent: (index: Int, child: WebElement) -> T,
) : PageModel(listFetch) {

    constructor(
        listBy: By,
        itemsBy: By,
        getContent: (index: Int, child: WebElement) -> T,
    ) : this(fetch(listBy), itemsBy, getContent)

    protected val itemElements: List<WebElement> get() = childElements(itemsBy)

    val items: List<T> get() = itemElements.mapIndexed(getContent)

    fun waitUntilItemMatches(check: (T) -> Boolean): Unit = getItemWhenMatches(check).let {}

    fun getItemWhenMatches(check: (T) -> Boolean): T = getElementWhenMatches(check).let { (i, e) -> getContent(i, e) }

    fun selectItemWhenMatches(check: (T) -> Boolean) = select(getElementWhenMatches(check).first)

    fun select(item: T) = select(item.index)

    fun select(index: Int) = itemElements[index].let { e ->
        if (!isSelected(e)) e.waitAndClick()
    }

    fun clickOnItemBy(selectItem: (T) -> Boolean, by: By) {
        waitUntilItemMatches(selectItem)
        val index = items.indexOfFirst(selectItem)
        itemElements[index].findElement(by).click()
    }

    // TODO: GVT-1935 Implement a generic way to communicate selection in lists from UI to tests
    protected open fun isSelected(element: WebElement) = false

    protected fun getElementWhenMatches(check: (content: T) -> Boolean): Pair<Int, WebElement> =
        childElementWhenMatches(itemsBy, { i, e -> check(getContent(i, e)) })

}
