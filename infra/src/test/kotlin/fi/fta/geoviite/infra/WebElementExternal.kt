package fi.fta.geoviite.infra

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(WebElement::class.java)

fun WebElement.findMandatoryByXpath(path: String, debugName: String): WebElement =
    requireNotNull(findByXpath(path)) {
        "Element child not found: parent=${this.tagName} target=$debugName childXPath=$path"
    }

fun WebElement.findByXpath(path: String): WebElement? = try {
    findElement(By.xpath(path))
} catch (e: org.openqa.selenium.NoSuchElementException) {
    logger.info("Element not found: parent=${this.tagName} seek=$path")
    null
}
