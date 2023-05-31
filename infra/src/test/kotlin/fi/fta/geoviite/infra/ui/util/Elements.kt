import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.Toaster
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger: Logger = LoggerFactory.getLogger(PageModel::class.java)

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

fun getElementIfExists(rootElement: WebElement, byCondition: By): WebElement? = try {
    rootElement.findElement(byCondition)
} catch (ex: NoSuchElementException) {
    null
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
