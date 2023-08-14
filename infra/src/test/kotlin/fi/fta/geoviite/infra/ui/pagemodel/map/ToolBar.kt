package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitUntilVisible

class E2EToolBar(parentFetch: ElementFetch) : E2EViewFragment(fetch(parentFetch, By.className("tool-bar"))) {

    //todo GVT-1935
    class SearchResult(private val resultRow: WebElement) {
        val value: String = resultRow.text
        fun select() = resultRow.click()
    }

    private val searchDropdown: E2EDropdown by lazy { E2EDropdown(elementFetch) }

    fun search(value: String, clear: Boolean = true): E2EToolBar = apply {
        if (clear) searchDropdown.clearInput()
        searchDropdown.inputValue(value)
        waitUntilSearchIsNotLoading()
    }

    fun searchResults(): List<SearchResult> {
        // TODO: GVT-1935 These list elements hold a reference to the WebElement, risking staleness. Use ListModel to replace this.
        val searchResults = searchDropdown.options().map { SearchResult(it) }
        logger.info("Search results: ${searchResults.map { it.value }}")
        return searchResults
    }

    fun selectResult(resultContains: String) {
        logger.info("Select result '$resultContains'")
        searchResults().first { it.value.contains(resultContains) }.select()
    }

    fun waitUntilSearchIsNotLoading() {
        waitChildVisible(By.className("dropdown__list"))
        waitChildNotVisible(byQaId("search-box-loading"))
    }

    val mapLayerMenu: E2EMapLayerPanel by lazy {
        logger.info("Open map layers")
        clickChild(byQaId("map-layers-button"))

        waitUntilVisible(By.className("map-layer-menu"))

        //todo gvt-1947: layer menu is in a weird part of dom tree
        E2EMapLayerPanel { getElementWhenVisible(By.className("map-layer-menu")) }
    }

    fun goToPreview(): E2EPreviewChangesPage {
        logger.info("To preview changes page")
        clickChild(By.xpath(".//button[span[text() = 'Esikatselu']]"))

        waitUntilVisible(byQaId("preview-content"))

        return E2EPreviewChangesPage()
    }

    fun switchToDraft(): E2EToolBar = apply {
        logger.info("Enable draft/edit mode")
        clickChild(By.xpath(".//button[span[text() = 'Luonnostila']]"))
    }

}

class E2EMapLayerPanel(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {
    enum class MapLayer(val uiText: String) {
        BACKGROUND("Taustakartta"),
        REFERENCE_LINES("Pituusmittauslinjat"),
        LOCATION_TRACKS("Sijaintairaiteet"),
        HIGHLIGHT_MISSING_VERTICAL_GEOMETRY("Korosta puuttuva pystygeometria"),
        HIGHLIGHT_MISSING_LINKING("Korosta puuttuva linkitys"),
        HIGHLIGHT_DUPLICATE_TRACKS("Korosta duplikaattiraiteet"),
        SWITCHES("Vaihteet"),
        KM_POSTS("Tasakilometripisteet"),
        TRACK_NUMBER_DIAGRAM("Pituusmittausraidekaavio"),

        GEOMETRY_ALIGNMENTS("Suunnitelman raiteet"),
        GEOMETRY_SWITCHES("Suunnitelman vaiheet"),
        GEOMETRY_KM_POSTS("Suunnitelman tasakilometripisteet"),
        PLAN_AREA("Suunnitelman alueet"),
    }

    fun showLayer(layer: MapLayer): E2EMapLayerPanel = apply {
        logger.info("Show layer ${layer.uiText}")
        if (!isSelected(layer)) {
            toggleLayer(layer)
        }
    }

    fun hideLayer(layer: MapLayer): E2EMapLayerPanel = apply {
        logger.info("Hide layer ${layer.uiText}")
        if (isSelected(layer)) {
            toggleLayer(layer)
        }
    }

    private fun isSelected(mapLayer: MapLayer): Boolean {
        return childElement(By.xpath("//label[@class='map-layer-menu__layer-visibility ' and span[text() = '${mapLayer.uiText}']]//input']")).isSelected
    }

    private fun toggleLayer(mapLayer: MapLayer) {
        clickChild(By.xpath("//label[@class='map-layer-menu__layer-visibility ' and span[text() = '${mapLayer.uiText}']]/label[@class='switch']"))
    }
}
