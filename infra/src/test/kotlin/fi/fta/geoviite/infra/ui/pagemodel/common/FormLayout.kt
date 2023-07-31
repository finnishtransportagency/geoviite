package fi.fta.geoviite.infra.ui.pagemodel.common

import clearInput
import javaScriptExecutor
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

open class FormLayout(getElement: () -> WebElement) : PageModel(getElement) {

    fun fieldValue(fieldLabel: String): String {
        logger.info("Get field $fieldLabel")
        val value = (javaScriptExecutor().executeScript("""
            const fieldValueElement = document.evaluate(
                ".//div[(@class='field-layout' or @class='field-layout field-layout--has-error') and div[contains(text(), '$fieldLabel')]]/div[@class='field-layout__value']",
                document).iterateNext();

            const valueChild = document.evaluate(".//div[@class='field-layout__value']", fieldValueElement).iterateNext()
            return valueChild !== null ? valueChild.textContent : fieldValueElement.textContent;
        """.trimIndent()) as String).trim()
        logger.info("Field value [$fieldLabel]=[$value]")
        return value
    }

    fun changeFieldValue(fieldLabel: String, newFieldValue: String) {
        logger.info("Change field $fieldLabel to $newFieldValue")
        val input = fieldValueInputElement(fieldLabel)
        clearInput(input)
        input.sendKeys(newFieldValue)
    }

    // TODO: GVT-1947 use qa-ids to find fields. Don't give out WebElement - only data
    protected fun fieldValueElement(fieldLabel: String): WebElement = childElement(
        By.xpath(
            ".//div[(@class='field-layout' or @class='field-layout field-layout--has-error') and div[contains(text(), '$fieldLabel')]]/div[@class='field-layout__value']"
        )
    )

    protected fun fieldValueInputElement(fieldLabel: String): WebElement = childElement(
        By.xpath(
            ".//div[(@class='field-layout' or @class='field-layout field-layout--has-error') and div[contains(text(), '$fieldLabel')]]/div[@class='field-layout__value']//input"
        )
    )

    fun changeFieldDropDownValue(fieldLabel: String, input: String) =
        changeFieldDropDownValues(fieldLabel, listOf(input))

    fun changeFieldDropDownValues(fieldLabel: String, inputs: List<String>) {
        logger.info("Change dropdown $fieldLabel to [$inputs]")
        inputs.forEachIndexed { index, input ->
            val dropDown = DropDown { fieldValueElement(fieldLabel).findElements(By.cssSelector(".dropdown"))[index] }
            dropDown.openDropdown()
            dropDown.selectItem(input)
        }
    }
}
