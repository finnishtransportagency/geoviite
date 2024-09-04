package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class E2ETextInput(private val inputBy: By) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val webElement: WebElement
        get() = getElementWhenVisible(inputBy)

    val value: String
        get() = webElement.getAttribute("value")

    fun replaceValue(text: String): E2ETextInput = apply {
        clear()
        inputValue(text)
    }

    fun inputValue(text: String): E2ETextInput = apply {
        logger.info("Input value $text")

        webElement.sendKeys(text)
    }

    fun clear(): E2ETextInput = apply {
        logger.info("Clear input")

        clickWhenClickable(inputBy)

        webElement.sendKeys(Keys.chord(Keys.COMMAND, "a"))
        webElement.sendKeys(Keys.BACK_SPACE)
        webElement.sendKeys(Keys.chord(Keys.CONTROL, "a"))
        webElement.sendKeys(Keys.BACK_SPACE)
    }
}
