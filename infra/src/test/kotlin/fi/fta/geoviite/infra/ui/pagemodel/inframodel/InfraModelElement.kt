package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.localDateFromString
import fi.fta.geoviite.infra.ui.util.localDateTimeFromString
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.time.LocalDateTime


class E2EInfraModelTable(tableBy: By) : E2ETable<E2EInfraModelTableRow>(
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
) {

    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        projectName = getColumnContent("im-form.name-header", columns, headers),
        fileName = getColumnContent("im-form.file-name-header", columns, headers),
        trackNumber = getColumnContent("im-form.track-number-header", columns, headers),
        startKmNumber = getColumnContent("im-form.km-start-header", columns, headers),
        endKmNumber = getColumnContent("im-form.km-end-header", columns, headers),
        planPhase = getColumnContent("im-form.plan-phase-header", columns, headers),
        decisionPhase = getColumnContent("im-form.decision-phase-header", columns, headers),
        planTime = localDateFromString(getColumnContent("im-form.created-at-header", columns, headers)),
        created = localDateTimeFromString(getColumnContent("im-form.uploaded-at-header", columns, headers)),
        linked = getColumnContent("im-form.linked-at-header", columns, headers)
            .takeIf(String::isNotBlank)
            ?.let(::localDateFromString),
    )
}

class E2EMetaFormGroup(formBy: By) : E2EFormGroup(formBy) {
    val projectName: String get() = getValueForField("Projektin nimi")
    val author: String get() = getValueForField("Suunnitteluyritys")

    fun selectNewProject(newProject: String): E2EMetaFormGroup = apply {
        logger.info("Select new project $newProject")

        selectNewDropdownValue("Projektin nimi", listOf(newProject))
        waitAndClearToast("new-project-created")
    }

    fun selectNewAuthor(newAuthor: String): E2EMetaFormGroup = apply {
        logger.info("Select new author $newAuthor")

        selectNewDropdownValue("Suunnitteluyritys", listOf(newAuthor))
        waitAndClearToast("new-author-created")
    }
}

class E2ELocationFormGroup(formBy: By) : E2EFormGroup(formBy) {
    val trackNumber: String get() = getValueForField("Ratanumero")
    val kmNumberRange: String get() = getValueForField("Ratakilometriväli")
    val coordinateSystem: String get() = getValueForField("Koordinaattijärjestelmä")
    val verticalCoordinateSystem: String get() = getValueForField("Korkeusjärjestelmä")

    fun selectTrackNumber(trackNumber: String): E2ELocationFormGroup = apply {
        logger.info("Select track number $trackNumber")

        selectDropdownValues("Ratanumero", listOf(trackNumber))
        clickEditIcon("Ratanumero")
    }

    fun selectNewTrackNumber(trackNumber: String, description: String): E2ELocationFormGroup = apply {
        logger.info("Select new track number $trackNumber with description $description")

        selectNewDropdownValue("Ratanumero", listOf(trackNumber, description))
        waitAndClearToast("track-number-edit.result.succeeded")
        clickEditIcon("Ratanumero")
    }

    fun selectVerticalCoordinateSystem(coordinateSystem: String): E2ELocationFormGroup = apply {
        logger.info("Select vertical coordinate system $coordinateSystem")

        selectDropdownValues("Korkeusjärjestelmä", listOf(coordinateSystem))
    }

    fun selectCoordinateSystem(coordinateSystem: String): E2ELocationFormGroup = apply {
        logger.info("Select coordinate system $coordinateSystem")

        selectDropdownValues("Koordinaattijärjestelmä", listOf(coordinateSystem))
    }
}

class E2EQualityFormGroup(formBy: By) : E2EFormGroup(formBy) {
    val planPhase: String get() = getValueForField("Suunnitteluvaihe")
    val decisionPhase: String get() = getValueForField("Vaiheen tarkennus")
    val measurementMethod: String get() = getValueForField("Laatu")
    val elevationMeasurementMethod: String get() = getValueForField("Korkeusasema")


    fun selectPlanPhase(phase: String): E2EQualityFormGroup = apply {
        logger.info("Select plan phase $phase")

        selectDropdownValues("Suunnitteluvaihe", listOf(phase))
    }

    fun selectDecisionPhase(decision: String): E2EQualityFormGroup = apply {
        logger.info("Select decision phase $decision")

        selectDropdownValues("Vaiheen tarkennus", listOf(decision))
    }

    fun selectMeasurementMethod(method: String): E2EQualityFormGroup = apply {
        logger.info("Select measurement method $method")

        selectDropdownValues("Laatu", listOf(method))
    }

    fun selectElevationMeasurementMethod(method: String): E2EQualityFormGroup = apply {
        logger.info("Select elevation measurement method $method")

        selectDropdownValues("Korkeusasema", listOf(method))
    }
}

class E2ELogFormGroup(formBy: By) : E2EFormGroup(formBy) {

    val planTime: String get() = getValueForField("Laadittu")

    fun setPlanTime(month: String, year: String): E2ELogFormGroup = apply {
        logger.info("Select plan time $month $year")

        selectDropdownValues("Laadittu", listOf(month, year))
    }
}

class E2EConfirmDialog : E2EDialog() {
    fun confirm() {
        logger.info("Confirm")

        waitUntilClosed {
            clickChildByText("Tallenna")
        }
    }
}
