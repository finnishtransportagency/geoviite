package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import waitAndClick

class E2ECheckbox(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {
    fun click(): E2ECheckbox = apply {
        logger.info("Click button '${webElement.text}'")
        webElement.waitAndClick()
    }
}
