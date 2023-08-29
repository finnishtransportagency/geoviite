package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.openqa.selenium.By
import waitUntilValueIs
import waitUntilValueIsNot

abstract class E2EInfoBox(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {

    init {
        logger.info("${this.javaClass.simpleName} loaded")
    }

    protected val title: String get() = childText(By.className("infobox__title"))

    // TODO: GVT-2034 These label-content fields could be a component of their own
    private fun getValueElementByForField(fieldName: String) = By.xpath(
        ".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]/div[@class='infobox__field-value']"
    )

    private fun getValueElementForField(fieldName: String) = childElement(getValueElementByForField(fieldName))

    protected fun getValueForField(fieldName: String): String = getValueElementForField(fieldName).text

    protected fun editFields(): E2EInfoBox = apply {
        logger.info("Click edit values icon")
        clickChild(By.className("infobox__edit-icon"))
    }

    protected fun getValueForFieldWhenNotEmpty(fieldName: String): String = childText(
        By.xpath(
            ".//div[@class='infobox__field-label' and text() = '$fieldName']/div[@class='infobox__field-value' and (text() != '' or ./*[text() != ''])]"
        )
    )

    protected fun waitUntilValueChangesForField(fieldName: String): E2EInfoBox = apply {
        val originalValue = getValueForField(fieldName)
        logger.info("Wait until field value is not $originalValue")
        waitUntilValueIsNot(getValueElementForField(fieldName), originalValue)
        logger.info("Field $fieldName changed and is now ${getValueForField(fieldName)}")
    }

    protected fun waitUntilValueChangesForField(fieldName: String, targetValue: String): E2EInfoBox = apply {
        logger.info("Wait until field value is  $targetValue")
        waitUntilValueIs(getValueElementForField(fieldName), targetValue)
        logger.info("Field $fieldName changed to $targetValue")
    }

    fun waitUntilLoaded(): E2EInfoBox = apply { waitChildNotVisible(By.className("spinner")) }
}
