package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.WebElement
import waitAndClick
import waitUntilClickable
import waitUntilDoesNotExist

class Button(fetch: () -> WebElement) : PageModel(fetch) {
    fun click() {
        logger.info("Click button '${webElement.text}'")
        webElement.waitUntilClickable()
        webElement.waitAndClick()
    }

    fun clickAndWaitToDisappear() {
        val elementBeforeClick = webElement
        val name = elementBeforeClick.text
        click()
        logger.info("Wait until button is no longer visible '$name'")
        elementBeforeClick.waitUntilDoesNotExist()
    }
}
