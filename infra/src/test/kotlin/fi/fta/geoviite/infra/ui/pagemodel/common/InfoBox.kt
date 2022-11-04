package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

abstract class InfoBox(rootBy: By) : PageModel(rootBy) {

    init {
        logger.info("${this.javaClass.simpleName} loaded")
    }

    protected fun fieldValue(fieldName: String): String {
        logger.info("Get field $fieldName")
        val fieldValueBy =
            By.xpath(".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]/div[@class='infobox__field-value']")
        try {
            return getChildElementStaleSafe(fieldValueBy).text.also { value -> logger.info("Get field [$fieldName]=[$value]") }
        } catch (staleEx: StaleElementReferenceException) {
            return getChildElementStaleSafe(fieldValueBy).text.also { value -> logger.info("Get field [$fieldName]=[$value]") }
        }
    }

    protected fun fieldValueElement(fieldName: String): WebElement {
        val fieldValueBy =
            By.xpath(".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]/div[@class='infobox__field-value']")
        val value = getChildElementStaleSafe(fieldValueBy)
        logger.info("Get field value element $fieldName = ${value.text}")
        return value
    }

    protected fun editInfoBoxValues() {
        logger.info("Click edit values icon")
        getChildElementStaleSafe(By.className("infobox__edit-icon")).click()
    }

    protected fun fieldElement(fieldName: String): WebElement {
        val fielValueElementBy =
            By.xpath(".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]")
        return getChildElementStaleSafe(fielValueElementBy)
    }

    protected fun fieldValueWhenNotEmpty(fieldName: String): String {
        val fieldValueElementBy =
            By.xpath(".//div[div[@class='infobox__field-label' and contains(text(), '$fieldName')]]/div[@class='infobox__field-value' and (text() != '' or ./*[text() != ''])]")
        return getChildElementStaleSafe(fieldValueElementBy).text
    }

    protected fun title() =
        rootElement.findElement(By.className("infobox__title")).text

    protected fun waitUntilFieldValueChanges(fieldName: String) {
        val orgValue = fieldValueElement(fieldName).text
        logger.info("Wait until field value is not ${orgValue}")
        try {
            WebDriverWait(browser(), Duration.ofSeconds(10))
                .until(
                    ExpectedConditions.not(
                        ExpectedConditions.textToBePresentInElement(
                            fieldValueElement(fieldName),
                            orgValue
                        )
                    )
                )
        } catch (ex: TimeoutException) {
            logger.error("Field did not change and is now ${fieldValueElement(fieldName).text}")
            throw ex
        }
        logger.info("Field $fieldName changed and is now ${fieldValueElement(fieldName).text}")
    }

    protected fun waitUntilFieldValueChanges(fieldName: String, targetValue: String) {
        //val fieldValueElement = fieldValueElement(fieldName)
        //val orgValue = fieldValueElement.text
        logger.info("Wait until field value is  ${targetValue}")
        try {
            WebDriverWait(browser(), Duration.ofSeconds(10))
                .until(
                        ExpectedConditions.textToBePresentInElement(
                            fieldValueElement(fieldName),
                            targetValue
                        )
                )
        } catch (ex: TimeoutException) {
            logger.error("Field did not change and is now ${fieldValueElement(fieldName).text}")
            throw ex
        }
        logger.info("Field $fieldName changed to $targetValue")
    }



}