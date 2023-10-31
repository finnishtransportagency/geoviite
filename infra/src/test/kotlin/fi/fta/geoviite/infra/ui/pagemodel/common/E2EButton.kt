package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import waitAndClick
import waitUntilNotExist

class E2EButton(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {
    fun click(): E2EButton = apply {
        logger.info("Click button '${webElement.text}'")
        webElement.waitAndClick()
    }

    fun clickAndWaitToDisappear() {
        val element = webElement;
        val name = element.text
        click()
        logger.info("Wait until button is no longer visible '$name'")

        element.waitUntilNotExist()
    }
}
