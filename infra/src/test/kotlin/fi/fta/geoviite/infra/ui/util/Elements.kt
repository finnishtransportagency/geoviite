import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.Toaster
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions.*
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger: Logger = LoggerFactory.getLogger(PageModel::class.java)

val defaultWait = Duration.ofSeconds(5L)

fun clickElementAtPoint(element: WebElement, x: Int, y: Int, doubleClick: Boolean = false) {

    //Invert results since negative means up/left
    val offSetX = -((element.rect.width / 2) - x)
    val offSetY = -((element.rect.height / 2) - y)

    logger.info("Click double=$doubleClick canvas [${element.rect.width}x${element.rect.height}], center offset ($offSetX,$offSetY)")

    val actions = Actions(browser()).moveToElement(element, offSetX, offSetY).click()
    if (doubleClick) actions.click()
    actions.build().perform()
    Thread.sleep(400) //Prevents double-clicking and zooming with map canvas
}

fun waitAndGetToasterElement(): Toaster {
    logger.info("Waiting toaster element to appear")
    val toaster = Toaster(By.cssSelector("div.Toastify__toast"))
    logger.info("Toaster appeared")
    return toaster
}

fun clearInput(inputElement: WebElement) {
    //CMD+A does nothing in non-mac systems and vice versa
    waitUntilElementClickable(inputElement)

    inputElement.click()
    inputElement.sendKeys(Keys.chord(Keys.COMMAND, "a"))
    inputElement.sendKeys(Keys.BACK_SPACE)
    inputElement.sendKeys(Keys.chord(Keys.CONTROL, "a"))
    inputElement.sendKeys(Keys.BACK_SPACE)
}

fun elementExists(byCondition: By) = getElementIfExists(byCondition) != null

fun childElementExists(parent: WebElement, byCondition: By) = getChildElementIfExists(parent, byCondition) != null

fun getChildElementIfExists(parentElement: WebElement, byCondition: By): WebElement? = try {
    parentElement.findElement(byCondition)
} catch (ex: NoSuchElementException) {
    null
}

fun getChildElements(parentElement: WebElement, byCondition: By): List<WebElement> = try {
    parentElement.findElements(byCondition)
} catch (ex: NoSuchElementException) {
    listOf()
}

fun getElementIfExists(byCondition: By): WebElement? = try {
    browser().findElement(byCondition)
} catch (ex: NoSuchElementException) {
    null
}

fun getElementWhenExists(byCondition: By, timeout: Duration = defaultWait): WebElement {
    waitUntilExists(byCondition, timeout)
    return browser().findElement(byCondition)
}

fun getElementWhenVisible(byCondition: By, timeout: Duration = defaultWait): WebElement {
    waitUntilVisible(byCondition, timeout)
    return browser().findElement(byCondition)
}

fun getElementsWhenVisible(byCondition: By, timeout: Duration = defaultWait): List<WebElement> {
    waitUntilVisible(byCondition, timeout)
    return browser().findElements(byCondition)
}

fun getElementWhenClickable(byCondition: By, timeout: Duration = defaultWait): WebElement {
    WebDriverWait(browser(), timeout).until(elementToBeClickable(byCondition))
    return browser().findElement(byCondition)
}

fun getChildWhenVisible(parent: WebElement, byCondition: By, timeout: Duration = defaultWait): WebElement =
    getOptionalChildWhenVisible(parent, byCondition, timeout)
        ?: throw IllegalStateException("No child element found: parent=$parent by=$byCondition timeout=$timeout")

fun getOptionalChildWhenVisible(parent: WebElement, byCondition: By, timeout: Duration = defaultWait): WebElement? {
    waitUntilChildVisible(parent, byCondition, timeout)
    return getChildElementIfExists(parent, byCondition)
}

fun getChildrenWhenVisible(parent: WebElement, byCondition: By, timeout: Duration = defaultWait): List<WebElement> {
    waitUntilChildVisible(parent, byCondition, timeout)
    return parent.findElements(byCondition)
}

fun getListElements(listBy: By, timeout: Duration = defaultWait) =
    getChildrenWhenVisible(getElementWhenVisible(listBy, timeout), By.tagName("li"), timeout)

fun waitUntilExists(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, presenceOfElementLocated(byCondition)) {
        "Wait for element exists failed: seekBy=$byCondition"
    }

