package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EDropdown(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {

    private val input: E2ETextInput get() = childTextInput(By.tagName("input"))
    private val currentValueHolder: WebElement get() = childElement(By.className("dropdown__current-value"))

    val value: String
        get() {
            logger.info("Current value ${input.value}")
            return input.value
        }

    fun open(): E2EDropdown = apply {
        logger.info("Open dropdown")

        if (!childExists(By.className("dropdown__list-container"))) {
            clickChild(By.className("dropdown__header"))
            waitChildVisible(By.className("dropdown__list-container"))
        }
        
    }

    // TODO: GVT-1935 This exposes elements directly to outside the class with a stale-risk
    //   Can be fixed by handling the contents like lists in ListModel
    @Deprecated("Element risks staleness")
    fun options() = childElements(By.cssSelector(".dropdown__list-item .dropdown__list-item-text"))

    fun select(name: String): E2EDropdown = apply {
        logger.info("Select item $name")
        open()
        clickChild(By.xpath(".//li[contains(@class, 'dropdown__list-item') and span[@class='dropdown__list-item-text' and contains(text(), '$name')]]"))
        waitChildNotVisible(By.className("dropdown__list-container"))
    }

    fun new() {
        logger.info("Add new item")
        clickChild(By.cssSelector(".dropdown__add-new-container > button"))
    }

    fun inputValue(text: String) {
        logger.info("Input text $text")
        input.inputValue(text)
    }

    fun clearInput() {
        if (!currentValueHolder.isDisplayed) {
            input.clear()
        }
    }
}
