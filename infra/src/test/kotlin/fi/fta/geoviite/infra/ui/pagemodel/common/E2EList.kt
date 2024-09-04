package fi.fta.geoviite.infra.ui.pagemodel.common

import getElementIfExists
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions.not
import org.openqa.selenium.support.ui.ExpectedConditions.numberOfElementsToBe
import tryWait

val byLiTag: By = By.tagName("li")

data class E2ETextListItem(val text: String) {
    constructor(element: WebElement) : this(element.text)
}

class E2ETextList(listBy: By, itemsBy: By = byLiTag, selectedItemBy: By) :
    E2EList<E2ETextListItem>(listBy, itemsBy, selectedItemBy) {

    fun selectByText(text: String): E2EList<E2ETextListItem> {
        logger.info("Select item that contains $text")

        return select { it.text.contains(text) }
    }

    fun unselectByText(text: String): E2EList<E2ETextListItem> {
        logger.info("Unselect item that contains $text")

        return unselect { it.text.contains(text) }
    }

    override fun getItemContent(item: WebElement) = E2ETextListItem(item)
}

abstract class E2EList<T>(listBy: By, val itemsBy: By, val selectedItemBy: By? = null) : E2EViewFragment(listBy) {

    private val itemElements: List<Pair<WebElement, T>>
        get() = childElements(itemsBy).map { it to getItemContent(it) }

    val items: List<T>
        get() = itemElements.map { it.second }

    protected abstract fun getItemContent(item: WebElement): T

    fun waitUntilItemMatches(check: (T) -> Boolean): E2EList<T> = apply {
        logger.info("Wait until item matches")

        getItemWhenMatches(check)
    }

    fun waitUntilItemCount(count: Int): E2EList<T> = apply {
        logger.info("Wait until item count is $count")

        tryWait(numberOfElementsToBe(childBy(itemsBy), count)) {
            "Count did not become $count. Count: ${items.count()}"
        }
    }

    fun waitUntilItemIsRemoved(check: (T) -> Boolean) = apply {
        logger.info("Wait until item doesn't exist")

        tryWait(not { itemElements.firstOrNull { (_, item) -> check(item) } }) { "Item still exists" }
    }

    fun getElementWhenMatches(check: (T) -> Boolean): Pair<WebElement, T> =
        tryWait({ itemElements.firstOrNull { (_, item) -> check(item) } }) {
            "No such element in items list. Items: $itemElements"
        }

    fun getItemWhenMatches(check: (T) -> Boolean): T = getElementWhenMatches(check).second

    fun select(check: (T) -> Boolean) = select(getItemWhenMatches(check))

    fun unselect(check: (T) -> Boolean) = unselect(getItemWhenMatches(check))

    private fun itemTextContains(container: WebElement, text: String): Boolean {
        return container.text.contains(text)
    }

    open fun select(item: T): E2EList<T> = apply {
        logger.info("Select item $item")

        getElementWhenMatches { it == item }
            .first
            .let { match ->
                val selectedItem = selectedItemBy?.let { getElementIfExists(selectedItemBy) }
                if (selectedItem == null || selectedItem.let { selected -> !itemTextContains(match, selected.text) })
                    match.click()
            }
    }

    open fun unselect(item: T): E2EList<T> = apply {
        logger.info("Unselect item $item")

        getElementWhenMatches { it == item }
            .first
            .let { match ->
                val selectedItem = selectedItemBy?.let { getElementIfExists(selectedItemBy) }
                if (
                    selectedItemBy != null &&
                        selectedItem?.let { selected -> itemTextContains(match, selected.text) } ?: false
                )
                    match.click()
            }
    }

    open fun selectBy(item: T, by: By): E2EList<T> = apply {
        logger.info("Select item $item")

        getElementWhenMatches { it == item }.first.findElement(by).click()
    }
}
