package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By

open class E2EAccordion(accordionBy: By) : E2EViewFragment(accordionBy) {
    enum class Toggle {
        OPEN,
        CLOSE,
    }

    val header: String
        get() = childText(By.className("accordion__header-title"))

    fun toggleVisibility(): E2EAccordion = apply {
        logger.info("Toggle visibility")
        clickChild(By.className("accordion__visibility"))
    }

    fun toggleAccordion(toggle: Toggle = Toggle.OPEN): E2EAccordion = apply {
        logger.info("Toggle accordion")
        when (toggle) {
            Toggle.OPEN -> open()
            Toggle.CLOSE -> close()
        }
    }

    fun clickHeader(): E2EAccordion = apply {
        logger.info("Click on header")
        clickChild(By.className("accordion__header-title"))
    }

    fun open(): E2EAccordion = apply {
        logger.info("Open accordion")
        if (!childExists(By.className("accordion__body"))) {
            clickChild(By.cssSelector(".accordion-toggle svg"))
            waitUntilChildVisible(By.className("accordion__body"))
        }
    }

    fun close(): E2EAccordion = apply {
        logger.info("Close accordion")
        if (childExists(By.className("accordion__body"))) {
            clickChild(By.cssSelector(".accordion-toggle svg"))
            waitUntilChildInvisible(By.className("accordion__body"))
        }
    }
}
