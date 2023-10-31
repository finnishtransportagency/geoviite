package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By

open class E2EAccordion(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {
    enum class Toggle { OPEN, CLOSE }

    constructor(by: By) : this(fetch(by))

    val header: String
        get() {
            logger.info("Get header")
            return childText(By.className("accordion__header-title"))
        }

    fun toggleVisibility(): E2EAccordion = apply {
        logger.info("Toggle visibility")
        clickButton(By.className("accordion__visibility"))
    }

    fun toggleAccordion(toggle: Toggle = Toggle.OPEN): E2EAccordion = apply {
        logger.info("Toggle accordion")
        when (toggle) {
            Toggle.OPEN -> open()
            Toggle.CLOSE -> close()
        }
    }

    fun clickHeader(): E2EAccordion = apply {
        logger.info("Click header")
        clickChild(By.className("accordion__header-title"))
    }

    fun open(): E2EAccordion = apply {
        logger.info("Open accordion")
        if (!childExists(By.className("accordion__body"))) {
            clickChild(By.cssSelector(".accordion-toggle svg"))
            waitChildVisible(By.className("accordion__body"))
        }
    }

    private fun close(): E2EAccordion = apply {
        logger.info("Close accordion")
        if (childExists(By.className("accordion__body"))) {
            clickChild(By.cssSelector(".accordion-toggle svg"))
            waitChildNotVisible(By.className("accordion__body"))
        }
    }
}