fun waitUntilChildExists(parent: WebElement, byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, presenceOfNestedElementLocatedBy(parent, byCondition)) {
        "Wait for child exists failed: parent=${parent.getAttribute("innerHTML")} seekBy=$byCondition"
    }

fun waitUntilDoesNotExist(element: WebElement, timeout: Duration = defaultWait) =
    tryWait(timeout, not(visibilityOf(element))) {
        "Wait for element disappearing failed: element=${element.getAttribute("innerHTML")}"
    }

fun waitUntilDoesNotExist(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, not(visibilityOfElementLocated(byCondition))) {
        "Wait for element disappearing failed: seekBy=$byCondition"
    }

fun waitUntilChildDoesNotExist(parent: WebElement, byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, not(visibilityOfNestedElementsLocatedBy(parent, byCondition))) {
        "Wait for child disappearing failed: parent=${parent.getAttribute("innerHTML")} seekBy=$byCondition"
    }

fun waitUntilVisible(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, visibilityOfElementLocated(byCondition)) {
        "Wait for element visible failed: seekBy=$byCondition"
    }

fun waitUntilChildVisible(parent: WebElement, byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, visibilityOfNestedElementsLocatedBy(parent, byCondition)) {
        "Wait for child visible failed: parent=${parent.getAttribute("innerHTML")} seekBy=$byCondition"
    }

fun waitUntilElementClickable(element: WebElement, timeout: Duration = defaultWait) =
    tryWait(timeout, elementToBeClickable(element)) {
        "Wait for element clickable failed: element=${element.getAttribute("innerHTML")}"
    }

fun waitUntilElementClickable(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, elementToBeClickable(byCondition)) {
        "Wait for element clickable failed: seekBy=$byCondition"
    }

fun waitUntilElementIsStale(element: WebElement, timeout: Duration = defaultWait) =
    tryWait(timeout, stalenessOf(element)) {
        "Wait for element staleness failed: element=${element.getAttribute("innerHTML")}"
    }

fun waitUntilValueIs(element: WebElement, value: String, timeout: Duration = defaultWait) =
    tryWait(timeout, textToBePresentInElement(element, value)) {
        "Wait for element value 'to be x' failed: element=${element.getAttribute("innerHTML")} value=$value"
    }

fun waitUntilValueIsNot(element: WebElement, value: String, timeout: Duration = defaultWait) =
    tryWait(timeout, not(textToBePresentInElement(element, value))) {
        "Wait for element value 'to not be x' failed: element=${element.getAttribute("innerHTML")} value=$value"
    }

fun waitUntilChildMatches(
    parent: WebElement,
    childBy: By,
    check: (index: Int, child: WebElement) -> Boolean,
    timeout: Duration = defaultWait,
) = WebDriverWait(browser(), timeout)
    .until { _ -> getChildElements(parent, childBy) }
        .mapIndexed { index, webElement -> check(index, webElement) }
        .any()

fun <T: Any> getChildWithContentWhenMatches(
    parent: WebElement,
    childBy: By,
    getContent: (index: Int, child: WebElement) -> T,
    check: (content: T) -> Boolean,
    timeout: Duration = defaultWait,
): Pair<WebElement, T> {
    waitUntilChildMatches(parent, childBy, { i, c -> check(getContent(i, c)) }, timeout)
    return getChildElements(parent, childBy)
        .mapIndexed { i, c -> c to getContent(i, c) }
        .find { (_, c) -> check(c) }
        ?: throw IllegalStateException("No child element found: parent=$parent childBy=$childBy timeout=$timeout")
}


fun tryWait(timeout: Duration, condition: ExpectedCondition<*>, lazyErrorMessage: () -> String) = try {
    WebDriverWait(browser(), timeout).until(condition)
} catch (e: Exception) {
    logger.warn("${lazyErrorMessage()} cause=${e.message}")
    throw e
}

fun clickElement(by: By, timeout: Duration = defaultWait) =
    clickElement(getElementWhenVisible(by, timeout), timeout)

fun clickChildElement(parent: WebElement, childBy: By, timeout: Duration = defaultWait) =
    clickElement(getChildWhenVisible(parent, childBy, timeout), timeout)

fun clickElement(element: WebElement, timeout: Duration = defaultWait) {
    waitUntilElementClickable(element, timeout)
    element.click()
}
