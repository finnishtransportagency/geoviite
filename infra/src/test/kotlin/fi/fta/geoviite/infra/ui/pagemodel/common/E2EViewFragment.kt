package fi.fta.geoviite.infra.ui.pagemodel.common

import childExists
import defaultWait
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.byText
import fi.fta.geoviite.infra.ui.util.fetch
import getChildElement
import getChildElements
import getChildWhenMatches
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tryWait
import waitAndClick
import waitUntilChildMatches
import waitUntilChildNotVisible
import waitUntilChildVisible
import java.time.Duration


abstract class E2EViewFragment(protected val elementFetch: ElementFetch) {
    constructor(by: By) : this(fetch(by))
    constructor(parentFetch: ElementFetch, by: By) : this(fetch(parentFetch, by))
    constructor(parent: E2EViewFragment, by: By) : this(parent.elementFetch, by)

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val webElement: WebElement get() = tryWait<WebElement>({ elementFetch() }) { "Could not fetch element" }

    protected inline operator fun <T> T.invoke(action: T.() -> Unit): T = apply(action)

    protected fun <T> childComponent(by: By, creator: (ElementFetch) -> T, timeout: Duration = defaultWait) =
        creator { childElement(by, timeout) }

    protected fun <T> childComponents(by: By, creator: (ElementFetch) -> T, timeout: Duration = defaultWait) =
        childElements(by, timeout).map { e -> creator { e } }

    protected fun childTextInput(by: By, timeout: Duration = defaultWait) = childComponent(by, ::E2ETextInput, timeout)

    protected fun childButton(by: By, timeout: Duration = defaultWait) = childComponent(by, ::E2EButton, timeout)

    protected fun clickButton(by: By, timeout: Duration = defaultWait) = childButton(by, timeout).click()

    protected fun clickButtonByText(text: String, timeout: Duration = defaultWait) = clickButton(byText(text), timeout)

    protected fun clickButtonByQaId(id: String, timeout: Duration = defaultWait) = clickButton(byQaId(id), timeout)

    protected fun childElement(by: By, timeout: Duration = defaultWait): WebElement =
        webElement.getChildElement(by, timeout)

    protected fun childElements(by: By, timeout: Duration = defaultWait): List<WebElement> =
        webElement.getChildElements(by, timeout)

    protected fun currentChildElements(by: By): List<WebElement> = webElement.findElements(by)

    protected fun clickChild(by: By, timeout: Duration = defaultWait) =
        webElement.getChildElement(by, timeout).waitAndClick(timeout)

    protected fun childText(by: By, timeout: Duration = defaultWait): String = childElement(by, timeout).text

    protected fun childTexts(by: By, timeout: Duration = defaultWait): List<String> =
        childElements(by, timeout).map(WebElement::getText)

    fun waitChildVisible(childBy: By, timeout: Duration = defaultWait) =
        webElement.waitUntilChildVisible(childBy, timeout)

    fun waitChildNotVisible(childBy: By, timeout: Duration = defaultWait) =
        webElement.waitUntilChildNotVisible(childBy, timeout)

    fun waitChildMatches(
        childBy: By,
        check: (index: Int, child: WebElement) -> Boolean,
        timeout: Duration = defaultWait,
    ) = waitUntilChildMatches(elementFetch, childBy, check, timeout)

    fun childElementWhenMatches(
        childBy: By,
        check: (index: Int, child: WebElement) -> Boolean,
        timeout: Duration = defaultWait,
    ): Pair<Int, WebElement> = getChildWhenMatches(elementFetch, childBy, check, timeout)

    protected fun childExists(by: By) = webElement.childExists(by)
}
