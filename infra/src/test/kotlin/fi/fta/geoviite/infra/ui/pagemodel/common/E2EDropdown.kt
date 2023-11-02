package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import exists
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitUntilNotVisible
import waitUntilVisible

private val CONTAINER_BY: By = By.className("dropdown__list-container")

class E2EDropdown(dropdownBy: By) : E2EViewFragment(dropdownBy) {

    private val input: E2ETextInput get() = childTextInput(By.tagName("input"))
    private val currentValueHolder: WebElement get() = childElement(By.className("dropdown__current-value"))

    private val optionsList: E2ETextList by lazy {
        E2ETextList(CONTAINER_BY, By.className("dropdown__list-item"))
    }

    val options: List<E2ETextListItem> get() = optionsList.items

    val value: String
        get() {
            logger.info("Current value ${input.value}")
            return input.value
        }

    fun open(): E2EDropdown = apply {
        logger.info("Open dropdown")

        if (!exists(CONTAINER_BY)) {
            clickChild(By.className("dropdown__header"))
            waitUntilVisible(CONTAINER_BY)
        }
    }

    fun select(name: String): E2EDropdown = apply {
        logger.info("Select item $name")
        open()
        optionsList.selectByTextWhenContains(name)
        waitUntilNotVisible(CONTAINER_BY)
    }

    fun selectFromDynamicByName(name: String): E2EDropdown = apply {
        logger.info("Select item $name from dynamic dropdown")
        input.inputValue(name)
        // can't use optionsList directly, as it contains a loading placeholder element that goes stale once the list
        // has loaded
        waitUntilNotVisible(By.className("dropdown__loading-indicator"))
        optionsList.selectByTextWhenContains(name)
    }

    fun new() {
        logger.info("Add new item")
        open()
        clickWhenClickable(By.cssSelector(".dropdown__add-new-container > button"))
    }

    fun inputValue(text: String): E2EDropdown = apply {
        logger.info("Input text $text")
        input.inputValue(text)
    }

    fun clearInput(): E2EDropdown = apply {
        if (!currentValueHolder.isDisplayed) {
            input.clear()
        }
    }
}
