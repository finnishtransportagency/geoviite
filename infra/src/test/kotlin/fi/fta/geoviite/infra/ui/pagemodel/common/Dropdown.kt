package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EDropdown(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {

    private val CONTAINER_BY: By = By.className("dropdown__list-container")

    private val input: E2ETextInput get() = childTextInput(By.tagName("input"))
    private val currentValueHolder: WebElement get() = childElement(By.className("dropdown__current-value"))

    private val optionsList: E2ETextList by lazy {
        E2ETextList(fetch(elementFetch, CONTAINER_BY), By.className("dropdown__list-item"))
    }

    val options: List<E2ETextListItem> get() = optionsList.items

    val value: String
        get() {
            logger.info("Current value ${input.value}")
            return input.value
        }

    fun open(): E2EDropdown = apply {
        logger.info("Open dropdown")

        if (!childExists(CONTAINER_BY)) {
            clickChild(By.className("dropdown__header"))
            waitChildVisible(CONTAINER_BY)
        }
    }

    fun select(name: String): E2EDropdown = apply {
        logger.info("Select item $name")
        open()
        optionsList.selectByTextWhenContains(name)
        waitChildNotVisible(CONTAINER_BY)
    }

    fun new() {
        logger.info("Add new item")
        clickChild(By.cssSelector(".dropdown__add-new-container > button"))
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
