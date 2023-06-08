package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitUntilChildDoesNotExist
import waitUntilValueIs
import waitUntilValueIsNot
import java.time.Duration

abstract class InfoBox(rootBy: By) : PageModel(rootBy) {

    init {
        logger.info("${this.javaClass.simpleName} loaded")
    }

    // TODO: These label-content fields could be a component of their own
    private fun fieldValueBy(fieldName: String) = By.xpath(
        ".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]/div[@class='infobox__field-value']"
    )
    protected fun fieldValueElement(fieldName: String) = childElement(fieldValueBy(fieldName))

    protected fun textField(fieldName: String) = childTextField(fieldValueBy(fieldName))

    // TODO: logging in getters like this is ugly. Instead log in the use-place, if needed
    protected fun fieldValue(fieldName: String): String = textField(fieldName).text.also { value ->
        logger.info("Get field value: field=$fieldName value=$value")
    }

    protected fun startEditingInfoBoxValues() {
        logger.info("Click edit values icon")
        clickChild(By.className("infobox__edit-icon"))
    }

    protected fun fieldElement(fieldName: String): WebElement = childElement(By.xpath(
        ".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]"
    ))

    protected fun fieldValueWhenNotEmpty(fieldName: String): String = childText(By.xpath(
        ".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]/div[@class='infobox__field-value' and (text() != '' or ./*[text() != ''])]"
    ))

    protected fun title() = childText(By.className("infobox__title"))

    protected fun waitUntilFieldValueChanges(fieldName: String) {
        val originalValue = fieldValue(fieldName)
        logger.info("Wait until field value is not ${originalValue}")
        waitUntilValueIsNot(fieldValueElement(fieldName), originalValue, Duration.ofSeconds(10))
        logger.info("Field $fieldName changed and is now ${fieldValue(fieldName)}")
    }

    protected fun waitUntilFieldValueChanges(fieldName: String, targetValue: String) {
        logger.info("Wait until field value is  ${targetValue}")
        waitUntilValueIs(fieldValueElement(fieldName), targetValue, Duration.ofSeconds(10))
        logger.info("Field $fieldName changed to $targetValue")
    }

    fun waitUntilLoaded() = waitUntilChildDoesNotExist(webElement, By.className("spinner"), Duration.ofSeconds(20))
}
