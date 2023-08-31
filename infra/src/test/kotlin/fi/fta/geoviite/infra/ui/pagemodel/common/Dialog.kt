package fi.fta.geoviite.infra.ui.pagemodel.common

import exists
import getChildWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

val defaultDialogBy: By = By.className("dialog")

open class E2EDialog(val by: By = defaultDialogBy) : E2EViewFragment(by) {
    
    private val titleElement: WebElement get() = getChildWhenVisible(elementFetch, By.className("dialog__title"))
    private val contentElement: WebElement get() = getChildWhenVisible(elementFetch, By.className("dialog__content"))

    val title: String get() = titleElement.text
    val content: E2EFormLayout get() = E2EFormLayout { contentElement }

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

    fun waitUntilClosed(fn: () -> Unit) = webElement.let { el ->
        fn()
        !el.exists()
    }
}

class E2EDialogWithTextField(by: By = defaultDialogBy) : E2EDialog(by) {
    fun inputValue(value: String, textFieldIdx: Int = 0): E2EDialog = apply {
        childComponents(By.cssSelector("input.text-field__input-element"), ::E2ETextInput)[textFieldIdx]
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
