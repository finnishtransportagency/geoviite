package fi.fta.geoviite.infra.ui.pagemodel.common

import childElementExists
import clickChildElement
import defaultWait
import fi.fta.geoviite.infra.ui.util.fetch
import fi.fta.geoviite.infra.ui.util.qaId
import fi.fta.geoviite.infra.ui.util.textContent
import getChildWhenVisible
import getChildrenWhenVisible
import getOptionalChildWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import waitUntilChildVisible
import java.time.Duration


abstract class PageModel(protected val elementFetch: () -> WebElement) {
    constructor(by: By): this(fetch(by))
    constructor(parent: WebElement, by: By): this(fetch(parent, by))
    constructor(parent: PageModel, by: By): this(fetch(parent.webElement, by))

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
        getChildWhenVisible(webElement, by, timeout)

    protected fun optionalChildElement(by: By, timeout: Duration = defaultWait): WebElement? =
        getOptionalChildWhenVisible(webElement, by, timeout)

    protected fun childElements(by: By, timeout: Duration = defaultWait): List<WebElement> =
        getChildrenWhenVisible(webElement, by, timeout)

    protected fun clickChild(by: By, timeout: Duration = defaultWait) =
        clickChildElement(webElement, by, timeout)

    protected fun childText(by: By, timeout: Duration = defaultWait): String =
        childElement(by, timeout).text

    protected fun childTexts(by: By, timeout: Duration = defaultWait): List<String> =
        childElements(by, timeout).map(WebElement::getText)

    protected fun waitChildVisible(by: By, timeout: Duration = defaultWait) =
        waitUntilChildVisible(webElement, by, timeout)

    protected fun childExists(by: By) = childElementExists(webElement, by)
}
