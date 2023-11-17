package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import exists
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained
import org.openqa.selenium.support.ui.ExpectedConditions
import tryWait
import waitUntilNotVisible
import waitUntilTextExists
import waitUntilVisible

private val CONTAINER_BY: By = By.className("dropdown__list-container")

class E2EDropdown(dropdownBy: By) : E2EViewFragment(dropdownBy) {

    private val valueBy: By = By.className("dropdown__current-value")

    private val input: E2ETextInput = childTextInput(By.tagName("input"))

    private val optionsList: E2ETextList by lazy {
        E2ETextList(CONTAINER_BY, By.className("dropdown__list-item"))
    }

    val options: List<E2ETextListItem> get() = optionsList.items

    val value: String get() = childText(valueBy)

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
        optionsList.selectByText(name)
        waitUntilNotVisible(CONTAINER_BY)
    }

    fun selectFromDynamicByName(name: String): E2EDropdown = apply {
        logger.info("Select item $name from dynamic dropdown")

        input.inputValue(name)
        // Can't use optionsList directly, as it comes and goes for a while before it's stable
        // This can be removed once the dropdown component itself has been fixed
        tryWait(
            ExpectedConditions.textToBePresentInElementLocated(
                ByChained(CONTAINER_BY, By.className("dropdown__list-item"), By.className("dropdown__list-item-text")),
                name
            )
        ) { "Option list does not contain item $name" }

        optionsList.selectByText(name)
    }

    fun new() = apply {
        logger.info("Add new option")

        open()
        clickWhenClickable(By.cssSelector(".dropdown__add-new-container > button"))
    }

    fun search(text: String): E2EDropdown = apply {
        logger.info("Search options with text $text")

        input.inputValue(text)
    }

    fun clearSearch(): E2EDropdown = apply {
        logger.info("Clear dropdown input")

        val currentValueHolder = childElement(valueBy)

        if (!currentValueHolder.isDisplayed) {
            input.clear()
        }
    }

    fun waitForValue(): E2EDropdown = apply {
        logger.info("Wait for dropdown value")

        waitUntilTextExists(childBy(valueBy))

    }
}
