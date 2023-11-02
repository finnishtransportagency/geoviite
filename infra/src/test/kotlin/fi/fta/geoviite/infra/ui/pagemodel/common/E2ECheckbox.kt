package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import org.openqa.selenium.By
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class E2ECheckbox(val checkboxBy: By) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun click(): E2ECheckbox = apply {
        logger.info("Click")

        clickWhenClickable(checkboxBy)
    }
}
