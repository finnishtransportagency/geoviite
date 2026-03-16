package fi.fta.geoviite.infra.ui.pagemodel.map

import clickWhenClickable
import fi.fta.geoviite.infra.tracklayout.LocationTrackNamingScheme
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EInfoBox
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ERadio
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilExists

class E2EGeometryPlanGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val remarks: String
        get() = getValueForField("geometry-plan-remarks")

    val author: String
        get() = getValueForField("geometry-plan-author")

    val projectName: String
        get() = getValueForField("geometry-plan-project")

    val fileName: String
        get() = getValueForField("geometry-plan-file")

    val planPhase: String
        get() = getValueForField("geometry-plan-phase")

    val decisionPhase: String
        get() = getValueForField("geometry-plan-decision")

    val trackNumber: String
        get() = getValueWhenFieldHasValue("geometry-plan-track-number")

    val startKmNumber: String
        get() = getValueForField("geometry-plan-start-km")

    val endKmNumber: String
        get() = getValueForField("geometry-plan-end-km")
}

class E2EGeometryPlanQualityInfobox(infoboxBy: By) : E2EInfoBox(infoboxBy) {

    val source: String
        get() = getValueForField("geometry-plan-source")

    val planTime: String
        get() = getValueForField("geometry-plan-plan-time")

    val measurementMethod: String
        get() = getValueForField("geometry-plan-measurement-method")

    val coordinateSystem: String
        get() = getValueForField("geometry-plan-coordinate-system")

    val verticalGeometry: String
        get() = getValueForField("geometry-plan-vertical-geometry")

    val cant: String
        get() = getValueForField("geometry-plan-cant")

    val verticalCoordinateSystem: String
        get() = getValueForField("geometry-plan-vertical-coordinate-system")
}

class E2ELayoutKmPostGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String
        get() = getValueForField("km-post-km-number")

    val trackNumber: String
        get() = getValueWhenFieldHasValue("km-post-track-number")

    fun zoomTo(): E2ELayoutKmPostGeneralInfoBox = apply {
        logger.info("Zoom to km post")

        clickButton(byQaId("zoom-to-km-post")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2ELayoutKmPostLocationInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val coordinates: String
        get() = getValueForField("km-post-coordinates")
}

class E2ELocationTrackLocationInfobox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val trackNumber: String
        get() = getValueForField("location-track-track-number")

    val startLocation: String
        get() = getValueForField("location-track-start-track-meter")

    val endLocation: String
        get() = getValueForField("location-track-end-track-meter")

    val trueLength: String
        get() = getValueForField("location-track-true-length")

    val trueLengthDouble: Double?
        get() = Regex("[0-9.]*").find(trueLength)?.value?.toDouble()

    val startCoordinates: String
        get() = getValueForField("location-track-start-coordinates")

    val endCoordinates: String
        get() = getValueForField("location-track-end-coordinates")

    fun waitForStartCoordinatesChange(value: String) =
        waitUntilValueChangesForField("location-track-start-coordinates", value)

    fun waitForEndCoordinatesChange(value: String) =
        waitUntilValueChangesForField("location-track-end-coordinates", value)

    fun startLinking(): E2ELocationTrackLocationInfobox = apply {
        logger.info("Edit start/end point")

        childButton(byQaId("modify-start-or-end")).clickAndWaitToDisappear()
    }

    fun startSplitting(): E2ELocationTrackSplittingInfobox {
        logger.info("Start splitting")
        childButton(byQaId("start-splitting")).clickAndWaitToDisappear()

        return E2ELocationTrackSplittingInfobox()
    }

    fun save(): E2ELocationTrackLocationInfobox = apply {
        logger.info("Save start/end points")

        clickButton(byQaId("save-start-and-end-changes"))
        waitAndClearToast("location-track-endpoints-updated")
    }
}

class E2ELocationTrackSplittingInfobox(infoboxBy: By = byQaId("location-track-splitting-infobox")) :
    E2EInfoBox(infoboxBy) {
    fun setTargetNamingScheme(index:Int, namingScheme: LocationTrackNamingScheme) = apply {
        val targetTrackNamingSchemeDropdown = childDropdown(By.xpath("(//*[@qa-id='split-target-track-naming-scheme'])[${index + 1}]"))
        targetTrackNamingSchemeDropdown.selectByEnum(namingScheme)
    }
    fun setTargetTrackNameFreeText(index: Int, trackName: String) = apply {
        val targetTrackNameInput = childTextInput(By.xpath("(//*[@qa-id='split-target-track-name-free-text'])[${index + 1}]"))

        targetTrackNameInput.replaceValue(trackName)
    }

    fun setTargetTrackDescription(index: Int, description: String) = apply {
        val targetTrackNameInput =
            childTextInput(By.xpath("(//*[@qa-id='split-target-track-description'])[${index + 1}]"))

        targetTrackNameInput.replaceValue(description)
    }

    fun cancelSplitting() {
        clickWhenClickable(byQaId("cancel-split"))

        val cancelDialog = E2EDialog()

        cancelDialog.clickWarningButton()
        cancelDialog.waitUntilInvisible()
    }

    fun confirmSplit() {
        clickWhenClickable(byQaId("confirm-split"))
        waitAndClearToast("splitting-success")
    }

    fun waitUntilTargetTrackInputExists(index: Int) {
        waitUntilExists(By.xpath("(//*[@qa-id='split-target-input-form'])[${index + 1}]"))
    }
}

