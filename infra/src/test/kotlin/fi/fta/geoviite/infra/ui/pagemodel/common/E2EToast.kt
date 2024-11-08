package fi.fta.geoviite.infra.ui.pagemodel.common

import childExists
import clickWhenClickable
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitUntilNotExist

enum class ToastType {
    SUCCESS,
    INFO,
    ERROR,
}

private val contentBy: By = By.className("Toastify__toast-text-body")
private val headerBy: By = By.className("Toastify__toast-header")

data class E2EToast(val header: String, val content: String?, val type: ToastType) {
    constructor(
        element: WebElement
    ) : this(
        header = element.findElement(headerBy).text,
        content = if (element.childExists(contentBy)) element.findElement(contentBy).text else null,
        type = getToastType(element),
    )
}

private fun getToastType(toast: WebElement) =
    with(toast.getAttribute("class")) {
        when {
            contains("Toastify__toast--success") -> ToastType.SUCCESS
            contains("Toastify__toast--error") -> ToastType.ERROR
            contains("Toastify__toast--warning") -> ToastType.INFO
            else -> error("Could not determine toast type")
        }
    }

private fun clearToast(toastBy: By) {
    clickWhenClickable(toastBy)
    waitUntilNotExist(toastBy)
}

fun waitAndClearToast(id: String): E2EToast {
    val toastBy = By.xpath("//div[starts-with(@id, 'toast') and contains(@id, '$id')]")

    return getElementWhenVisible(toastBy).let(::E2EToast).also { clearToast(toastBy) }
}
