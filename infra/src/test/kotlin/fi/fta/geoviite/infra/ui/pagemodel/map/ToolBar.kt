package fi.fta.geoviite.infra.ui.pagemodel.map

import clickWhenClickable
import exists
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETextListItem
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import waitUntilNotVisible
import waitUntilVisible

class E2EToolBar(parentView: E2EViewFragment) : E2EViewFragment(parentView, By.className("tool-bar")) {
    private val searchDropdown: E2EDropdown by lazy {
        childDropdown(By.cssSelector(".tool-bar__left-section .dropdown"))
    }

    val mapLayerMenu: E2EMapLayerPanel by lazy {
        if (!exists(By.className("map-layer-menu"))) {
            clickChild(byQaId("map-layers-button"))
            waitUntilChildVisible(By.className("map-layer-menu"))
        }

        E2EMapLayerPanel(By.className("map-layer-menu"))
    }

    fun search(value: String, clear: Boolean = true): E2EToolBar = apply {
        logger.info("Search for $value")

        if (clear) searchDropdown.clearSearch()
        searchDropdown.search(value)
        waitUntilVisible(By.className("dropdown__loading-indicator"))
        waitUntilNotVisible(By.className("dropdown__loading-indicator"))
    }

    val searchResults: List<E2ETextListItem> get() = searchDropdown.options

    fun selectSearchResult(resultContains: String) = apply {
        logger.info("Select result '$resultContains'")
        searchDropdown.select(resultContains)
    }

    fun goToPreview(): E2EPreviewChangesPage {
        logger.info("Go to preview changes page")
        clickChild(By.xpath(".//button[span[text() = 'Esikatselu']]"))

        waitUntilVisible(byQaId("preview-content"))

        return E2EPreviewChangesPage()
    }

    fun switchToDraft(): E2EToolBar = apply {
        logger.info("Enable draft/edit mode")
        clickChild(By.xpath(".//button[span[text() = 'Luonnostila']]"))
    }

    fun createNewLocationTrack(): E2ELocationTrackEditDialog {
        logger.info("Create new location track")

        clickChild(byQaId("tool-bar.new"))
        clickWhenClickable(byQaId("tool-bar.new-location-track"))
        return E2ELocationTrackEditDialog()
    }
}

class E2EMapLayerPanel(panelBy: By) : E2EViewFragment(panelBy) {
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
        logger.info("Show map layer ${layer.uiText}")
        if (!isSelected(layer)) {
            toggleLayer(layer)
        }
    }

    fun hideLayer(layer: MapLayer): E2EMapLayerPanel = apply {
        logger.info("Hide map layer ${layer.uiText}")
        if (isSelected(layer)) {
            toggleLayer(layer)
        }
    }

    private fun isSelected(mapLayer: MapLayer): Boolean {
        return childElement(
            By.xpath(
                "//label[@class='map-layer-menu__layer-visibility ' " +
                        "and span[text() = '${mapLayer.uiText}']]//input']"
            )
        ).isSelected
    }

    private fun toggleLayer(mapLayer: MapLayer) {
        clickChild(
            By.xpath(
                "//label[@class='map-layer-menu__layer-visibility ' " +
                        "and span[text() = '${mapLayer.uiText}']]/label[@class='switch']"
            )
        )
    }
}