class E2ELocationTrackGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val oid: String
        get() = getValueForField("location-track-oid")

    val name: String
        get() = getValueForField("location-track-name")

    val state: String
        get() = getEnumValueForField("location-track-state")

    val type: String
        get() = getEnumValueForField("location-track-type")

    val description: String
        get() = getValueWhenFieldHasValue("location-track-description")

    val trackNumber: String
        get() = getValueWhenFieldHasValue("location-track-track-number")

    fun waitUntilDescriptionChanges(value: String) = waitUntilValueChangesForField("location-track-description", value)

    fun edit(): E2ELocationTrackEditDialog {
        logger.info("Enable location track editing")

        editFields()
        return E2ELocationTrackEditDialog()
    }

    fun zoomTo(): E2ELocationTrackGeneralInfoBox = apply {
        logger.info("Zoom to location track")

        clickButton(byQaId("zoom-to-location-track")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2ELocationTrackVerticalGeometryInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    fun toggleVerticalGeometryDiagram() =
        clickButton(byQaId("tool-panel.location-track.vertical-geometry.diagram-visibility"))
}

class E2ELocationTrackLogInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val created: String
        get() = getValueForField("location-track-created-date")

    val changed: String
        get() = getValueForField("location-track-changed-date")
}

class E2ETrackNumberGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val oid: String
        get() = getValueForField("track-number-oid")

    val name: String
        get() = getValueForField("track-number-name")

    val state: String
        get() = getValueForField("track-number-state")

    val description: String
        get() = getValueForField("track-number-description")

    fun edit(): E2ETrackNumberEditDialog {
        logger.info("Enable track number editing")

        editFields()
        return E2ETrackNumberEditDialog()
    }
}

class E2ETrackNumberLocationInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val startLocation: String
        get() = getValueForField("track-number-start-track-meter")

    val endLocation: String
        get() = getValueForField("track-number-end-track-meter")

    val trueLength: String
        get() = getValueForField("track-number-true-length")

    val startCoordinates: String
        get() = getValueForField("track-number-start-coordinates")

    val endCoordinates: String
        get() = getValueForField("track-number-end-coordinates")

    fun zoomTo(): E2ETrackNumberLocationInfoBox = apply {
        logger.info("Zoom to track number")

        clickButton(byQaId("zoom-to-track-number")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2ETrackNumberLogInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val created: String
        get() = getValueForField("track-number-created-date")

    val changed: String
        get() = getValueForField("track-number-changed-date")
}

class E2ELayoutSwitchGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String
        get() = getValueForField("switch-name")

    val oid: String
        get() = getValueForField("switch-oid")

    val category: String
        get() = getValueForField("switch-state-category")

    fun edit(): E2ELayoutSwitchEditDialog {
        logger.info("Enable switch editing")

        editFields()
        return E2ELayoutSwitchEditDialog()
    }

    fun zoomTo(): E2ELayoutSwitchGeneralInfoBox = apply {
        logger.info("Zoom to switch")

        clickButton(byQaId("zoom-to-switch")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2ELayoutSwitchAdditionalInfoInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val owner: String
        get() = getValueForField("switch-owner")
}

class E2ESwitchStructureGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val type: String
        get() = childText(By.cssSelector("p"))

    val hand: String
        get() = getValueForField("switch-hand")

    val trap: String
        get() = getValueForField("switch-trap-point")
}

class E2ESwitchCoordinatesInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    data class SwitchLineAndTrack(val switchLine: String, val switchTrack: String)

    val coordinates: String
        get() = getValueForField("switch-coordinates")

    val jointAlignments: List<SwitchLineAndTrack>
        get() {
            val switchLines = childTexts(By.cssSelector("dt.switch-joint-infobox__joint-alignments-title"))
            val switchTracks = childTexts(By.cssSelector("dd.switch-joint-infobox__location-tracks div span"))
            return switchLines.mapIndexed { index, line -> SwitchLineAndTrack(line, switchTracks[index]) }
        }

    fun jointAlignment(line: String) = jointAlignments.first { it.switchLine == line }
}

