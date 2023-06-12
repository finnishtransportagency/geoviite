package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import getChildWithContentWhenMatches
import getChildrenWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

val listChildElementByLi = By.tagName("li")

interface ListContentItem {
    val index: Int
    val id: Any
}

data class TextListItem(val text: String, override val index: Int, override val id: Any = index): ListContentItem {
    constructor(element: WebElement, index: Int, id: Any = index): this(element.text, index, id)
}

open class ListModel<T: ListContentItem>(
    listFetch: () -> WebElement,
    val itemsBy: By,
    val getContent: (index: Int, child: WebElement) -> T,
): PageModel(listFetch) {
    constructor(
        listParent: WebElement,
        listBy: By,
        itemsBy: By,
        getContent: (index: Int, child: WebElement) -> T,
    ): this(fetch(listParent, listBy), itemsBy, getContent)

    protected val itemElements: List<WebElement> get() = getChildrenWhenVisible(webElement, itemsBy)

    val items: List<T> get() = itemElements.mapIndexed(getContent)

    fun getItemWhenMatches(check: (T) -> Boolean): T =
        getChildWithContentWhenMatches(webElement, itemsBy, getContent, check).second

    fun selectByItemText(text: String) {
//        waitUntilChildMatches(webElement, itemsBy) { e -> e}
//        tryWait(defaultWait, visibi)
//        waitUntilVisible(webElement, By. fg)
    }
//    fun selectByItemChildText(childBy: S)
}
