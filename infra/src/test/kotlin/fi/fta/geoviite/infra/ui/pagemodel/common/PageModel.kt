package fi.fta.geoviite.infra.ui.pagemodel.common

import childElementExists
import clickChildElement
import defaultWait
import fi.fta.geoviite.infra.ui.util.fetch
import fi.fta.geoviite.infra.ui.util.qaId
import fi.fta.geoviite.infra.ui.util.textContent
import getChildWhenMatches
import getChildWhenVisible
import getChildrenWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import waitUntilChildMatches
import waitUntilChildNotVisible
import waitUntilChildVisible
import java.time.Duration


abstract class PageModel(protected val elementFetch: () -> WebElement) {
    constructor(by: By): this(fetch(by))
    constructor(parentFetch: () -> WebElement, by: By): this(fetch(parentFetch, by))
    constructor(parent: PageModel, by: By): this(parent.elementFetch, by)

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val webElement: WebElement get() = elementFetch()

    protected inline operator fun <T> T.invoke(action: T.() -> Unit): T = apply(action)

    protected fun <T> childComponent(by: By, creator: (() -> WebElement) -> T, timeout: Duration = defaultWait) =
        creator { childElement(by, timeout) }

    protected fun childTextField(by: By, timeout: Duration = defaultWait) = childComponent(by, ::TextField, timeout)

    protected fun childButton(by: By, timeout: Duration = defaultWait) = childComponent(by, ::Button, timeout)

    protected fun clickButton(by: By, timeout: Duration = defaultWait) = childButton(by, timeout).click()

    @Deprecated("Relevant buttons should be marked with qa-id and used via that, rather than content")
    protected fun clickButtonByText(text: String, timeout: Duration = defaultWait) =
        clickButton(textContent(text), timeout)

    protected fun clickButtonByQaId(id: String, timeout: Duration = defaultWait) =
        clickButton(qaId(id), timeout)

    protected fun childElement(by: By, timeout: Duration = defaultWait): WebElement =
        getChildWhenVisible(elementFetch, by, timeout)

    protected fun childElements(by: By, timeout: Duration = defaultWait): List<WebElement> =
        getChildrenWhenVisible(elementFetch, by, timeout)

    protected fun clickChild(by: By, timeout: Duration = defaultWait) =
        clickChildElement(elementFetch, by, timeout)

    protected fun childText(by: By, timeout: Duration = defaultWait): String =
        childElement(by, timeout).text

    protected fun childTexts(by: By, timeout: Duration = defaultWait): List<String> =
        childElements(by, timeout).map(WebElement::getText)

    fun waitChildVisible(childBy: By, timeout: Duration = defaultWait) =
        waitUntilChildVisible({ webElement }, childBy, timeout)

    fun waitChildNotVisible(childBy: By, timeout: Duration = defaultWait) =
        waitUntilChildNotVisible({ webElement }, childBy, timeout)

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

    protected fun childExists(by: By) = webElement.childElementExists(by)
}
