package fi.fta.geoviite.infra.ui.pagemodel.common

import clearInput
import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement

class E2ETextInput(private val fetch: ElementFetch) {

    private val webElement: WebElement get() = fetch()

    val value: String get() = webElement.getAttribute("value")

    fun inputValue(text: String): E2ETextInput = apply {
        webElement.sendKeys(text)
    }

    fun clear(): E2ETextInput = apply {
        clearInput(webElement)
    }
}
