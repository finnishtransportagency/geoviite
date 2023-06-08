package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.WebElement
import waitUntilElementClickable

class Button(fetch: () -> WebElement): PageModel(fetch) {
    fun click() {
        logger.info("Click button '${webElement.text}'")
        waitUntilElementClickable(webElement)
        // TODO: Check if this is actually needed
        Thread.sleep(500) //Fixes problems where button cannot be clicked while enabled
        webElement.click()
    }
}
