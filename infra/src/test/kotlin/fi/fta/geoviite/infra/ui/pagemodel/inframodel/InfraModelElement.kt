package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EFormGroup
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContentByAttr
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateFromString
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateTimeFromString
import fi.fta.geoviite.infra.ui.util.ElementFetch
import getChildElements
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitAndAssertToaster
import java.time.LocalDateTime


class E2EInfraModelTable(tableFetch: ElementFetch) : E2ETable<E2EInfraModelTableRow>(
    tableFetch = tableFetch,
    headersBy = By.cssSelector("thead th"),
    rowsBy = By.cssSelector("tbody tr"),
) {
    override fun getRowContent(row: WebElement): E2EInfraModelTableRow {
        return E2EInfraModelTableRow(row.getChildElements(By.tagName("td")), headerElements)
    }

    fun getRow(projectName: String? = null, fileName: String? = null): E2EInfraModelTableRow? {
        return rows.find { it.projectName == projectName || it.fileName == fileName }
    }

    fun sortBy(colName: String): E2EInfraModelTable = apply {
        logger.info("Sort by column $colName")
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
) {

    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        projectName = getColumnContentByAttr("im-form.name-header", columns, headers),
        fileName = getColumnContentByAttr("im-form.file-name-header", columns, headers),
        trackNumber = getColumnContentByAttr("im-form.track-number-header", columns, headers),
        startKmNumber = getColumnContentByAttr("im-form.km-start-header", columns, headers),
        endKmNumber = getColumnContentByAttr("im-form.km-end-header", columns, headers),
        planPhase = getColumnContentByAttr("im-form.plan-phase-header", columns, headers),
        decisionPhase = getColumnContentByAttr("im-form.decision-phase-header", columns, headers),
        planTime = localDateFromString(getColumnContentByAttr("im-form.created-at-header", columns, headers)),
        created = localDateTimeFromString(getColumnContentByAttr("im-form.uploaded-at-header", columns, headers)),
        linked = getColumnContentByAttr("im-form.linked-at-header", columns, headers).let { v ->
            if (v.isNotBlank()) localDateTimeFromString(v)
            else null
        },
    )
}

class E2EMetaFormGroup(elementFetch: ElementFetch) : E2EFormGroup(elementFetch) {
    val projectName: String get() = getValueForField("Projektin nimi")
    val author: String get() = getValueForField("Suunnitteluyritys")

    fun selectNewProject(newProject: String): E2EMetaFormGroup = apply {
        selectNewDropdownValue("Projektin nimi", listOf(newProject))
        waitAndAssertToaster("Uusi projekti luotu")
    }

    fun selectNewAuthor(newAuthor: String): E2EMetaFormGroup = apply {
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
    fun confirm() = waitUntilClosed {
        clickButtonByText("Tallenna")
    }
}
