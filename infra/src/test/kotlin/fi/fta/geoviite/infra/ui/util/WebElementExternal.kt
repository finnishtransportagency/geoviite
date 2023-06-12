import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import java.time.Duration

fun WebElement.childElementExists(byCondition: By) =
    getChildElementIfExists(byCondition) != null

fun WebElement.getChildElementIfExists(byCondition: By): WebElement? = try {
    findElement(byCondition)
} catch (ex: NoSuchElementException) {
    null
}

fun WebElement.getChildElements(byCondition: By): List<WebElement> = try {
    findElements(byCondition)
} catch (ex: NoSuchElementException) {
    listOf()
}

fun WebElement.waitUntilChildNotVisible(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(
        timeout,
        ExpectedConditions.not(ExpectedConditions.visibilityOfNestedElementsLocatedBy(this, byCondition)),
    ) {
        "Wait for child disappearing failed: parent=${getInnerHtml()} seekBy=$byCondition"
    }

fun WebElement.waitUntilChildExists(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, ExpectedConditions.presenceOfNestedElementLocatedBy(this, byCondition)) {
        "Wait for child exists failed: parent=${getInnerHtml()} seekBy=$byCondition"
    }

fun WebElement.waitUntilDoesNotExist(timeout: Duration = defaultWait) =
    tryWait(timeout, ExpectedConditions.not(ExpectedConditions.visibilityOf(this))) {
        "Wait for element disappearing failed: element=${getInnerHtml()}"
    }
fun WebElement.waitUntilChildVisible(byCondition: By, timeout: Duration = defaultWait) =
    tryWait(timeout, ExpectedConditions.visibilityOfNestedElementsLocatedBy(this, byCondition)) {
        "Wait for child visible failed: parent=${getInnerHtml()} seekBy=$byCondition"
    }

fun WebElement.waitUntilClickable(timeout: Duration = defaultWait) =
    tryWait(timeout, ExpectedConditions.elementToBeClickable(this)) {
        "Wait for element clickable failed: element=${getInnerHtml()}"
    }

fun WebElement.getInnerHtml() = getAttribute("innerHTML")

fun WebElement.waitAndClick(timeout: Duration = defaultWait) {
    waitUntilClickable(timeout)
    click()
}
