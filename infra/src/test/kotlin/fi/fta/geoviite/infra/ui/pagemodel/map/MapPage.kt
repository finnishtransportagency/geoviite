package fi.fta.geoviite.infra.ui.pagemodel.map

import browser
import clickElementAtPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutPoint
import fi.fta.geoviite.infra.ui.pagemodel.common.MainNavigationBar
import getElementWhenClickable
import getElementWhenVisible
import javaScriptExecutor
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.interactions.Actions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import waitUntilDoesNotExist
import waitUntilValueIsNot
import withNoImplicitWait
import kotlin.math.roundToInt

class MapPage {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        currentMapScale()
    }

    val toolPanel = MapToolPanel()
    val navigationPanel = MapNavigationPanel()
    val mainNavigation = MainNavigationBar()

    companion object {
        fun finishLoading() {
            withNoImplicitWait { waitUntilDoesNotExist(By.className(".map__loading-spinner")) }
        }
    }

    fun addEndPointDialog(): AddEndPointDialog {
        logger.info("Get add end point popup dialog")
        return AddEndPointDialog()
    }

    fun switchLinkingDialog(): SwitchLinkingDialog {
        logger.info("Get switch linking popup dialog")
        return SwitchLinkingDialog()
    }

    fun karttatasoasetukset(): MapLayerSettingsPanel {
        logger.info("Open map layers")
        getElementWhenVisible(By.xpath("//button[@qa-id='map-layers-button']")).click()
        return MapLayerSettingsPanel(By.xpath("//div[@class='map-layer-menu']"))
    }

    fun esikatselu(): PreviewChangesPage {
        logger.info("To preview changes page")
        getElementWhenVisible(By.xpath(".//span[contains(text(), 'Esikatselu')]")).click()
        return PreviewChangesPage()
    }

    fun luonnostila() {
        logger.info("Enable draft/edit mode")
        getElementWhenVisible(By.xpath(".//span[contains(text(), 'Luonnostila')]")).click()
    }

    fun searchBox(): SearchBox = SearchBox(By.xpath("//div[@qa-id='search-box']"))

    fun scrollMap(xOffset: Int, yOffset: Int) {
        logger.info("Scroll map x=$xOffset y=$yOffset")
        val canvas = getElementWhenVisible(By.cssSelector("div.map"))

        Actions(browser()).dragAndDropBy(canvas, xOffset, yOffset).build().perform()
    }

    fun clickAtCoordinates(point: Point, doubleClick: Boolean = false) {
        clickAtCoordinates(point.x, point.y, doubleClick)
    }

    fun clickAtCoordinates(point: LayoutPoint, doubleClick: Boolean = false) {
        clickAtCoordinates(point.x, point.y, doubleClick)
    }

    fun clickAtCoordinates(xPoint: Double, yPoint: Double, doubleClick: Boolean = false) {
        val pxlCoordinates =
            javaScriptExecutor().executeScript("return map.getPixelFromCoordinate([$xPoint,$yPoint])").toString()
                .replace("[^0-9.,]".toRegex(), "").split(",").map { doubleStr -> doubleStr.toDouble().roundToInt() }

        logger.info("Map coordinates ($xPoint,$yPoint) are at $pxlCoordinates")
        clickAtCoordinates(pixelX = pxlCoordinates[0], pixelY = pxlCoordinates[1], doubleClick)
    }

    fun clickAtCoordinates(pixelX: Int, pixelY: Int, doubleClick: Boolean = false) {
        logger.info("Click map at ($pixelX,$pixelY)")
        val canvas = getElementWhenVisible(By.cssSelector("div.map"))
        clickElementAtPoint(canvas, pixelX, pixelY, doubleClick)
    }

    fun currentMapScale(): String {
        val scale = getElementWhenVisible(By.className("ol-scale-line-inner")).text
        logger.info("Current map scale $scale")
        return scale
    }

    fun zoomInToScale(targetScale: String) {
        logger.info("Zoom in to scale $targetScale")
        if (currentMapScale().contentEquals(targetScale)) return

        while (!currentMapScale().contentEquals(targetScale)) {
            zoomIn()
        }
        finishLoading()
    }

    fun zoomOutToScale(targetScale: String) {
        logger.info("Zoom out to scale $targetScale")
        if (currentMapScale().contentEquals(targetScale)) return

        while (!currentMapScale().contentEquals(targetScale)) {
            zoomOut()
        }
    }

    private fun zoomOut() {
        val currentScale = currentMapScale()
        getElementWhenClickable(By.className("ol-zoom-out")).click()
        try {
            waitUntilValueIsNot(browser().findElement(By.className("ol-scale-line-inner")), currentScale)
        } catch (ex: TimeoutException) {
            logger.warn("Zoom out failed")
        }
    }

    private fun zoomIn() {
        val currentScale = currentMapScale()
        getElementWhenClickable(By.className("ol-zoom-in")).click()
        try {
            waitUntilValueIsNot(browser().findElement(By.className("ol-scale-line-inner")), currentScale)
        } catch (ex: TimeoutException) {
            logger.warn("Zoom in failed")
        }
    }

}
