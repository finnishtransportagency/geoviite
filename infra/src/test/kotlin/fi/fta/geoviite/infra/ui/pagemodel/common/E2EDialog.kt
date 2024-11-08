package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilNotExist

val DIALOG_BY: By = By.className("dialog")

open class E2EDialog(val dialogBy: By = DIALOG_BY) : E2EViewFragment(dialogBy) {

    val title: String
        get() = childText(By.className("dialog__title"))

    fun clickPrimaryButton() {
        logger.info("Click primary button")

        clickButton(By.cssSelector("button.button--primary"))
    }

    fun clickPrimaryWarningButton() {
        logger.info("Click primary warning button")

        clickButton(By.cssSelector("button.button--primary-warning"))
    }

    fun clickSecondaryButton() {
        logger.info("Click secondary button")

        clickButton(By.cssSelector("button.button--secondary"))
    }

    fun clickWarningButton() {
        logger.info("Click warning button")

        clickButton(By.cssSelector("button.button--warning"))
    }

    fun <T> waitUntilClosed(fn: () -> T): Unit =
        fn().run {
            logger.info("Waiting for dialog to disappear")

            waitUntilNotExist(dialogBy)
        }
}

class E2EDialogWithTextField(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {
    fun inputValue(value: String, textFieldIdx: Int = 0): E2EDialogWithTextField = apply {
        logger.info("Input field $textFieldIdx with value $value")

        childTextInput(
                ByChained(
                    By.className("dialog__content"),
                    By.xpath("(//*[@class='text-field__input-element'])[${textFieldIdx + 1}]"),
                )
            )
            .replaceValue(value)
    }

    fun inputValues(values: List<String>): E2EDialogWithTextField = apply {
        logger.info("Input values $values")

        values.forEachIndexed { index, value -> inputValue(value, index) }
    }

    fun selectInput(by: By): E2EDialogWithTextField = apply { clickChild(by) }
}
