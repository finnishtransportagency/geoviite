package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2EInfraModelForm : E2EViewFragment(By.className("infra-model-upload__form-column")) {
    fun saveAsNew() {
        logger.info("Save new infra model")

        save(true)
        waitAndClearToast("infra-model.upload.success")
    }

    fun save(expectConfirm: Boolean = false) {
        logger.info("Save infra model")

        clickChild(byQaId("infra-model-save-button"))
        if (expectConfirm) confirmSaving()
    }

    val metaFormGroup: E2EMetaFormGroup by lazy { childComponent(byQaId("im-form-project"), ::E2EMetaFormGroup) }

    val locationFormGroup: E2ELocationFormGroup by lazy {
        childComponent(byQaId("im-form-location"), ::E2ELocationFormGroup)
    }

    val qualityFormGroup: E2EQualityFormGroup by lazy {
        childComponent(byQaId("im-form-phase-quality"), ::E2EQualityFormGroup)
    }

    val logFormGroup: E2ELogFormGroup by lazy { childComponent(byQaId("im-form-log"), ::E2ELogFormGroup) }

    private fun confirmSaving() {
        // Confirm saving if confirmation dialog appears
        E2EConfirmDialog().confirm()
    }
}

class E2EMetaFormGroup(formBy: By) : E2EFormGroup(formBy) {

    private val projectField: E2EFormGroupField = formGroupField("project-im-field")
    private val authorField: E2EFormGroupField = formGroupField("author-im-field")

    val project: String
        get() = projectField.value

    val author: String
        get() = authorField.value

    fun selectNewProject(newProject: String): E2EMetaFormGroup = apply {
        logger.info("Select new project $newProject")

        projectField.createAndSelectNewValue(listOf(newProject), "new-project-created").waitUntilFieldIs(newProject)
    }

    fun selectNewAuthor(newAuthor: String): E2EMetaFormGroup = apply {
        logger.info("Select new author $newAuthor")

        authorField.createAndSelectNewValue(listOf(newAuthor), "new-author-created").waitUntilFieldIs(newAuthor)
    }
}

class E2ELocationFormGroup(formBy: By) : E2EFormGroup(formBy) {

    private val trackNumberField: E2EFormGroupField = formGroupField("track-number-im-field")
    private val kmNumberField: E2EFormGroupField = formGroupField("km-interval-im-field")
    private val coordinateSystemField: E2EFormGroupField = formGroupField("coordinate-system-im-field")
    private val verticalCoordinateSystemField: E2EFormGroupField = formGroupField("vertical-coordinate-system-im-field")

    val trackNumber: String
        get() = trackNumberField.value

    val kmNumberRange: String
        get() = kmNumberField.value

    val coordinateSystem: String
        get() = coordinateSystemField.value

    val verticalCoordinateSystem: String
        get() = verticalCoordinateSystemField.value

    fun selectExistingTrackNumber(trackNumber: String): E2ELocationFormGroup = apply {
        logger.info("Select track number $trackNumber")

        trackNumberField.selectValue(trackNumber)
    }

    fun selectManualTrackNumber(trackNumber: String): E2ELocationFormGroup = apply {
        logger.info("Selecting manual track number $trackNumber")

        trackNumberField
            .createAndSelectNewValue(listOf(trackNumber), "im-form.track-number-manually-set")
            .waitUntilFieldIs(trackNumber)
    }

    fun selectVerticalCoordinateSystem(coordinateSystem: String): E2ELocationFormGroup = apply {
        logger.info("Select vertical coordinate system $coordinateSystem")

        verticalCoordinateSystemField.selectValue(coordinateSystem)
    }

    fun selectCoordinateSystem(coordinateSystem: String): E2ELocationFormGroup = apply {
        logger.info("Select coordinate system $coordinateSystem")

        coordinateSystemField.selectValue(coordinateSystem)
    }
}

class E2EQualityFormGroup(formBy: By) : E2EFormGroup(formBy) {

    private val planPhaseField: E2EFormGroupField = formGroupField("plan-phase-im-field")
    private val decisionPhaseField: E2EFormGroupField = formGroupField("decision-phase-im-field")
    private val measurementMethodField: E2EFormGroupField = formGroupField("measurement-method-im-field")
    private val elevationMeasurementMethodField: E2EFormGroupField =
        formGroupField("elevation-measurement-method-im-field")

    val planPhase: String
        get() = planPhaseField.value

    val decisionPhase: String
        get() = decisionPhaseField.value

    val measurementMethod: String
        get() = measurementMethodField.value

    val elevationMeasurementMethod: String
        get() = elevationMeasurementMethodField.value

    fun selectPlanPhase(phase: String): E2EQualityFormGroup = apply {
        logger.info("Select plan phase $phase")

        planPhaseField.selectValue(phase)
    }

    fun selectDecisionPhase(decision: String): E2EQualityFormGroup = apply {
        logger.info("Select decision phase $decision")

        decisionPhaseField.selectValue(decision)
    }

    fun selectMeasurementMethod(method: String): E2EQualityFormGroup = apply {
        logger.info("Select measurement method $method")

        measurementMethodField.selectValue(method)
    }

    fun selectElevationMeasurementMethod(method: String): E2EQualityFormGroup = apply {
        logger.info("Select elevation measurement method $method")

        elevationMeasurementMethodField.selectValue(method)
    }
}

class E2ELogFormGroup(formBy: By) : E2EFormGroup(formBy) {

    private val planTimeField: E2EFormGroupField = formGroupField("plan-time-im-field")

    val planTime: String
        get() = planTimeField.value

    fun setPlanTime(month: String, year: String): E2ELogFormGroup = apply {
        logger.info("Select plan time $month $year")

        planTimeField.selectValues(listOf(month, year))
    }
}
