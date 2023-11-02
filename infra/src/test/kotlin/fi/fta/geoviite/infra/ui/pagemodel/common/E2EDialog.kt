package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilNotExist

val DIALOG_BY: By = By.className("dialog")
private val CONTENT_BY: By = By.className("dialog__content")

open class E2EDialog(val dialogBy: By = DIALOG_BY) : E2EViewFragment(dialogBy) {

    private val titleElement: WebElement get() = childElement(By.className("dialog__title"))
    private val contentElement: WebElement get() = childElement(CONTENT_BY)

    val title: String get() = titleElement.text

    val content: E2EFormLayout get() = childComponent(CONTENT_BY, ::E2EFormLayout)

    init {
        logger.info("Title: $title \n Content: ${contentElement.text}")
    }

    fun clickPrimaryButton() {
        clickButton(By.cssSelector("button.button--primary"))
    }

    fun clickPrimaryWarningButton() {
        clickButton(By.cssSelector("button.button--primary-warning"))
    }

    fun clickSecondaryButton() {
        clickButton(By.cssSelector("button.button--secondary"))
    }

    fun clickWarningButton() {
        clickButton(By.cssSelector("button.button--warning"))
    }

    fun <T> waitUntilClosed(fn: () -> T): Unit = fn().run {
        waitUntilNotExist(dialogBy)
    }
}

class E2EDialogWithTextField(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {
    fun inputValue(value: String, textFieldIdx: Int = 0): E2EDialog = apply {
        childTextInput(
            ByChained(
                By.className("dialog__content"),
                By.xpath("(//*[@class='text-field__input-element'])[${textFieldIdx + 1}]")
            )
        )
            .clear()
            .inputValue(value)
    }

    fun inputValues(values: List<String>): E2EDialog = apply {
        logger.info("Input text fields [$values]")

        values.forEachIndexed { index, value ->
            inputValue(value, index)
        }
    }
}
