package fi.fta.geoviite.infra.ui.pagemodel.common

import browser
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import waitAndClick
import waitUntilDoesNotExist
import waitUntilVisible

private val logger: Logger = LoggerFactory.getLogger(E2EToast::class.java)

enum class ToastType {
    SUCCESS,
    INFO,
    ERROR
}

private val contentBy: By = By.className("Toastify__toast-text-body")
private val headerBy: By = By.className("Toastify__toast-header")
private val qaIdBy: By = By.className("Toastify__toast-content")
private val toasterBy: By = By.className("Toastify__toast")

data class E2EToast(
    val header: String,
    val content: String?,
    val type: ToastType,
    val qaId: String?,
) {

    constructor(element: WebElement) : this(
        header = element.findElement(headerBy).text,
        content = element.findElements(contentBy).takeIf { it.isNotEmpty() }?.get(0)?.text,
        qaId = element.findElement(qaIdBy).getAttribute("qa-id"),
        type = getToastType(element)
    )
}

private fun getToastType(toast: WebElement) = with(toast.getAttribute("class")) {
    when {
        contains("Toastify__toast--success") -> ToastType.SUCCESS
        contains("Toastify__toast--error") -> ToastType.ERROR
        contains("Toastify__toast--warning") -> ToastType.INFO
        else -> throw IllegalStateException("Could not determine toast type")
    }
}

private fun waitForToasts() {
    waitUntilVisible(toasterBy)
}

private fun getToasts(): List<Pair<E2EToast, WebElement>> {
    return browser().findElements(toasterBy).mapNotNull { e ->
        if (e.isDisplayed) E2EToast(e) to e else null
    }
}

private fun clearToast(toast: WebElement) {
    toast.waitAndClick()
    toast.waitUntilDoesNotExist()
}

fun waitAndClearToastByContent(content: String): E2EToast {
    waitForToasts()
    val toasts = getToasts()
    val matchingToast = toasts
        .firstOrNull { (t, _) -> t.content == content || t.header == content }
        ?.let { (toast, element) ->
            clearToast(element)
            toast
        }

    return checkNotNull(matchingToast) {
        "None of the toasts matched with content [$content]. Toasts that were visible: ${toasts.map { it.first }}"
    }
}

fun waitAndClearToast(qaId: String): E2EToast {
    waitForToasts()

    val toasts = getToasts()
    val matchingToast = toasts
        .firstOrNull { (t, _) -> t.qaId == qaId }
        ?.let { (toast, element) ->
            clearToast(element)
            toast
        }

    return checkNotNull(matchingToast) {
        "None of the toasts matched with qa-id [$qaId]. Toasts that were visible: ${toasts.map { it.first }}"
    }
}

fun <R> expectToast(fn: () -> R) {
    val oldToasts = getToasts().map { it.first }
    fn()

    waitForToasts()
    check(getToasts().any { (nt, _) -> !oldToasts.contains(nt) }) {
        "Expected a new toast but nothing appeared"
    }
}
