package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import org.openqa.selenium.By
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import waitUntilNotExist

class E2EButton(val buttonBy: By) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun click(): E2EButton = apply {
        logger.info("Click on button")

        clickWhenClickable(buttonBy)
    }

    fun clickAndWaitToDisappear() {
        logger.info("Click and wait for button to disappear")

        click()
        waitUntilNotExist(buttonBy)
    }
}
