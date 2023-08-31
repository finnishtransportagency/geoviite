package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import tryWait
import waitAndClick

val byLiTag: By = By.tagName("li")

data class E2ETextListItem(val text: String) {
    constructor(element: WebElement) : this(element.text)
}

class E2ETextList(listFetch: ElementFetch, itemsBy: By = byLiTag) : E2EList<E2ETextListItem>(listFetch, itemsBy) {
    fun selectByTextWhenMatches(text: String) = selectItemWhenMatches { i -> i.text == text }

    fun selectByTextWhenContains(text: String) = selectItemWhenMatches { i -> i.text.contains(text) }

    override fun getItemContent(item: WebElement): E2ETextListItem {
        return E2ETextListItem(item)
    }
}

abstract class E2EList<T>(listFetch: ElementFetch, val itemsBy: By) : E2EViewFragment(listFetch) {

    private val itemElements: List<Pair<WebElement, T>> get() = childElements(itemsBy).map { it to getItemContent(it) }

    val items: List<T> get() = itemElements.map { it.second }

    protected abstract fun getItemContent(item: WebElement): T

    fun waitUntilItemMatches(check: (T) -> Boolean): E2EList<T> = apply {
        getItemWhenMatches(check)
    }

    private fun getElementWhenMatches(check: (T) -> Boolean): WebElement =
        tryWait<WebElement>(
            { itemElements.firstOrNull { check(it.second) }?.first },
            { "No such element in items list. Items: $itemElements" }
        )

    fun getItemWhenMatches(check: (T) -> Boolean): T = tryWait<T>(
        { items.firstOrNull { check(it) } },
        { "No such element in items list. Items: $items" }
    )

    fun selectItemWhenMatches(check: (T) -> Boolean) = select(getItemWhenMatches(check))

    open fun select(item: T): E2EList<T> = apply {
        getElementWhenMatches { it == item }.also { e ->
            if (!isSelected(e)) e.waitAndClick()
        }
    }

    open fun selectBy(item: T, by: By): E2EList<T> = apply {
        getElementWhenMatches { it == item }.also { e ->
            if (!isSelected(e)) e.findElement(by).waitAndClick()
        }
    }

    open fun isSelected(element: WebElement): Boolean = false
}
