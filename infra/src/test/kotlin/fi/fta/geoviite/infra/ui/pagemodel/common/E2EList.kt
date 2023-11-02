package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions.numberOfElementsToBe
import tryWait

val byLiTag: By = By.tagName("li")

data class E2ETextListItem(val text: String) {
    constructor(element: WebElement) : this(element.text)
}

class E2ETextList(listBy: By, itemsBy: By = byLiTag) : E2EList<E2ETextListItem>(listBy, itemsBy) {
    fun selectByTextWhenMatches(text: String) = select { i -> i.text == text }

    fun selectByTextWhenContains(text: String) = select { i -> i.text.contains(text) }

    override fun getItemContent(item: WebElement): E2ETextListItem {
        return E2ETextListItem(item)
    }
}

abstract class E2EList<T>(listBy: By, val itemsBy: By) : E2EViewFragment(listBy) {

    private val itemElements: List<Pair<WebElement, T>> get() = childElements(itemsBy).map { it to getItemContent(it) }

    val items: List<T> get() = itemElements.map { it.second }

    protected abstract fun getItemContent(item: WebElement): T

    fun waitUntilItemMatches(check: (T) -> Boolean): E2EList<T> = apply {
        getItemWhenMatches(check)
    }

    fun waitUntilCount(count: Int): E2EList<T> = apply {
        tryWait(numberOfElementsToBe(childBy(itemsBy), count)) {
            "Count did not become $count. Count: ${items.count()}"
        }
    }

    private fun getElementWhenMatches(check: (T) -> Boolean): WebElement =
        tryWait(
            condition = { itemElements.firstOrNull { check(it.second) }?.first },
            lazyErrorMessage = { "No such element in items list. Items: $itemElements" }
        )

    fun getItemWhenMatches(check: (T) -> Boolean): T = tryWait<T>(
        condition = { items.firstOrNull { check(it) } },
        lazyErrorMessage = { "No such element in items list. Items: $items" }
    )

    fun select(check: (T) -> Boolean) = select(getItemWhenMatches(check))

    open fun select(item: T): E2EList<T> = apply {
        getElementWhenMatches { it == item }.also { e ->
            if (!isSelected(e)) e.click()
        }
    }

    open fun selectBy(item: T, by: By): E2EList<T> = apply {
        getElementWhenMatches { it == item }.also { e ->
            if (!isSelected(e)) e.findElement(by).click()
        }
    }

    open fun isSelected(element: WebElement): Boolean = false
}
