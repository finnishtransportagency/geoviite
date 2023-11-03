package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained

abstract class E2EFormGroup(formBy: By) : E2EViewFragment(formBy) {

    protected val title: String get() = childText(By.className("formgroup__title"))

    protected fun getValueForField(fieldName: String): String {
        logger.info("Get value for field [$fieldName]")

        val formBy = getFormBy(fieldName)
        val fieldBy = ByChained(formBy, By.className("field-layout__value"))
        val valueBy = if (childExists(fieldBy)) fieldBy else formBy

        return childText(valueBy)
    }

    protected fun selectDropdownValues(label: String, values: List<String>): E2EFormGroup = apply {
        logger.info("Change dropdown field [$label] to [$values]")

        clickEditIcon(label)

        values.forEachIndexed { index, value ->
            childComponent(
                ByChained(getFormBy(label), By.cssSelector(".dropdown:nth-child(${index + 1})")),
                ::E2EDropdown
            ).select(value)
        }

        clickEditIcon(label)
    }

    protected fun selectNewDropdownValue(label: String, values: List<String>): E2EFormGroup = apply {
        logger.info("Add and change dropdown value field [$label] to [$values]")

        clickEditIcon(label)

        childComponent(ByChained(getFormBy(label), By.className("dropdown")), ::E2EDropdown)
            .open()
            .new()

        E2EDialogWithTextField()
            .inputValues(values)
            .clickPrimaryButton()

        clickEditIcon(label)
    }

    //todo Replace this with common component or with qaIds
    private fun getFormBy(fieldName: String) =
        By.xpath(
            ".//div[@class='formgroup__field' and div[text() = '$fieldName']]" +
                    "/div[@class='formgroup__field-value']"
        )


    //todo Replace this with common component or with qaIds
    protected fun clickEditIcon(fieldName: String): E2EFormGroup = apply {
        logger.info("Enable editing in field $fieldName")

        clickChild(
            By.xpath(
                ".//div[@class='formgroup__field' and div[text() = '$fieldName']]" +
                        "/div[@class='formgroup__edit-icon']/div"
            )
        )
    }
}
