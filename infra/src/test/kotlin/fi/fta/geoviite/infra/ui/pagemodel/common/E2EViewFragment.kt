package fi.fta.geoviite.infra.ui.pagemodel.common

import browser
import clickWhenClickable
import defaultWait
import exists
import fi.fta.geoviite.infra.authorization.DESIRED_ROLE_COOKIE_NAME
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenExists
import getElementWhenVisible
import getElementsWhenExists
import getElementsWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.pagefactory.ByChained
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import waitUntilExists
import waitUntilInvisible
import waitUntilVisible
import java.time.Duration


abstract class E2EViewFragment(protected val viewBy: By) {
    constructor(parent: E2EViewFragment, by: By) : this(ByChained(parent.viewBy, by))

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected fun childBy(vararg by: By): By = ByChained(viewBy, *by)

    protected inline operator fun <T> T.invoke(action: T.() -> Unit): T = apply(action)

    protected fun <T> childComponent(by: By, creator: (by: By) -> T): T = creator(childBy(by))

    protected fun childTextInput(by: By) = childComponent(by, ::E2ETextInput)

    protected fun childButton(by: By) = childComponent(by, ::E2EButton)

    protected fun childRadio(by: By) = childComponent(by, ::E2ERadio)

    protected fun childDropdown(by: By) = childComponent(by, ::E2EDropdown)

    protected fun childCheckbox(by: By) = childComponent(by, ::E2ECheckbox)

    protected fun childElement(by: By, timeout: Duration = defaultWait): WebElement =
        getElementWhenVisible(childBy(by), timeout)

    protected fun childElements(by: By, timeout: Duration = defaultWait): List<WebElement> =
        getElementsWhenVisible(childBy(by), timeout)

    protected fun clickChild(by: By, timeout: Duration = defaultWait): Unit = clickWhenClickable(childBy(by), timeout)

    protected fun clickButton(by: By) = childButton(by).click()

    protected fun childText(by: By, timeout: Duration = defaultWait): String = childElement(by, timeout).text

    protected fun childTexts(by: By, timeout: Duration = defaultWait): List<String> =
        childElements(by, timeout).map(WebElement::getText)

    //This will not check for element's visibility
    protected fun findElement(by: By, timeout: Duration = defaultWait): WebElement = getElementWhenExists(by, timeout)

    //This will not check for elements' visibility
    protected fun findElements(by: By, timeout: Duration = defaultWait): List<WebElement> =
        getElementsWhenExists(by, timeout)

    protected fun waitUntilChildVisible(by: By, timeout: Duration = defaultWait): Unit =
        waitUntilVisible(childBy(by), timeout)

    protected fun waitUntilChildInvisible(by: By, timeout: Duration = defaultWait): Unit =
        waitUntilInvisible(childBy(by), timeout)

    //This will not check for child's visibility
    protected fun waitUntilChildExists(by: By, timeout: Duration = defaultWait): Unit =
        waitUntilExists(childBy(by), timeout)

    protected fun childExists(by: By) = exists(childBy(by))

    fun waitUntilInvisible() = waitUntilInvisible(viewBy)
}
