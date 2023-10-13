package fi.fta.geoviite.infra.ui.pagemodel.common

import elementExists
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.fetch
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndClick
import waitUntilNotVisible
import waitUntilVisible

class E2EDropdown(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {

    private val CONTAINER_BY: By = By.className("dropdown__list-container")

    private val input: E2ETextInput get() = childTextInput(By.tagName("input"))
    private val currentValueHolder: WebElement get() = childElement(By.className("dropdown__current-value"))

    private val optionsList: E2ETextList by lazy {
        E2ETextList(fetch(CONTAINER_BY), By.className("dropdown__list-item"))
    }

    val options: List<E2ETextListItem> get() = optionsList.items

    val value: String
        get() {
            logger.info("Current value ${input.value}")
            return input.value
        }

    fun open(): E2EDropdown = apply {
        logger.info("Open dropdown")

        if (!elementExists(CONTAINER_BY)) {
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
        waitUntilNotVisible(By.className("dropdown__loading-indicator"))
        optionsList.selectByTextWhenContains(name)
        // can't use optionsList directly, as it contains a loading placeholder element that goes stale once the list
        // has loaded

        //fetch(elementFetch, CONTAINER_BY)().getChildElement(By.xpath(".//span[contains(text(), \"$name\")]")).click()
    }

    fun new() {
        logger.info("Add new item")
        open()
        getElementWhenVisible(By.cssSelector(".dropdown__add-new-container > button")).waitAndClick()
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
