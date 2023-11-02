package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained

open class E2EFormLayout(formBy: By) : E2EViewFragment(formBy) {

    fun getValueForFieldByLabel(fieldName: String): String {
        logger.info("Get field $fieldName")

        val nameBy = getFieldValueBy(fieldName)
        val fieldBy = ByChained(nameBy, By.className("field-layout__value"))
        val valueBy = if (childExists(fieldBy)) fieldBy else nameBy

        return childText(valueBy).also { v ->
            logger.info("Field value [$fieldName]=[$v]")
        }
    }

    fun inputFieldValueByLabel(label: String, value: String): E2EFormLayout = apply {
        logger.info("Change field $label to $value")
        getTextInputForField(label)
            .clear()
            .inputValue(value)
    }

    fun clearInputByLabel(label: String): E2EFormLayout = apply {
        getTextInputForField(label).clear()
    }

    private fun getFieldValueBy(fieldName: String) = By.xpath(
        ".//div[contains(@class, 'field-layout') and div[contains(text(), '$fieldName')]]/div[@class='field-layout__value']"
    )

    private fun getTextInputForField(fieldName: String) = childTextInput(
        By.xpath(
            ".//div[contains(@class, 'field-layout') and div[contains(text(), '$fieldName')]]/div[@class='field-layout__value']//input"
        )
    )

    fun dropdownByQaId(qaId: String) = childDropdown(byQaId((qaId)))

    fun textInputByQaId(qaId: String) = childTextInput(byQaId(qaId))

    fun checkBoxByQaId(qaId: String) = childCheckbox(byQaId(qaId))

    fun selectDropdownValueByLabel(label: String, value: String) {
        logger.info("Change dropdown $label to [$value]")
        childDropdown(ByChained(getFieldValueBy(label), By.className("dropdown")))
            .select(value)
    }
}
