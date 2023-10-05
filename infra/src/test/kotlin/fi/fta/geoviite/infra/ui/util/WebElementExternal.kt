import org.openqa.selenium.*
import org.openqa.selenium.support.ui.ExpectedConditions.*
import java.time.Duration

fun WebElement.getChildElement(byCondition: By, timeout: Duration = defaultWait): WebElement {
    waitUntilChildExists(byCondition, timeout)
    return findElement(byCondition)
}

fun WebElement.getChildElementIfExists(byCondition: By, timeout: Duration = defaultWait): WebElement? = try {
    this.getChildElement(byCondition, timeout)
} catch (e: NoSuchElementException) {
    null
} catch (e: TimeoutException) {
    null
}

fun WebElement.getChildElements(byCondition: By, timeout: Duration = defaultWait): List<WebElement> {
    waitUntilChildExists(byCondition, timeout)
    return findElements(byCondition)
}

fun WebElement.getChildElementsIfExists(byCondition: By, timeout: Duration = defaultWait): List<WebElement> = try {
    getChildElements(byCondition, timeout)
} catch (e: NoSuchElementException) {
    emptyList()
}

fun WebElement.waitUntilChildNotVisible(byCondition: By, timeout: Duration = defaultWait) = tryWait(
    timeout,
    not(visibilityOfNestedElementsLocatedBy(this, byCondition)),
) {
    "Wait for child disappearing failed: parent=${getInnerHtml()} seekBy=$byCondition"
}

fun WebElement.waitUntilChildVisible(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, visibilityOfNestedElementsLocatedBy(this, byCondition)) {
        "Wait for child visible failed: parent=${getInnerHtml()} seekBy=$byCondition"
    }

fun WebElement.waitUntilChildExists(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, presenceOfNestedElementLocatedBy(this, byCondition)) {
        "Wait for child exists failed: parent=${getInnerHtml()} seekBy=$byCondition"
    }

fun WebElement.childExists(byCondition: By) = this.findElements(byCondition).isNotEmpty()

fun WebElement.childNotExists(byCondition: By) = this.findElements(byCondition).isEmpty()

fun WebElement.waitUntilChildNotExist(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, not(presenceOfNestedElementLocatedBy(this, byCondition))) {
        "Wait for child exists failed: parent=${getInnerHtml()} seekBy=$byCondition"
    }

fun WebElement.waitUntilExists(timeout: Duration = defaultWait) = tryWait(timeout, { exists() }) {
    "Wait for element appearing failed: element=${getInnerHtml()}"
}

fun WebElement.waitUntilNotExist(timeout: Duration = defaultWait) = tryWait(timeout, { !exists() }) {
    "Wait for element disappearing failed: element=${getInnerHtml()}"
}

fun WebElement.waitUntilClickable(timeout: Duration = defaultWait) = tryWait(timeout, elementToBeClickable(this)) {
    "Wait for element clickable failed: element=${getInnerHtml()}"
}

fun WebElement.getInnerHtml(): String = try {
    getAttribute("innerHTML")
} catch (e: Exception) {
    "[cant get innerHtml: cause=${e.message}]"
}

fun WebElement.waitAndClick(timeout: Duration = defaultWait) {
    waitUntilClickable(timeout)
    click()
}

fun WebElement.exists(): Boolean = try {
    isDisplayed
} catch (e: WebDriverException) {
    false
}
