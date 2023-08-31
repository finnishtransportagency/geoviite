package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EFormGroup
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETableRow
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateFromString
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateTimeFromString
import fi.fta.geoviite.infra.ui.util.ElementFetch
import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndAssertToaster
import java.time.LocalDateTime


class E2EInfraModelTable(tableFetch: ElementFetch) : E2ETable<E2EInfraModelTableRow>(
    tableFetch = tableFetch,
    rowsBy = By.cssSelector("tbody tr"),
    getContent = { idx, element ->
        E2EInfraModelTableRow(
            index = idx,
            row = element,
            headers = tableFetch().findElements(By.cssSelector("thead th")).map { it.getAttribute("qa-id") })
    }
) {
    private val headers: List<WebElement> get() = childElements(By.cssSelector("thead th"))

    fun getRow(projectName: String? = null, fileName: String? = null): E2EInfraModelTableRow? {
        return rows.find { it.projectName == projectName || it.fileName == fileName }
    }

    fun sortBy(colName: String) {
        logger.info("Select column $colName")
        headers.first { it.text == colName }.click()
    }
}

class E2EInfraModelTableRow(index: Int, row: WebElement, headers: List<String>) : E2ETableRow(index, row, headers) {
    val projectName: String get() = getColumnContent("im-form.name-header")
    val fileName: String get() = getColumnContent("im-form.file-name-header")
    val trackNumber: String get() = getColumnContent("im-form.track-number-header")
    val startKmNumber: String get() = getColumnContent("im-form.km-start-header")
    val endKmNumber: String get() = getColumnContent("im-form.km-end-header")
    val planPhase: String get() = getColumnContent("im-form.plan-phase-header")
    val decisionPhase: String get() = getColumnContent("im-form.decision-phase-header")
    val planTime: LocalDateTime get() = localDateFromString(getColumnContent("im-form.created-at-header"))
    val created: LocalDateTime get() = localDateTimeFromString(getColumnContent("im-form.uploaded-at-header"))
    val linked: LocalDateTime get() = localDateTimeFromString(getColumnContent("im-form.linked-at-header"))

    override fun toString(): String = ToStringBuilder.reflectionToString(this)
}

class E2EMetaFormGroup(elementFetch: ElementFetch) : E2EFormGroup(elementFetch) {
    val projectName: String get() = getValueForField("Projektin nimi")
    val author: String get() = getValueForField("Suunnitteluyritys")

    fun selectNewProject(newProject: String) {
        selectNewDropdownValue("Projektin nimi", listOf(newProject))
        waitAndAssertToaster("Uusi projekti luotu")
    }

    fun selectNewAuthor(newAuthor: String) {
        selectNewDropdownValue("Suunnitteluyritys", listOf(newAuthor))
        waitAndAssertToaster("Uusi suunnitteluyritys luotu")
    }
}

class E2ELocationFormGroup(elementFetch: ElementFetch) : E2EFormGroup(elementFetch) {
    val trackNumber: String get() = getValueForField("Ratanumero")
    val kmNumberRange: String get() = getValueForField("Ratakilometriväli")
    val coordinateSystem: String get() = getValueForField("Koordinaattijärjestelmä")
    val verticalCoordinateSystem: String get() = getValueForField("Korkeusjärjestelmä")

    fun selectTrackNumber(trackNumber: String): E2ELocationFormGroup = apply {
        selectDropdownValues("Ratanumero", listOf(trackNumber))
        clickEditIcon("Ratanumero")
    }

    fun selectNewTrackNumber(trackNumber: String, description: String): E2ELocationFormGroup = apply {
        selectNewDropdownValue("Ratanumero", listOf(trackNumber, description))
        waitAndAssertToaster("Ratanumero tallennettu")
        clickEditIcon("Ratanumero")
    }

    fun selectVerticalCoordinateSystem(coordinateSystem: String): E2ELocationFormGroup = apply {
        selectDropdownValues("Korkeusjärjestelmä", listOf(coordinateSystem))
    }

    fun selectCoordinateSystem(coordinateSystem: String): E2ELocationFormGroup = apply {
        selectDropdownValues("Koordinaattijärjestelmä", listOf(coordinateSystem))
    }
}

class E2EQualityFormGroup(elementFetch: ElementFetch) : E2EFormGroup(elementFetch) {
    val planPhase: String get() = getValueForField("Suunnitteluvaihe")
    val decisionPhase: String get() = getValueForField("Vaiheen tarkennus")
    val measurementMethod: String get() = getValueForField("Laatu")
    val elevationMeasurementMethod: String get() = getValueForField("Korkeusasema")


    fun selectPlanPhase(phase: String): E2EQualityFormGroup = apply {
        selectDropdownValues("Suunnitteluvaihe", listOf(phase))
    }

    fun selectDecisionPhase(decision: String): E2EQualityFormGroup = apply {
        selectDropdownValues("Vaiheen tarkennus", listOf(decision))
    }

    fun selectMeasurementMethod(method: String): E2EQualityFormGroup = apply {
        selectDropdownValues("Laatu", listOf(method))
    }

    fun selectElevationMeasurementMethod(method: String): E2EQualityFormGroup = apply {
        selectDropdownValues("Korkeusasema", listOf(method))
    }
}

class E2ELogFormGroup(elementFetch: ElementFetch) : E2EFormGroup(elementFetch) {
    val planTime: String get() = getValueForField("Laadittu")
    fun setPlanTime(month: String, year: String): E2ELogFormGroup = apply {
        selectDropdownValues("Laadittu", listOf(month, year))
    }
}

class E2EConfirmDialog : E2EDialog() {
    fun confirm() {
        clickButtonByText("Tallenna")
    }
}
