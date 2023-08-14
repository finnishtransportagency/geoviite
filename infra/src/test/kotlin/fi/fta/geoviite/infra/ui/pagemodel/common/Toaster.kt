package fi.fta.geoviite.infra.ui.pagemodel.common

import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import waitAndClick
import waitUntilDoesNotExist
import waitUntilExists
import kotlin.test.assertTrue

val defaultToasterBy: By = By.className("Toastify__toast")

// TODO: GVT-1934 fix toaster logic
//   Implement a way to find a particular toaster message, so multiple ones don't hurt the test logic
//   Rather than verify the toaster text, we should ensure that the correct toaster (by localization key or qa-id) is a success-toast (by class or qa-id)
class E2EToaster(by: By = defaultToasterBy) : E2EViewFragment(by) {

    val content: String get() = childText(By.className("Toastify__toast-text"))

    fun close() {
        webElement.also { e ->
            e.waitAndClick()
            e.waitUntilDoesNotExist()
        }
    }

    fun readAndClose() = content.also { close() }

    fun assertAndClose(partialContent: String) {
        assertTrue(content.contains(partialContent), "Wrong toaster message: expected=$partialContent actual=$content")
        logger.info("Toaster content verified: text=$content expectedPartial=$partialContent")
        close()
    }

    override fun toString(): String = ToStringBuilder.reflectionToString(this)

    fun waitUntilVisible(): E2EToaster = apply {
        webElement.waitUntilExists()
    }
}
