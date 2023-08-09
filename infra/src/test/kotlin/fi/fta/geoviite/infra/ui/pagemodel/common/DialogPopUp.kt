package fi.fta.geoviite.infra.ui.pagemodel.common

import getChildWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitUntilDoesNotExist

open class DialogPopUp(by: By = By.cssSelector("div.dialog__popup")) : PageModel(by) {

    private val titleElement: WebElement get() = getChildWhenVisible(elementFetch, By.className("dialog__title"))
    private val contentElement: WebElement get() = getChildWhenVisible(elementFetch, By.className("dialog__content"))

    val title: String get() = titleElement.text
    val content: FormLayout get() = FormLayout { contentElement }

    init {
        logger.info("Title: $title \n Content: ${contentElement.text}")
    }

    fun clickPrimaryButton() = clickButton(By.cssSelector("button.button--primary"))

    fun clickPrimaryWarningButton() = clickButton(By.cssSelector("button.button--primary-warning"))

    fun clickSecondaryButton() = clickButton(By.cssSelector("button.button--secondary"))

    fun waitUntilClosed() = webElement.waitUntilDoesNotExist()
}

class DialogPopUpWithTextField : DialogPopUp() {
    fun inputTextField(input: String, textFieldIdx: Int = 0) =
        webElement.findElements(By.cssSelector("input.text-field__input-element"))[textFieldIdx].sendKeys(input)

    fun inputTextField(inputs: List<String>) {
        logger.info("Input text fields [$inputs]")
        inputs.forEachIndexed { index, input ->
            webElement.findElements(By.cssSelector("input.text-field__input-element"))[index].sendKeys(input)
        }
    }
}
