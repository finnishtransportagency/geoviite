package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained

open class E2EFormLayout(formBy: By) : E2EViewFragment(formBy) {

    fun getValueForFieldByLabel(fieldName: String): String {
        logger.info("Get value for field $fieldName")

        val nameBy = getFieldValueBy(fieldName)
        val fieldBy = ByChained(nameBy, By.className("field-layout__value"))
        val valueBy = if (childExists(fieldBy)) fieldBy else nameBy

        return childText(valueBy)
    }

    fun inputFieldValueByLabel(label: String, value: String): E2EFormLayout = apply {
        logger.info("Change field $label to $value")

        getTextInputForField(label)
            .clear()
            .inputValue(value)
    }

    fun clearInputByLabel(label: String): E2EFormLayout = apply {
        logger.info("Clear input value for label $label")

        getTextInputForField(label).clear()
    }

    //todo Replace this with common component or with qaIds
    private fun getFieldValueBy(fieldName: String) = By.xpath(
        ".//div[contains(@class, 'field-layout') and div[contains(text(), '$fieldName')]]" +
                "/div[@class='field-layout__value']"
    )

    //todo Replace this with common component or with qaIds
    private fun getTextInputForField(fieldName: String) = childTextInput(
        By.xpath(
            ".//div[contains(@class, 'field-layout') and div[contains(text(), '$fieldName')]]" +
                    "/div[@class='field-layout__value']//input"
        )
    )

    fun dropdown(qaId: String) = childDropdown(byQaId((qaId)))

    fun textInput(qaId: String) = childTextInput(byQaId(qaId))

    fun checkbox(qaId: String) = childCheckbox(byQaId(qaId))

    fun selectDropdownValueByLabel(label: String, value: String) = apply {
        logger.info("Change dropdown $label to [$value]")

        childDropdown(ByChained(getFieldValueBy(label), By.className("dropdown")))
            .select(value)
    }
}
