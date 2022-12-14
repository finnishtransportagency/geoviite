package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement

open class FormLayout(layoutRoot: By): PageModel(layoutRoot){

    fun fieldValue(fieldLabel: String): String {
        logger.info("Get field $fieldLabel")
        val fieldValueElement = fieldValueElement(fieldLabel)
        val value = if (fieldValueElement.findElements(By.xpath(".//div[@class='field-layout__value']")).isNotEmpty()) {
            fieldValueElement.findElement(By.cssSelector("div.field-layout__value")).text
        } else {
            fieldValueElement.text
        }
        logger.info("Field value [$fieldLabel]=[$value]")
        return value
    }

    fun changeFieldValue(fieldLabel: String, newFieldValue: String) {
        logger.info("Change field $fieldLabel to $newFieldValue")
        val input = fieldValueInputElement(fieldLabel)
        clearInput(input)
        input.sendKeys(newFieldValue)
    }

    protected fun fieldValueElement(fieldLabel: String): WebElement =
        getChildElementStaleSafe(
            By.xpath(".//div[(@class='field-layout' or @class='field-layout field-layout--has-error') and div[contains(text(), '$fieldLabel')]]/div[@class='field-layout__value']"))

    protected fun fieldValueInputElement(fieldLabel: String): WebElement =
        getChildElementStaleSafe(
            By.xpath(".//div[(@class='field-layout' or @class='field-layout field-layout--has-error') and div[contains(text(), '$fieldLabel')]]/div[@class='field-layout__value']//input"))

    fun changeFieldDropDownValue(fieldLabel: String, input: String) =
        changeFieldDropDownValues(fieldLabel, listOf(input))

    fun changeFieldDropDownValues(fieldLabel: String, inputs: List<String>) {
        logger.info("Change dropdown $fieldLabel to [$inputs]")
        inputs.forEachIndexed { index, input ->
            val dropDown = DropDown(fieldValueElement(fieldLabel).findElements(By.cssSelector(".dropdown"))[index])
            dropDown.openDropdown()
            dropDown.selectItem(input)
        }
    }

    fun changeToNewDropDownValue(fieldLabel: String, inputs: List<String>) {
        logger.info("Create and change dropdown $fieldLabel to [$inputs]")
        val dropDown = DropDown(fieldValueElement(fieldLabel).findElement(By.cssSelector(".dropdown")))
        dropDown.openDropdown()
        dropDown.clickAddNew()
        val dialogPopUp = DialogPopUpWithTextField()
        dialogPopUp.inputTextField(inputs)
        dialogPopUp.clickPrimaryButton()
    }

}