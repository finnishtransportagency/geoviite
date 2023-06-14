package fi.fta.geoviite.infra.ui.pagemodel.common

import getChildElementIfExists
import org.openqa.selenium.By

abstract class FormGroup(rootBy: By) : PageModel(rootBy) {

    init {
        logger.info("${this.javaClass} loaded")
    }

    protected fun title() =
        webElement.findElement(By.cssSelector("div.formgroup__title")).text

    protected fun fieldValue(fieldLabel: String): String {
        logger.info("Get field [$fieldLabel]")
        val fieldValueElement = fieldValueElement(fieldLabel)
        val fieldLayoutValueElement = fieldValueElement.getChildElementIfExists(By.className("field-layout__value"))
        return if (fieldLayoutValueElement != null) {
            logger.info("field [$fieldLabel]=[${fieldLayoutValueElement.text}]")
            fieldLayoutValueElement.text
        } else {
            logger.info("field [$fieldLabel]=[${fieldValueElement.text}]")
            fieldValueElement.text
        }
    }

    protected fun changeFieldDropDownValues(fieldLabel: String, inputs: List<String>) {
        logger.info("Change dropdown field [$fieldLabel] to [$inputs]")
        clickEditIcon(fieldLabel)
        inputs.forEachIndexed { index, input ->
            val dropDown = DropDown{ fieldValueElement(fieldLabel).findElements(By.cssSelector(".dropdown"))[index] }
            dropDown.openDropdown()
            dropDown.selectItem(input)
        }
        clickEditIcon(fieldLabel)
    }

    protected fun changeToNewDropDownValue(fieldLabel: String, inputs: List<String>): Toaster {
        logger.info("Add and change dropdown value field [$fieldLabel] to [$inputs]")
        clickEditIcon(fieldLabel)
        val dropDown = DropDown{ fieldValueElement(fieldLabel).findElement(By.cssSelector(".dropdown")) }
        dropDown.openDropdown()
        dropDown.clickAddNew()
        val dialogPopUp = DialogPopUpWithTextField()
        dialogPopUp.inputTextField(inputs)
        dialogPopUp.clickPrimaryButton()
        return Toaster().also { t ->
            t.waitUntilVisible()
            clickEditIcon(fieldLabel)
        }
    }

    protected fun fieldValueElement(fieldLabel: String) =
        childElement(By.xpath(".//div[@class='formgroup__field' and div[contains(text(), '$fieldLabel')]]/div[@class='formgroup__field-value']"))


    protected fun clickEditIcon(fieldLabel: String) {
        logger.info("Click edit icon in field $fieldLabel")
        webElement.findElement(
            By.xpath(".//div[@class='formgroup__field' and div[contains(text(), '$fieldLabel')]]/div[@class='formgroup__edit-icon']/div")
        )
            .click()
    }
}
