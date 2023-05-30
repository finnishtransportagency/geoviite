package fi.fta.geoviite.infra.ui.pagemodel.common

import browser
import getElementWhenVisible
import org.openqa.selenium.*
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import waitUntilElementIsClickable
import java.time.Duration

const val SCREENSHOTS_PATH = "build/reports/screenshots"

abstract class PageModel(protected val rootByCondition: By) {
    protected var rootElement: WebElement = getElementWhenVisible(rootByCondition, timeoutSeconds = 5)
    protected val logger: Logger = LoggerFactory.getLogger(PageModel::class.java)


    protected inline operator fun <T> T.invoke(action: T.() -> Unit): T = apply(action)

    protected open fun clickButton(buttonContent: String) {
        logger.info("Click button '$buttonContent'")
        val button = getButtonElementByContent(buttonContent)
        try {
            waitUntilElementIsClickable(button)
            Thread.sleep(500) //Fixes problems where button cannot be clicked while enabled
            button.click()
        } catch (ex: StaleElementReferenceException) {
            getButtonElementByContent(buttonContent).click()
        }

    }

    protected open fun clickButtonByQaId(qaId: String) {
        logger.info("Click button qa-id=$qaId")
        val button = getButtonElementByQaId(qaId)
        try {
            waitUntilElementIsClickable(button)
            Thread.sleep(500) //Fixes problems where button cannot be clicked while enabled
            button.click()
        } catch (ex: StaleElementReferenceException) {
            getButtonElementByContent(qaId).click()
        }

    }

    protected fun getButtonElementByContent(buttonContent: String) =
        getChildElementStaleSafe(By.xpath(".//span[text() = '$buttonContent']"))

    protected fun getButtonElementByQaId(qaId: String) =
        getChildElementStaleSafe(By.cssSelector("button[qa-id=$qaId]"))

    protected fun getChildElementStaleSafe(childByCondition: By): WebElement {
        try {
            waitUntilChildIsVisible(childByCondition)
            return rootElement.findElement(childByCondition)
        } catch (ex: WebDriverException) {
            when (ex) {
                is StaleElementReferenceException -> {
                    logger.info("Root element has become stale ${rootByCondition}")
                    refreshRootElement()
                    logger.info("Retry waiting child to become visible")
                    waitUntilChildIsVisible(childByCondition)
                    return rootElement.findElement(childByCondition)
                }
                is TimeoutException -> {
                    logger.error("${rootElement.getAttribute("innerHTML")} -> $childByCondition TIMEOUT")
                    throw ex
                }
                else -> throw ex
            }
        }
    }

    protected fun getChildElementsStaleSafe(childByCondition: By, timeout: Duration = Duration.ofSeconds(5)): List<WebElement> {
        try {
            waitUntilChildIsVisible(childByCondition, timeout)
            return rootElement.findElements(childByCondition)
        } catch (ex: WebDriverException) {
            when (ex) {
                is StaleElementReferenceException -> {
                    refreshRootElement()
                    waitUntilChildIsVisible(childByCondition, timeout)
                    return rootElement.findElements(childByCondition)
                }

                is TimeoutException -> {
                    logger.error("$rootElement -> $childByCondition TIMEOUT")
                    throw ex
                }
                else -> throw ex
            }
        }
    }

    protected fun refreshRootElement() {
        logger.info("Refresh root element")
        rootElement = getElementWhenVisible(rootByCondition)
    }

    protected fun waitUntilChildIsVisible(childByCondition: By, timeout: Duration = Duration.ofSeconds(5)) {
        try {
            WebDriverWait(browser(), timeout)
                .until(ExpectedConditions.visibilityOfNestedElementsLocatedBy(rootElement, childByCondition))
        } catch (ex: TimeoutException) {
            logger.warn("${rootElement.getAttribute("innerHTML")} did not include $childByCondition")
            throw ex
        }

    }

    protected fun childElementExists(byCondition: By) =
        rootElement.findElements(byCondition).isNotEmpty()

    /**
     * Click element at point x,y where 0,0 is at top left corner
     */

}
