package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import waitUntilValueIs
import waitUntilValueIsNot

abstract class E2EInfoBox(infoboxBy: By) : E2EViewFragment(infoboxBy) {

    protected val title: String get() = childText(By.className("infobox__title"))

    //todo Replace this with common component or with qaIds
    private fun getFieldValueBy(fieldName: String) = By.xpath(
        ".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]" +
                "/div[@class='infobox__field-value']"
    )

    protected fun getValueForField(fieldName: String): String {
        logger.info("Get value for field $fieldName")

        return childText(getFieldValueBy(fieldName))
    }

    protected fun editFields(): E2EInfoBox = apply {
        logger.info("Enable editing")

        clickChild(By.className("infobox__edit-icon"))
    }

    protected fun getValueForFieldWhenNotEmpty(fieldName: String): String {
        logger.info("Get value for field $fieldName when it's not empty")

        return childText(
            By.xpath(
                ".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]" +
                        "/div[@class='infobox__field-value' and (text() != '' or ./*[text() != ''])]"
            )
        )
    }

    protected fun waitUntilFieldContainsValue(fieldName: String, targetValue: String): E2EInfoBox = apply {
        logger.info("Wait for field $fieldName to contain value $targetValue")

        waitUntilValueIs(childBy(getFieldValueBy(fieldName)), targetValue)
    }

    protected fun waitUntilFieldOmitsValue(fieldName: String, oldValue: String): E2EInfoBox = apply {
        logger.info("Wait for field $fieldName to change from value $oldValue")

        waitUntilValueIsNot(childBy(getFieldValueBy(fieldName)), oldValue)
    }

    fun waitUntilLoaded(): E2EInfoBox = apply { waitUntilChildNotVisible(By.className("spinner")) }
}
