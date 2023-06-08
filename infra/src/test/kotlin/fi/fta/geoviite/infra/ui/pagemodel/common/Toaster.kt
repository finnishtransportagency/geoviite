package fi.fta.geoviite.infra.ui.pagemodel.common

import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class Toaster(by: By): PageModel(by) {

    private val messageElement: WebElement get() = webElement.findElement(By.cssSelector("div.Toastify__toast-text"))
    val message: String get() = messageElement.text
    val title: String get() = webElement.findElement(By.cssSelector("span.Toastify__toast-header")).text

    fun click() = messageElement.click()

    fun readAndClose(): String = message.also { click() }

    override fun toString() = ToStringBuilder.reflectionToString(this)
}
