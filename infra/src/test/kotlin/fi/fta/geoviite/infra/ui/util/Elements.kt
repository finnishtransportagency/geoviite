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
    inputElement.waitAndClick()
    inputElement.sendKeys(Keys.chord(Keys.COMMAND, "a"))
    inputElement.sendKeys(Keys.BACK_SPACE)
    inputElement.sendKeys(Keys.chord(Keys.CONTROL, "a"))
    inputElement.sendKeys(Keys.BACK_SPACE)
}

fun elementExists(byCondition: By) = getElementIfExists(byCondition) != null

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

fun getChildWhenVisible(parentFetch: () -> WebElement, byCondition: By, timeout: Duration = defaultWait): WebElement {
    waitUntilChildVisible(parentFetch, byCondition, timeout)
    return parentFetch().let { parent ->
        parent.getChildElementIfExists(byCondition)
            ?: throw IllegalStateException("No child element found: parent=$parent by=$byCondition timeout=$timeout")
    }
}

fun getChildrenWhenVisible(parentFetch: () -> WebElement, byCondition: By, timeout: Duration = defaultWait): List<WebElement> {
    waitUntilChildVisible(parentFetch, byCondition, timeout)
    return parentFetch().findElements(byCondition)
}

fun waitUntilExists(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, presenceOfElementLocated(byCondition)) {
        "Wait for element exists failed: seekBy=$byCondition"
    }

fun waitUntilDoesNotExist(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, not(visibilityOfElementLocated(byCondition))) {
        "Wait for element disappearing failed: seekBy=$byCondition"
    }

fun waitUntilVisible(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, visibilityOfElementLocated(byCondition)) {
        "Wait for element visible failed: seekBy=$byCondition"
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

fun waitUntilChildVisible(parentFetch: () -> WebElement, childBy: By, timeout: Duration = defaultWait) = tryWait(
    timeout,
    { parentFetch().childElementExists(childBy) },
    { parentFetch().let { parent ->
        "Child element never appeared: parent=$parent child=${parent.getChildElementIfExists(childBy)}"
    } }
)

fun waitUntilChildNotVisible(parentFetch: () -> WebElement, childBy: By, timeout: Duration = defaultWait) = tryWait(
    timeout,
    { !parentFetch().childElementExists(childBy) },
    { parentFetch().let { parent ->
        "Child element never disappeared: parent=$parent child=${parent.getChildElementIfExists(childBy)}"
    } }
)

fun waitUntilChildMatches(
    parentFetch: () -> WebElement,
    childBy: By,
    check: (index: Int, child: WebElement) -> Boolean,
    timeout: Duration = defaultWait,
) = tryWait(timeout,
    { parentFetch().getChildElements(childBy).filterIndexed { index, webElement -> check(index, webElement) }.any() },
    { "Wait for child content to match condition failed: parent=${parentFetch()} childBy=$childBy timeout=$timeout" }
)

fun getChildWhenMatches(
    parentFetch: () -> WebElement,
    childBy: By,
    check: (index: Int, child: WebElement) -> Boolean,
    timeout: Duration = defaultWait,
): Pair<Int, WebElement> {
    waitUntilChildMatches(parentFetch, childBy, { i, c -> check(i, c) }, timeout)
    return parentFetch().let { parent -> parent
        .getChildElements(childBy)
        .mapIndexedNotNull { i, e -> if(check(i, e)) i to e else null }
        .singleOrNull()
        ?: throw IllegalStateException("No child element found: parent=$parent childBy=$childBy timeout=$timeout")
    }
}

fun tryWait(timeout: Duration, condition: ExpectedCondition<*>, lazyErrorMessage: () -> String) = try {
    WebDriverWait(browser(), timeout).until(condition)
} catch (e: Exception) {
    logger.warn("${lazyErrorMessage()} cause=${e.message}")
    throw e
}

fun tryWait(timeout: Duration, condition: () -> Boolean, lazyErrorMessage: () -> String) = try {
    WebDriverWait(browser(), timeout).until { _ -> condition() }
} catch (e: Exception) {
    logger.warn("${lazyErrorMessage()} cause=${e.message}")
    throw e
}

fun clickElement(by: By, timeout: Duration = defaultWait) =
    getElementWhenVisible(by, timeout).waitAndClick(timeout)

fun clickChildElement(parentFetch: () -> WebElement, childBy: By, timeout: Duration = defaultWait) =
    getChildWhenVisible(parentFetch, childBy, timeout).waitAndClick(timeout)
