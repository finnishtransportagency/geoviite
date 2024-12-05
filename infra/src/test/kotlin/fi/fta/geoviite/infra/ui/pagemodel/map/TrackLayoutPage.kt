package fi.fta.geoviite.infra.ui.pagemodel.map

import clickElementAtPoint
import exists
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialogWithTextField
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.browser
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.javaScriptExecutor
import getElementIfExists
import getElementWhenExists
import java.time.Instant
import kotlin.math.roundToInt
import org.openqa.selenium.By
import org.openqa.selenium.interactions.Actions
import tryWait
import waitUntilExists
import waitUntilNotExist

class E2ETrackLayoutPage : E2EViewFragment(byQaId("track-layout-content")) {

    enum class MapScale(val value: String) {
        MM_5("5 mm"),
        MM_10("10 mm"),
        MM_20("20 mm"),
        MM_50("50 mm"),
        MM_100("100 mm"),
        MM_200("200 mm"),
        MM_500("500 mm"),
        MM_1000("1000 mm"),
        M_2("2 m"),
        M_5("5 m"),
        M_10("10 m"),
        M_20("20 m"),
        M_50("50 m"),
        M_100("100 m"),
        M_200("200 m"),
        M_500("500 m"),
        M_1000("1000 m"),
        KM_2("2 km"),
        KM_5("5 km"),
        KM_10("10 km"),
        KM_20("20 km"),
        KM_50("50 km"),
        KM_100("100 km"),
        KM_200("200 km"),
        KM_500("500 km"),
    }

    init {
        mapScale
    }

    val toolPanel: E2EToolPanel by lazy { E2EToolPanel(this) }
    val selectionPanel: E2ESelectionPanel by lazy { E2ESelectionPanel(this) }
    val toolBar: E2EToolBar by lazy { E2EToolBar(this) }
    val verticalGeometryDiagram: E2EVerticalGeometryDiagram by lazy { E2EVerticalGeometryDiagram(this) }

    private val resolution: Double
        get() = childElement(By.className("map__ol-map")).getAttribute("qa-resolution").toDouble()

    val switchToDraftModeButton = getElementIfExists(byQaId("draft-mode-tab"))
    val switchToDesignModeButton = getElementIfExists(byQaId("design-mode-tab"))

    val mapScale: MapScale
        get() =
            tryWait({
                val scale = childText(By.className("ol-scale-line-inner"))
                MapScale.entries.firstOrNull { it.value == scale }
            }) {
                "Invalid map scale"
            }

    companion object {
        fun finishLoading() {
            waitUntilNotExist(byQaId("map-loading-spinner"))
        }
    }

    fun scrollMap(xOffset: Int, yOffset: Int): E2ETrackLayoutPage = apply {
        logger.info("Scroll map x=$xOffset y=$yOffset")
        val canvas = childElement(By.cssSelector("div.map"))

        Actions(browser()).dragAndDropBy(canvas, xOffset, yOffset).build().perform()
        finishLoading()
    }

    fun clickAtCoordinates(point: IPoint, doubleClick: Boolean = false): E2ETrackLayoutPage = apply {
        finishLoading()
        clickAtCoordinates(point.x, point.y, doubleClick)
    }

    fun clickAtCoordinates(xPoint: Double, yPoint: Double, doubleClick: Boolean = false): E2ETrackLayoutPage = apply {
        finishLoading()
        val pxlCoordinates =
            javaScriptExecutor()
                .executeScript("return map.getPixelFromCoordinate([$xPoint,$yPoint])")
                .toString()
                .replace("[^0-9.,]".toRegex(), "")
                .split(",")
                .map { doubleStr -> doubleStr.toDouble().roundToInt() }

        logger.info("Map coordinates ($xPoint,$yPoint) are at $pxlCoordinates")
        clickAtCoordinates(pixelX = pxlCoordinates[0], pixelY = pxlCoordinates[1], doubleClick)
    }

    fun clickAtCoordinates(pixelX: Int, pixelY: Int, doubleClick: Boolean = false): E2ETrackLayoutPage = apply {
        finishLoading()
        logger.info("Click map at ($pixelX,$pixelY)")
        val canvas = childElement(By.cssSelector("div.map"))
        clickElementAtPoint(canvas, pixelX, pixelY, doubleClick)
    }

    fun switchToDraftMode(): E2ETrackLayoutPage = apply {
        logger.info("Switch to draft")
        toolBar.switchToDraft()
    }

    fun switchToOfficialMode(): E2ETrackLayoutPage = apply {
        logger.info("Switch to official")
        toolBar.switchToOfficial()
    }

    fun switchToDesignMode(): E2ETrackLayoutPage = apply {
        logger.info("Switch to design")
        toolBar.switchToDesign()
    }

    fun goToPreview() = toolBar.goToPreview()

    fun zoomToScale(targetScale: MapScale): E2ETrackLayoutPage = apply {
        logger.info("Zoom map to scale $targetScale")

        var oldResolution = resolution
        while (targetScale != mapScale) {
            if (targetScale.ordinal > mapScale.ordinal) zoomOut(oldResolution)
            if (targetScale.ordinal < mapScale.ordinal) zoomIn(oldResolution)
            oldResolution = resolution
        }

        finishLoading()
    }

    fun addDesign(designName: String = "design-${Instant.now()}") {
        val workspaceSelectionDropdownQaId = byQaId("workspace-selection")

        if (!exists(workspaceSelectionDropdownQaId)) {
            clickChild(byQaId("workspace-selection-dropdown-toggle"))
        }

        waitUntilExists(workspaceSelectionDropdownQaId)
        E2EDropdown(workspaceSelectionDropdownQaId).new()

        E2EDialogWithTextField()
            .inputValues(listOf(designName))
            .selectInput(byQaId("workspace-dialog-date"))
            .also { getElementWhenExists(By.xpath("//*[contains(@class, 'react-datepicker__day--001')]")).click() }
            .clickPrimaryButton()
    }

    fun removeActiveDesign() {
        clickChild(byQaId("workspace-delete-button"))
        E2EDialog().clickPrimaryWarningButton()
    }

    private fun zoomOut(oldResolution: Double) {
        clickChild(By.className("ol-zoom-out"))
        waitUntilResolutionChanges(oldResolution)
    }

    private fun zoomIn(oldResolution: Double) {
        clickChild(By.className("ol-zoom-in"))
        waitUntilResolutionChanges(oldResolution)
    }

    private fun waitUntilResolutionChanges(oldResolution: Double) {
        tryWait({ resolution != oldResolution }) { "Map scale did not change from $oldResolution" }
    }
}
