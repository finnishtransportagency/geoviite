package fi.fta.geoviite.infra.ui.pagemodel.common

import clearInput
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class DropDown(elementFetch: () -> WebElement) : PageModel(elementFetch) {

    private val inputElement: WebElement get() = childElement(By.xpath(".//input"))
    private val currentValueHolder: WebElement get() = childElement(By.className("dropdown__current-value"))

    fun currentValue(): String {
        val value = inputElement.getAttribute("value")
        logger.info("Current value $value")
        return value
    }

    fun openDropdown() {
        logger.info("Open dropdown")
        clickChild(By.cssSelector("div.dropdown__header"))
    }

    // TODO: GVT-1935 This exposes elements directly to outside the class with a stale-risk
    //   Can be fixed by handling the contents like lists in ListModel
    @Deprecated("Element risks staleness")
    fun listItems() = childElements(By.cssSelector("li.dropdown__list-item span.dropdown__list-item-text"))


    fun selectItem(itemName: String) {
        logger.info("Select item $itemName")
        getElementWhenVisible(By.xpath(".//li/span[@class='dropdown__list-item-text' and contains(text(), '$itemName')]")).click()
    }

    fun clickAddNew() {
        logger.info("Add new item")
        webElement.findElement(By.cssSelector("div.dropdown__add-new-container > button")).click()
    }

    fun inputText(input: String) {
        logger.info("Input text $input")
        inputElement.sendKeys(input)
    }

    fun clearInput() {
        if (!currentValueHolder.isDisplayed) {
            clearInput(inputElement)
        }
    }
}