class E2EGeometryAlignmentGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String
        get() = getValueForField("geometry-alignment-name")

    val trackNumber: String
        get() = getValueForField("geometry-alignment-track-number")

    fun zoomTo() = apply {
        logger.info("Zoom to geometry alignment")

        clickButton(byQaId("zoom-to-geometry-alignment")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

abstract class E2ELinkingInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    abstract val linked: String

    abstract fun initiateLinking(): E2ELinkingInfoBox

    abstract fun link(): E2ELinkingInfoBox

    abstract fun linkTo(name: String): E2ELinkingInfoBox
}

class E2EGeometryKmPostLinkingInfoBox(infoboxBy: By) : E2ELinkingInfoBox(infoboxBy) {
    override val linked: String
        get() = childText(ByChained(byQaId("geometry-km-post-linked"), By.className("infobox__field-value")))

    override fun linkTo(name: String): E2EGeometryKmPostLinkingInfoBox = apply {
        logger.info("Link km post to $name")
        clickChild(
            By.xpath("//li[@class='geometry-km-post-linking-infobox__layout-km-post' and //span[text() = '$name']]")
        )
    }

    fun createNewLayoutKmPost(): E2EKmPostEditDialog {
        logger.info("Create new layout km post")

        clickChild(By.cssSelector("div.geometry-km-post-linking-infobox__search button"))
        return E2EKmPostEditDialog()
    }

    val layoutKmPosts: List<String>
        get() = childTexts(By.xpath(".//li[@class='geometry-km-post-linking-infobox__layout-km-post']"))

    override fun initiateLinking(): E2ELinkingInfoBox = apply {
        logger.info("Initiate linking")

        childButton(byQaId("start-geometry-km-post-linking")).clickAndWaitToDisappear()
    }

    override fun link(): E2ELinkingInfoBox = apply {
        logger.info("Link geometry km post")

        childButton(byQaId("link-geometry-km-post")).clickAndWaitToDisappear()
    }
}

class E2EGeometryAlignmentLinkingInfoBox(infoboxBy: By) : E2ELinkingInfoBox(infoboxBy) {

    private val alignmentTypeRadio: E2ERadio by lazy {
        childRadio(By.className("geometry-alignment-infobox__radio-buttons"))
    }

    fun selectLocationTrackLinking() {
        logger.info("Select location track linking")

        alignmentTypeRadio.choose("location-track-linking")
    }

    fun selectReferenceLineLinking() {
        logger.info("Select reference line linking")

        alignmentTypeRadio.choose("reference-line-linking")
    }

    override val linked: String
        get() = childText(ByChained(byQaId("geometry-alignment-linked"), By.className("infobox__field-value")))

    val linkedReferenceLines: List<String>
        get() =
            childTexts(ByChained(byQaId("geometry-alignment-linked-reference-lines"), By.className("alignment-badge")))

    override fun linkTo(name: String): E2EGeometryAlignmentLinkingInfoBox = apply {
        logger.info("Link alignment to $name")
        clickChild(By.xpath("//li[@class='geometry-alignment-infobox__alignment' and div/span[text() = '$name']]"))
    }

    override fun initiateLinking(): E2ELinkingInfoBox = apply {
        logger.info("Initiate linking")

        childButton(byQaId("start-alignment-linking")).clickAndWaitToDisappear()
    }

    override fun link(): E2ELinkingInfoBox = apply {
        logger.info("Link geometry aligment")

        childButton(byQaId("link-geometry-alignment")).clickAndWaitToDisappear()
    }

    fun createNewLocationTrack(): E2ELocationTrackEditDialog {
        logger.info("Create new location track")
        clickChild(byQaId("create-location-track-button"))

        return E2ELocationTrackEditDialog()
    }

    fun lock(): E2EGeometryAlignmentLinkingInfoBox = apply {
        logger.info("Lock location track selection")
        childButton(byQaId("lock-alignment")).clickAndWaitToDisappear()
    }
}

class E2EGeometrySwitchGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String
        get() = getValueForField("geometry-switch-name")

    val hand: String
        get() = getValueForField("geometry-switch-hand")

    fun zoomTo(): E2EGeometrySwitchGeneralInfoBox = apply {
        logger.info("Zoom to geometry switch")

        clickButton(byQaId("zoom-to-geometry-switch")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2EGeometrySwitchLinkingInfoBox(infoboxBy: By) : E2ELinkingInfoBox(infoboxBy) {

    override val linked: String
        get() = childText(ByChained(byQaId("geometry-switch-linked"), By.className("infobox__field-value")))

    fun createNewLayoutSwitch(): E2ELayoutSwitchEditDialog {
        logger.info("Create new layout switch")
        clickChild(By.cssSelector("div.geometry-switch-infobox__search-container button"))

        return E2ELayoutSwitchEditDialog()
    }

    override fun linkTo(name: String): E2EGeometrySwitchLinkingInfoBox = apply {
        logger.info("Link switch to $name")

        clickChild(By.xpath("//li[@class='geometry-switch-infobox__switch' and //span[text() = '$name']]"))
    }

    override fun initiateLinking(): E2ELinkingInfoBox = apply {
        logger.info("Initiate linking")

        childButton(byQaId("start-geometry-switch-linking")).clickAndWaitToDisappear()
    }

    override fun link(): E2ELinkingInfoBox = apply {
        logger.info("Link geometry switch")

        childButton(byQaId("link-geometry-switch")).clickAndWaitToDisappear()
    }
}

class E2EOperationalPointGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val oid: String
        get() = getValueForField("operational-point-oid")

    val name: String
        get() = getValueForField("operational-point-name")

    val abbreviation: String
        get() = getValueForField("operational-point-abbreviation")
}
