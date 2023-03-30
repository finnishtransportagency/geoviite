package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.pagemodel.frontpage.FrontPage
import fi.fta.geoviite.infra.util.logger
import org.openqa.selenium.*
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

abstract class PageModel(
    protected val rootByCondition: By,
    protected var rootElement: WebElement = getElementWhenVisible(rootByCondition, timeoutSeconds = 5)
) {

    protected inline operator fun <T> T.invoke(action: T.() -> Unit): T = apply(action)
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)


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
                    refresRootElement()
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
                    refresRootElement()
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

    protected fun refresRootElement() {
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


    companion object {
        lateinit var webDriver: WebDriver
        fun setBrowser(webDriver: WebDriver) {
            Companion.webDriver = webDriver
        }

        fun browser() = webDriver
        fun javaScriptExecutor(): JavascriptExecutor = webDriver as JavascriptExecutor

        /**
         * Click element at point x,y where 0,0 is at top left corner
         */
        fun clickElementAtPoint(element: WebElement, x: Int, y: Int, doubleClick: Boolean = false) {

            //Invert results since negative means up/left
            val offSetX = -((element.rect.width / 2) - x)
            val offSetY = -((element.rect.height / 2) - y)

            logger.info("Click double=$doubleClick canvas [${element.rect.width}x${element.rect.height}], center offset ($offSetX,$offSetY)")

            val actions = Actions(browser())
                .moveToElement(element, offSetX, offSetY)
                .click()
            if (doubleClick) actions.click()
            actions.build().perform()
            Thread.sleep(400) //Prevents double-clicking and zooming with map canvas
        }

        fun openGeoviite(startUrl: String): FrontPage {
            logger.info("Navigate to Geoviite $startUrl")
            browser().navigate().to(startUrl);
            return FrontPage()
        }

        fun waitAndGetToasterElement(): Toaster {
            logger.info("Waiting toaster element to appear")
            val toaster = Toaster(getElementWhenVisible(By.cssSelector("div.Toastify__toast"), 15))
            logger.info("Toaster appeared")
            return toaster
        }

        fun waitUntilPopUpDialogCloses() =
            WebDriverWait(browser(), Duration.ofSeconds(5))
                .until(
                    ExpectedConditions.not(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.dialog__popup"))
                    )
                )

        fun waitUntilElementIsClickable(element: WebElement, timeoutSeconds: Long = 5) =
            WebDriverWait(browser(), Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.elementToBeClickable(element))


        fun clearInput(inputElement: WebElement) {
            //CMD+A does nothing in non-mac systems and vice versa
            inputElement.click()
            inputElement.sendKeys(Keys.chord(Keys.COMMAND, "a"))
            inputElement.sendKeys(Keys.BACK_SPACE)
            inputElement.sendKeys(Keys.chord(Keys.CONTROL, "a"))
            inputElement.sendKeys(Keys.BACK_SPACE)
        }

        fun waitUntilElementIsStale(element: WebElement, timeoutSeconds: Long = 10) {
            WebDriverWait(browser(), Duration.ofSeconds(timeoutSeconds)).until(ExpectedConditions.stalenessOf(element))
        }

        fun waitUntilChildExists(rootElement: WebElement, childByCondition: By) {
            WebDriverWait(browser(), Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfNestedElementsLocatedBy(rootElement, childByCondition))
        }

        fun waitUntilChildDoesNotExist(rootElement: WebElement, childByCondition: By) {
            WebDriverWait(browser(), Duration.ofSeconds(5))
                .until(ExpectedConditions.not(ExpectedConditions.visibilityOfNestedElementsLocatedBy(rootElement, childByCondition)))
        }

        fun getElementIfExists(rootElement: WebElement, byCondition: By): WebElement? {
            try {
                return rootElement.findElement(byCondition)
            } catch (ex: NoSuchElementException) {
                return null
            }
        }

        fun getElementWhenExists(byCondition: By, timeoutSeconds: Long = 10): WebElement {
            WebDriverWait(browser(), Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.presenceOfElementLocated(byCondition))
            return browser().findElement(byCondition)
        }

        fun getElementWhenVisible(byCondition: By, timeoutSeconds: Long = 10): WebElement {
            WebDriverWait(browser(), Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.visibilityOfElementLocated(byCondition))
            return browser().findElement(byCondition)
        }

        fun getElementsWhenVisible(byCondition: By, timeoutSeconds: Long = 10): List<WebElement> {
            WebDriverWait(browser(), Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.visibilityOfElementLocated(byCondition))
            return browser().findElements(byCondition)
        }

        fun getElementWhenClickable(byCondition: By, timeoutSeconds: Long = 10): WebElement {
            WebDriverWait(browser(), Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.elementToBeClickable(byCondition))
            return browser().findElement(byCondition)
        }

    }
}
