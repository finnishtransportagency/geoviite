package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement

open class Accordion(by: By) : PageModel(by) {
    enum class Toggle { OPEN, CLOSE }

    fun header(): String {
        logger.info("Get header")
        return childText(By.cssSelector("span.accordion__header-title"))
    }

    fun toggleVisibility() {
        logger.info("Toggle visibility")
        webElement.findElement(By.cssSelector("span.accordion__visibility")).click()
    }

    fun toggleAccordion(toggle: Toggle = Toggle.OPEN) {
        logger.info("Toggle accordion")
        when (toggle) {
            Toggle.OPEN -> openAccordion()
            Toggle.CLOSE -> closeAccordion()
        }
    }

    fun clickHeader() {
        logger.info("Click header")
        clickChild(By.cssSelector("span.accordion__header-title"))
    }

    // TODO: GVT-1935 These list elements hold a reference to the WebElement, risking staleness. Use ListModel to replace this.
    @Deprecated("Element risks staleness")
    fun listItems() = childElements(By.cssSelector("li")).map { element -> AccordionListItem(element) }

    fun listItemByName(itemName: String) = listItems().first { item -> item.name() == itemName }

    fun selectListItem(itemName: String) = apply {
        logger.info("Select '$itemName'")
        try {
            listItemByName(itemName).select()
        } catch (ex: java.util.NoSuchElementException) {
            logger.error("No such item [$itemName]! Available items ${listItems().map { it.name() }}")
        }
    }

    fun subAccordionByTitle(title: String): Accordion {
        logger.info("Open subAccordion '$title'")
        return Accordion(
            By.xpath(".//div[@class='accordion__body']/div/div[@class='accordion' and h4/span[contains(text(), '$title')]]")
        )
    }

    private fun openAccordion() {
        logger.info("Open accordion")
        if (childExists(By.cssSelector("ul li"))) {
            logger.warn("Accordion already open")
        } else {
            clickChild(By.cssSelector("span.accordion-toggle svg"))
            waitChildVisible(By.cssSelector("div.accordion__body"))
        }
    }

    private fun closeAccordion() {
        logger.info("Close accordion")
        if (childExists(By.cssSelector("ul li"))) {
            clickChild(By.cssSelector("span.accordion-toggle svg"))
        } else {
            logger.warn("Accordion already closed")
        }
    }
}

class AccordionListItem(val element: WebElement) {
    fun name(): String = element.findElement(By.cssSelector("span span")).text
    fun select() {
        element.findElement(By.cssSelector("span span")).click()
    }

    override fun toString(): String = name()
}
