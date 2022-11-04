package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement

open class Accordion(by: By) : PageModel(by) {
    enum class Toggle {OPEN, CLOSE}

    fun header(): String {
        logger.info("Get header")
        return getChildElementStaleSafe(By.cssSelector("span.accordion__header-title")).text
    }

    fun toggleVisibility() {
        logger.info("Toggle visibility")
        rootElement.findElement(By.cssSelector("span.accordion__visibility")).click()
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
        getChildElementStaleSafe(By.cssSelector("span.accordion__header-title")).click()
    }

    fun listItems() = getChildElementsStaleSafe(By.cssSelector("li")).map { element -> AccordionListItem(element) }

    fun selectListItem(itemName: String) = this {
        logger.info("Select '$itemName'")
        try {
            listItems().first { item -> item.name() == itemName}.select()
        } catch (ex: java.util.NoSuchElementException) {
            logger.error("No such item [$itemName]! Available items ${listItems().map { it.name() }}")
        }
    }

    fun subAccordioByTitle(title: String): Accordion {
        logger.info("Open subaccordion '$title'")
        return Accordion(
            By.xpath(".//div[@class='accordion__body']/div/div[@class='accordion' and h4/span[contains(text(), '$title')]]")
        )
    }

    private fun openAccordion() {
        logger.info("Open accordion")
        if (childElementExists(By.cssSelector("ul li"))) {
            logger.warn("Accordion already open")
            return
        }
        getChildElementStaleSafe(By.cssSelector("span.accordion-toggle svg")).click()
        getChildElementStaleSafe(By.cssSelector("div.accordion__body"))

    }

    private fun closeAccordion() {
        logger.info("Close accordion")
        if(childElementExists(By.cssSelector("ul li"))) {
            getChildElementStaleSafe(By.cssSelector("span.accordion-toggle svg")).click()
            return
        }
        logger.warn("Accordion already closed")
    }

}

class AccordionListItem(val element: WebElement) {
    fun name() = element.findElement(By.cssSelector("span span")).text
    fun select() {
        element.findElement(By.cssSelector("span span")).click()
    }

    override fun toString(): String = name()
}