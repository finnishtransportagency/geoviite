package fi.fta.geoviite.infra.ui.pagemodel.common

import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndClick
import waitUntilDoesNotExist
import kotlin.test.assertTrue

// TODO: implement a way to find a particular toaster message, so multiple ones don't hurt the test logic
//   One way to do this is to include the toaster localization string in an attribute and find the element by that
// TODO: most tests now ignore toasters or only check text.
//  Since most operations show a toaster for success, we should assert it always after an op and then close it
//  Rather than verify the toaster text, we should ensure that the correct toaster (by localization key or qa-id) is a success-toast (by class or qa-id)

val defaultToasterBy = By.cssSelector("div.Toastify__toast")
class Toaster(by: By = defaultToasterBy): PageModel(by) {

    private val messageElement: WebElement get() = webElement.findElement(By.cssSelector("div.Toastify__toast-text"))
    val title: String get() = webElement.findElement(By.cssSelector("span.Toastify__toast-header")).text

    fun close(): Unit = webElement.let { e ->
        e.waitAndClick()
        e.waitUntilDoesNotExist()
    }

    fun read(): String = messageElement.text

    fun readAndClose(): String = read().also { close() }

    // TODO: Rather than text, this should assert localization strings, qa-ids and/or success/fail status
    fun assertAndClose(partialContent: String): String = readAndClose().also { text ->
        assertTrue(text.contains(partialContent), "Wrong toaster message: expected=$partialContent actual=$text")
        logger.info("Toaster content verified: text=$text expectedPartial=$partialContent")
    }

    override fun toString() = ToStringBuilder.reflectionToString(this)
}
