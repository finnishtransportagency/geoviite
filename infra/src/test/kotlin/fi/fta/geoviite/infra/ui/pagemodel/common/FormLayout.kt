package fi.fta.geoviite.infra.ui.pagemodel.common

import childExists
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import getChildElement
import getChildElements
import org.openqa.selenium.By

open class E2EFormLayout(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {

    fun getValueForField(fieldName: String): String {
        logger.info("Get field $fieldName")
        val fieldValueElement = getFieldValueElement(fieldName)
        val value =
            if (fieldValueElement.childExists(By.className("field-layout__value"))) {
                fieldValueElement.getChildElement(By.className("field-layout__value")).text
            } else fieldValueElement.text

        logger.info("Field value [$fieldName]=[$value]")
        return value
    }

    fun inputFieldValue(label: String, value: String): E2EFormLayout = apply {
        logger.info("Change field $label to $value")
        getTextInputForField(label)
            .clear()
            .inputValue(value)
    }

    fun clearInput(label: String): E2EFormLayout = apply {
        getTextInputForField(label).clear()
    }

    // TODO: GVT-1947 use qa-ids to find fields
    private fun getFieldValueElement(fieldName: String) = childElement(
        By.xpath(
            ".//div[contains(@class, 'field-layout') and div[contains(text(), '$fieldName')]]/div[@class='field-layout__value']"
        )
    )

    private fun getTextInputForField(fieldName: String) = childTextInput(
        By.xpath(
            ".//div[contains(@class, 'field-layout') and div[contains(text(), '$fieldName')]]/div[@class='field-layout__value']//input"
        )
    )

    fun dropdown(label: String) = E2EDropdown { getFieldValueElement(label).findElement(By.className("dropdown")) }
    fun dropdownByQaId(qaId: String) = E2EDropdown { childElement(byQaId(qaId)) }
    fun textInput(label: String) =
        E2ETextInput { getFieldValueElement(label).findElement(By.cssSelector("input.text-field__input-element")) }

    fun checkBox(label: String) = E2ECheckbox { getFieldValueElement(label).findElement(By.cssSelector("input.checkbox__input")) }

    fun checkBoxByLabel(label: String) = E2ECheckbox { childElement(By.xpath(".//label/*[contains(text(), \"$label\")]")) }

    fun radio(label: String) = E2ERadio { getFieldValueElement(label) }

    fun selectDropdownValue(label: String, value: String) = selectDropdownValues(label, listOf(value))

    fun selectDropdownValues(label: String, values: List<String>): E2EFormLayout = apply {
        logger.info("Change dropdown $label to [$values]")
        values.forEachIndexed { index, value ->
            E2EDropdown { getFieldValueElement(label).getChildElements(By.className("dropdown"))[index] }.select(value)
        }
    }
}
