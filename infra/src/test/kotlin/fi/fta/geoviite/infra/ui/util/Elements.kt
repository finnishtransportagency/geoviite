import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.browser
import java.time.Duration
import java.util.regex.Pattern
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable
import org.openqa.selenium.support.ui.ExpectedConditions.not
import org.openqa.selenium.support.ui.ExpectedConditions.presenceOfAllElementsLocatedBy
import org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated
import org.openqa.selenium.support.ui.ExpectedConditions.textMatches
import org.openqa.selenium.support.ui.ExpectedConditions.textToBe
import org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfAllElementsLocatedBy
import org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(E2EViewFragment::class.java)

val defaultWait: Duration = Duration.ofSeconds(10L)
val defaultPoll: Duration = Duration.ofMillis(100)

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
    return requireNotNull(
        tryWaitNullable(timeout, visibilityOfElementLocated(by)) { "Wait for element to be visible failed: seekBy=$by" }
    )
}

fun getElementWhenExists(by: By, timeout: Duration = defaultWait): WebElement {
    return tryWaitNonNull(timeout, presenceOfElementLocated(by) as ExpectedCondition<WebElement?>) {
        "Wait for element to exists failed: seekBy=$by"
    }
}

fun getElementsWhenVisible(by: By, timeout: Duration = defaultWait): List<WebElement> {
    return tryWaitNonNull(timeout, visibilityOfAllElementsLocatedBy(by)) {
        "Wait for elements to be visible failed: seekBy=$by"
    }
}

fun getElementsWhenExists(by: By, timeout: Duration = defaultWait): List<WebElement> {
    return tryWaitNonNull(timeout, presenceOfAllElementsLocatedBy(by)) {
        "Wait for elements to exists failed: seekBy=$by"
    }
}

fun getElementWhenClickable(by: By, timeout: Duration = defaultWait): WebElement {
    return tryWaitNonNull(timeout, elementToBeClickable(by)) { "Wait for element to be clickable, by=$by" }
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

fun tryWait(condition: ExpectedCondition<Boolean>, lazyErrorMessage: () -> String): Boolean =
    tryWait(defaultWait, defaultPoll, condition, lazyErrorMessage)

fun <T> tryWaitNonNull(condition: ExpectedCondition<T?>, lazyErrorMessage: () -> String): T =
    tryWaitNonNull(defaultWait, defaultPoll, condition, lazyErrorMessage)

fun <T> tryWaitNullable(condition: ExpectedCondition<T?>, lazyErrorMessage: () -> String): T? =
    tryWaitNullable(defaultWait, defaultPoll, condition, lazyErrorMessage)

fun tryWait(
    timeout: Duration = defaultWait,
    condition: ExpectedCondition<Boolean>,
    lazyErrorMessage: () -> String,
): Boolean = tryWait(timeout, defaultPoll, condition, lazyErrorMessage)

fun <T> tryWaitNonNull(
    timeout: Duration = defaultWait,
    condition: ExpectedCondition<T?>,
    lazyErrorMessage: () -> String,
): T = tryWaitNonNull(timeout, defaultPoll, condition, lazyErrorMessage)

fun <T> tryWaitNullable(
    timeout: Duration = defaultWait,
    condition: ExpectedCondition<T?>,
    lazyErrorMessage: () -> String,
): T? = tryWaitNullable(timeout, defaultPoll, condition, lazyErrorMessage)

fun tryWait(
    timeout: Duration = defaultWait,
    pollInterval: Duration = defaultPoll,
    condition: ExpectedCondition<Boolean>,
    lazyErrorMessage: () -> String,
): Boolean = tryWaitNonNull(timeout, pollInterval, condition as ExpectedCondition<Boolean?>, lazyErrorMessage)

fun <T> tryWaitNonNull(
    timeout: Duration = defaultWait,
    pollInterval: Duration = defaultPoll,
    condition: ExpectedCondition<T?>,
    lazyErrorMessage: () -> String,
): T = requireNotNull(tryWaitNullable(timeout, pollInterval, condition, lazyErrorMessage)) { lazyErrorMessage() }

fun <T> tryWaitNullable(
    timeout: Duration = defaultWait,
    pollInterval: Duration = defaultPoll,
    condition: ExpectedCondition<T?>,
    lazyErrorMessage: () -> String,
): T? =
    try {
        WebDriverWait(browser(), timeout, pollInterval).until<T>(condition)
    } catch (e: Exception) {
        logger.warn("${lazyErrorMessage()} cause=${e.message}")
        throw e
    }

fun getElementIfExists(by: By): WebElement? {
    return getElements(by).firstOrNull()
}
