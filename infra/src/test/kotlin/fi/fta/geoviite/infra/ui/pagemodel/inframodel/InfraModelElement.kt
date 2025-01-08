package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.localDateFromString
import fi.fta.geoviite.infra.ui.util.localDateTimeFromString
import java.time.LocalDateTime
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitUntilTextIs

class E2EInfraModelTable(tableBy: By) :
    E2ETable<E2EInfraModelTableRow>(
        tableBy = tableBy,
        headersBy = By.cssSelector("thead th"),
        rowsBy = By.cssSelector("tbody tr"),
    ) {
    override fun getRowContent(row: WebElement): E2EInfraModelTableRow {
        return E2EInfraModelTableRow(row.findElements(By.tagName("td")), headerElements)
    }

    fun getRow(projectName: String? = null, fileName: String? = null): E2EInfraModelTableRow? {
        return rows.firstOrNull { it.projectName == projectName || it.fileName == fileName }
    }

    fun sortBy(colName: String): E2EInfraModelTable = apply {
        logger.info("Sort infra models by column $colName")

        headerElements.first { it.text == colName }.click()
        waitUntilReady()
    }
}

data class E2EInfraModelTableRow(
    val projectName: String,
    val fileName: String,
    val trackNumber: String,
    val startKmNumber: String,
    val endKmNumber: String,
    val planPhase: String,
    val decisionPhase: String,
    val planTime: LocalDateTime,
    val created: LocalDateTime,
    val linked: LocalDateTime?,
    val name: String,
) {

    constructor(
        columns: List<WebElement>,
        headers: List<WebElement>,
    ) : this(
        name = getColumnContent("im-form.name-header", columns, headers),
        projectName = getColumnContent("im-form.project-name-header", columns, headers),
        fileName = getColumnContent("im-form.file-name-header", columns, headers),
        trackNumber = getColumnContent("im-form.track-number-header", columns, headers),
        startKmNumber = getColumnContent("im-form.km-start-header", columns, headers),
        endKmNumber = getColumnContent("im-form.km-end-header", columns, headers),
        planPhase = getColumnContent("im-form.plan-phase-header", columns, headers),
        decisionPhase = getColumnContent("im-form.decision-phase-header", columns, headers),
        planTime = localDateFromString(getColumnContent("im-form.created-at-header", columns, headers)),
        created = localDateTimeFromString(getColumnContent("im-form.uploaded-at-header", columns, headers)),
        linked =
            getColumnContent("im-form.linked-at-header", columns, headers)
                .takeIf(String::isNotBlank)
                ?.let(::localDateFromString),
    )
}

class E2EConfirmDialog : E2EDialog() {
    fun confirm() {
        logger.info("Confirm")

        waitUntilClosed { clickPrimaryButton() }
    }
}

abstract class E2EFormGroup(formBy: By) : E2EViewFragment(formBy) {

    val title: String
        get() = childText(By.className("formgroup__title"))

    internal fun formGroupField(qaId: String) = childComponent(byQaId(qaId), ::E2EFormGroupField)
}

internal class E2EFormGroupField(by: By) : E2EViewFragment(by) {

    val value: String
        get() {
            val dropdownValueBy = By.className("field-layout__value")

            val valueClass =
                if (childExists(dropdownValueBy)) dropdownValueBy else By.className("formgroup__field-value")

            return childText(valueClass)
        }

    fun selectValues(values: List<String>) = apply {
        logger.info("Selecting values [$values]")

        toggleEdit()

        values.forEachIndexed { index, value ->
            childDropdown(By.cssSelector(".dropdown:nth-child(${index + 1})")).selectByName(value)
        }

        toggleEdit()
    }

    fun createAndSelectNewValue(newValues: List<String>, toastId: String) = apply {
        logger.info("Create new dropdown option with values $newValues")

        toggleEdit()

        childDropdown(By.className("dropdown")).open().new()

        E2EDialogWithTextField().inputValues(newValues).clickPrimaryButton().also { waitAndClearToast(toastId) }

        toggleEdit()
    }

    fun waitUntilFieldIs(value: String) = apply {
        waitUntilTextIs(childBy(By.className("formgroup__field-value")), value)
    }

    fun selectValue(value: String) = apply {
        selectValues(listOf(value))

        waitUntilFieldIs(value)
    }

    private fun toggleEdit() = clickChild(By.xpath("div[@class='formgroup__edit-icon']/button"))
}
