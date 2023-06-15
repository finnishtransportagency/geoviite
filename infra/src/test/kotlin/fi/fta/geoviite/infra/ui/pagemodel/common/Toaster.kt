package fi.fta.geoviite.infra.ui.pagemodel.common

import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndClick
import waitUntilDoesNotExist
import waitUntilExists
import kotlin.test.assertTrue


val defaultToasterBy = By.cssSelector("div.Toastify__toast")

// TODO: GVT-1934 fix toaster logic
//   Implement a way to find a particular toaster message, so multiple ones don't hurt the test logic
//   Rather than verify the toaster text, we should ensure that the correct toaster (by localization key or qa-id) is a success-toast (by class or qa-id)
class Toaster(by: By = defaultToasterBy): PageModel(by) {

    private val messageElement: WebElement get() = webElement.findElement(By.cssSelector("div.Toastify__toast-text"))
    val title: String get() = webElement.findElement(By.cssSelector("span.Toastify__toast-header")).text

    fun close(): Unit = webElement.let { e ->
        e.waitAndClick()
        e.waitUntilDoesNotExist()
    }

    fun read(): String = messageElement.text

    fun readAndClose(): String = read().also { close() }

    fun assertAndClose(partialContent: String): String = read().also { text ->
        assertTrue(text.contains(partialContent), "Wrong toaster message: expected=$partialContent actual=$text")
        logger.info("Toaster content verified: text=$text expectedPartial=$partialContent")
        close()
    }

    override fun toString() = ToStringBuilder.reflectionToString(this)
    fun waitUntilVisible() { webElement.waitUntilExists() }
}
