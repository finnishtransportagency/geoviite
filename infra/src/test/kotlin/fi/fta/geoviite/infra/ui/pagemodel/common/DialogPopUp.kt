package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By

open class DialogPopUp(by: By = By.cssSelector("div.dialog__popup")): PageModel(by) {
    val title = rootElement.findElement(By.className("dialog__title")).text
    val contentElement = rootElement.findElement(By.className("dialog__content"))

    init {
        logger.info("Title: $title \n Content: ${contentElement.text}")
    }

    fun clickPrimaryButton() {
        val button = rootElement.findElement(By.cssSelector("button.button--primary"))
        logger.info("Click primary button ${button.text}")
        waitUntilElementIsClickable(button)
        button.click()
    }

    fun clickSecondaryButton() {
        val button = rootElement.findElement(By.cssSelector("button.button--secondary"))
        logger.info("Click secondary button")
        waitUntilElementIsClickable(button)
        button.click()
    }
}

class DialogPopUpWithTextField() : DialogPopUp() {
    fun inputTextField(input: String, textFieldIndx: Int = 0) =
        rootElement.findElements(By.cssSelector("input.text-field__input-element"))[textFieldIndx].sendKeys(input)

    fun inputTextField(inputs: List<String>) {
        logger.info("Input text fields [$inputs]")
        inputs.forEachIndexed { index, input ->
            rootElement.findElements(By.cssSelector("input.text-field__input-element"))[index].sendKeys(input)
        }
    }
}

