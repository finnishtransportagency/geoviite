package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement

class E2ETextInput(private val inputBy: By) {

    private val webElement: WebElement get() = getElementWhenVisible(inputBy)

    val value: String get() = webElement.getAttribute("value")

    fun inputValue(text: String): E2ETextInput = apply {
        webElement.sendKeys(text)
    }

    fun clear(): E2ETextInput = apply {
        clickWhenClickable(inputBy)

        webElement.sendKeys(Keys.chord(Keys.COMMAND, "a"))
        webElement.sendKeys(Keys.BACK_SPACE)
        webElement.sendKeys(Keys.chord(Keys.CONTROL, "a"))
        webElement.sendKeys(Keys.BACK_SPACE)
    }
}
