import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.browser
import java.time.Duration
import java.util.regex.Pattern
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions.*
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(E2EViewFragment::class.java)

val defaultWait: Duration = Duration.ofSeconds(10L)
val defaultPoll: Duration = Duration.ofMillis(10)

fun clickElementAtPoint(element: WebElement, x: Int, y: Int, doubleClick: Boolean = false) {

    // Invert results since negative means up/left
    val offSetX = -((element.rect.width / 2) - x)
    val offSetY = -((element.rect.height / 2) - y)

    logger.info(
        "Click double=$doubleClick canvas [${element.rect.width}x${element.rect.height}], center offset ($offSetX,$offSetY)"
    )

    val actions = Actions(browser()).moveToElement(element, offSetX, offSetY).click()
    if (doubleClick) actions.click()
    actions.build().perform()
    Thread.sleep(400) // Prevents double-clicking and zooming with map canvas
}

fun getElementWhenVisible(by: By, timeout: Duration = defaultWait): WebElement {
    return tryWait(timeout, visibilityOfElementLocated(by)) { "Wait for element to be visible failed: seekBy=$by" }
}

fun getElementWhenExists(by: By, timeout: Duration = defaultWait): WebElement {
    return tryWait(timeout, presenceOfElementLocated(by)) { "Wait for element to exists failed: seekBy=$by" }
}

fun getElementsWhenVisible(by: By, timeout: Duration = defaultWait): List<WebElement> {
    return tryWait(timeout, visibilityOfAllElementsLocatedBy(by)) {
        "Wait for elements to be visible failed: seekBy=$by"
    }
}

fun getElementsWhenExists(by: By, timeout: Duration = defaultWait): List<WebElement> {
    return tryWait(timeout, presenceOfAllElementsLocatedBy(by)) { "Wait for elements to exists failed: seekBy=$by" }
}

fun getElementWhenClickable(by: By, timeout: Duration = defaultWait): WebElement {
    return tryWait(timeout, elementToBeClickable(by)) { "Wait for element to be clickable, by=$by" }
}

fun waitUntilExists(by: By, timeout: Duration = defaultWait) {
    getElementsWhenExists(by, timeout)
}

fun waitUntilNotExist(by: By, timeout: Duration = defaultWait) {
    tryWait(timeout, not(visibilityOfElementLocated(by))) { "Wait for element disappearing failed: seekBy=$by" }
}

fun waitUntilVisible(by: By, timeout: Duration = defaultWait) {
    getElementWhenVisible(by, timeout)
}

fun waitUntilInvisible(by: By, timeout: Duration = defaultWait) {
    tryWait(timeout, not(visibilityOfElementLocated(by))) { "Wait for element to disappear failed: seekBy=$by" }
}

fun waitUntilElementClickable(by: By, timeout: Duration = defaultWait) {
    getElementWhenClickable(by, timeout)
}

fun waitUntilTextExists(by: By, timeout: Duration = defaultWait) {
    tryWait(timeout, textMatches(by, Pattern.compile("(?s).+"))) { "Wait for element to have value, by:$by" }
}

fun waitUntilTextIs(by: By, value: String, timeout: Duration = defaultWait) {
    tryWait(timeout, textToBe(by, value)) { "Wait for element value 'to be x' failed: expected=$value by:$by" }
}

fun waitUntilTextIsNot(by: By, value: String, timeout: Duration = defaultWait) {
    tryWait(timeout, not(textToBe(by, value))) {
        "Wait for element value 'to not be x' failed: expectedNot=$value by=$by"
    }
}

fun getElement(by: By): WebElement {
    return browser().findElement(by)
}

fun getElements(by: By): List<WebElement> {
    return browser().findElements(by)
}

fun clickWhenClickable(by: By, timeout: Duration = defaultWait) {
    getElementWhenClickable(by, timeout).click()
}

fun exists(by: By): Boolean = getElements(by).isNotEmpty()

fun <T> tryWait(condition: ExpectedCondition<T?>, lazyErrorMessage: () -> String) =
    tryWait(defaultWait, defaultPoll, condition, lazyErrorMessage)

fun <T> tryWait(timeout: Duration = defaultWait, condition: ExpectedCondition<T?>, lazyErrorMessage: () -> String) =
    tryWait(timeout, defaultPoll, condition, lazyErrorMessage)

fun <T> tryWait(
    timeout: Duration = defaultWait,
    pollInterval: Duration = defaultPoll,
    condition: ExpectedCondition<T?>,
    lazyErrorMessage: () -> String,
): T =
    try {
        WebDriverWait(browser(), timeout, pollInterval).until<T>(condition)
    } catch (e: Exception) {
        logger.warn("${lazyErrorMessage()} cause=${e.message}")
        throw e
    }

fun getElementIfExists(by: By): WebElement? {
    return getElements(by).firstOrNull()
}
