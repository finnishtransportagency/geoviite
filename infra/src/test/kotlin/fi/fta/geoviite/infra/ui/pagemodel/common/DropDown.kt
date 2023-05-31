package fi.fta.geoviite.infra.ui.pagemodel.common

import clearInput
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DropDown (val element: WebElement) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun currentValue(): String {
        val value = inputElement().getAttribute("value")
        logger.info("Current value $value")
        return value
    }

    fun openDropdown() {
        logger.info("Open dropdown")
        element.findElement(By.cssSelector("div.dropdown__header")).click()
    }

    fun listItems() =
        element.findElements(By.cssSelector("li.dropdown__list-item span.dropdown__list-item-text"))


    fun selectItem(itemName: String) {
        logger.info("Select item $itemName")
        getElementWhenVisible(By.xpath(".//li/span[@class='dropdown__list-item-text' and contains(text(), '$itemName')]")).click()
    }

    fun clickAddNew() {
        logger.info("Add new item")
        element.findElement(By.cssSelector("div.dropdown__add-new-container > button")).click()
    }

    fun inputText(input: String) {
        logger.info("Input text $input")
        inputElement().sendKeys(input)
    }

    fun clearInput() = clearInput(inputElement())

    private fun inputElement(): WebElement =
        element.findElement(By.xpath(".//input"))

}
