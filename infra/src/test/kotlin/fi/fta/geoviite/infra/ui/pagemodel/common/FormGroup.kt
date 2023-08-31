package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import getChildElementIfExists
import org.openqa.selenium.By

abstract class E2EFormGroup(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {
    
    init {
        logger.info("${this.javaClass} loaded")
    }

    protected val title: String get() = childText(By.className("formgroup__title"))

    protected fun getValueForField(fieldName: String): String {
        logger.info("Get field [$fieldName]")
        val fieldValueElement = getFieldValueElement(fieldName)
        val fieldLayoutValueElement = fieldValueElement.getChildElementIfExists(By.className("field-layout__value"))
        return if (fieldLayoutValueElement != null) {
            logger.info("field [$fieldName]=[${fieldLayoutValueElement.text}]")
            fieldLayoutValueElement.text
        } else {
            logger.info("field [$fieldName]=[${fieldValueElement.text}]")
            fieldValueElement.text
        }
    }

    protected fun selectDropdownValues(label: String, values: List<String>): E2EFormGroup = apply {
        logger.info("Change dropdown field [$label] to [$values]")
        clickEditIcon(label)

        values.forEachIndexed { index, value ->
            E2EDropdown { getFieldValueElement(label).findElements(By.className("dropdown"))[index] }.select(value)
        }

        clickEditIcon(label)
    }

    protected fun selectNewDropdownValue(label: String, values: List<String>): E2EFormGroup = apply {
        logger.info("Add and change dropdown value field [$label] to [$values]")
        clickEditIcon(label)

        E2EDropdown { getFieldValueElement(label).findElement(By.className("dropdown")) }
            .open()
            .new()

        E2EDialogWithTextField()
            .inputValues(values)
            .clickPrimaryButton()

        E2EToaster().waitUntilVisible()

        clickEditIcon(label)
    }

    private fun getFieldValueElement(fieldName: String) =
        childElement(By.xpath(".//div[@class='formgroup__field' and div[text() = '$fieldName']]/div[@class='formgroup__field-value']"))


    protected fun clickEditIcon(fieldName: String): E2EFormGroup = apply {
        logger.info("Click edit icon in field $fieldName")
        clickChild(
            By.xpath(".//div[@class='formgroup__field' and div[text() = '$fieldName']]/div[@class='formgroup__edit-icon']/div")
        )
    }
}
