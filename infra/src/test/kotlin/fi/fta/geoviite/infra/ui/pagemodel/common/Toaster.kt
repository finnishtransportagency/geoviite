package fi.fta.geoviite.infra.ui.pagemodel.common

import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Toaster(toasterElement: WebElement) {
    private val logger: Logger = LoggerFactory.getLogger(Toaster::class.java)


    val messageElement = toasterElement.findElement(By.cssSelector("div.Toastify__toast-text"))
    val message = toasterElement.findElement(By.cssSelector("div.Toastify__toast-text")).text
    val title = toasterElement.findElement(By.cssSelector("span.Toastify__toast-header")).text
    val html = toasterElement.getAttribute("innerHTML")

    init {
        //closes toaster after reading content
        messageElement.click()
        logger.info("Title: $title \n Message: $message")

    }

    override fun toString() =
        ToStringBuilder.reflectionToString(this)
}