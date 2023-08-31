package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndClick

val byLiTag: By = By.tagName("li")

interface E2EListItem {
    val index: Int
}

data class E2ETextListItem(val text: String, override val index: Int) : E2EListItem {
    constructor(element: WebElement, index: Int) : this(element.text, index)
}

class E2ETextList(listFetch: ElementFetch, itemsBy: By = byLiTag) : E2EList<E2ETextListItem>(
    listFetch = listFetch,
    itemsBy = itemsBy,
    getContent = { i, e -> E2ETextListItem(e, i) },
) {
    fun selectByTextWhenMatches(text: String) = selectItemWhenMatches { i -> i.text == text }
}

open class E2EList<T : E2EListItem>(
    listFetch: ElementFetch,
    val itemsBy: By,
    val getContent: (index: Int, child: WebElement) -> T,
) : E2EViewFragment(listFetch) {
    
    private val itemElements: List<WebElement> get() = childElements(itemsBy)

    val items: List<T> get() = itemElements.mapIndexed(getContent)

    fun waitUntilItemMatches(check: (T) -> Boolean): E2EList<T> = apply {
        getItemWhenMatches(check)
    }

    fun getItemWhenMatches(check: (T) -> Boolean): T = items.first { check(it) }

    fun selectItemWhenMatches(check: (T) -> Boolean) = select(getItemWhenMatches(check))

    fun select(item: T) = select(item.index)

    open fun select(index: Int): E2EList<T> = apply {
        itemElements[index].let { e ->
            if (!isSelected(e)) e.waitAndClick()
        }
    }

    // TODO: GVT-1935 Implement a generic way to communicate selection in lists from UI to tests
    protected open fun isSelected(element: WebElement) = false
}
