package fi.fta.geoviite.infra.ui.pagemodel.map

import clickWhenClickable
import exists
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdownListItem
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import java.time.LocalDate
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilInvisible
import waitUntilVisible

class E2EToolBar(parentView: E2EViewFragment) : E2EViewFragment(parentView, By.className("tool-bar")) {
    private val searchDropdown: E2EDropdown by lazy {
        childDropdown(By.cssSelector(".tool-bar__right-section .dropdown"))
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
        waitUntilInvisible(By.className("dropdown__loading-indicator"))
    }

    val searchResults: List<E2EDropdownListItem>
        get() = searchDropdown.options

    fun selectSearchResult(resultContains: String) = apply {
        logger.info("Select result '$resultContains'")
        searchDropdown.selectByName(resultContains)
    }

    fun goToPreview(): E2EPreviewChangesPage {
        logger.info("Go to preview changes page")
        clickChild(byQaId("open-preview-view"))

        waitUntilVisible(byQaId("preview-content"))

        return E2EPreviewChangesPage()
    }

    fun switchToDraft(): E2EToolBar = apply {
        logger.info("Enable draft/edit mode")
        clickChild(byQaId("draft-mode-tab"))
    }

    fun switchToOfficial(): E2EToolBar = apply {
        logger.info("Exit draft/edit mode")
        clickChild(byQaId("current-mode-tab"))
    }

    fun switchToDesign(): E2EToolBar = apply {
        logger.info("Switch to design mode")
        clickChild(byQaId("design-mode-tab"))
    }

    fun createNewLocationTrack(): E2ELocationTrackEditDialog {
        logger.info("Create new location track")

        clickChild(byQaId("tool-bar.new"))
        clickWhenClickable(byQaId("tool-bar.new-location-track"))
        return E2ELocationTrackEditDialog()
    }

    fun workspaceDropdown() = E2EDropdown(byQaId("workspace-dropdown"))

    fun createNewWorkspace(name: String, date: LocalDate = LocalDate.now()) {
        logger.info("Create new workspace")

        E2EDropdown(byQaId("workspace-dropdown")).new()
        val newDialog = E2EWorkspaceEditDialog()

        // Slight hack: Set date before name to make sure datepicker closes and doesn't block the
        // save button
        newDialog.setDate(date)
        newDialog.setName(name)
        newDialog.save()
    }
}

class E2EMapLayerPanel(panelBy: By) : E2EViewFragment(panelBy) {
    enum class MapLayer(val qaId: String) {
        BACKGROUND("background-map-layer"),
        REFERENCE_LINES("reference-line-layer"),
        LOCATION_TRACKS("location-track-layer"),
        HIGHLIGHT_MISSING_VERTICAL_GEOMETRY("missing-vertical-geometry-layer"),
        HIGHLIGHT_MISSING_LINKING("missing-linking-layer"),
        HIGHLIGHT_DUPLICATE_TRACKS("duplicate-tracks-layer"),
        SWITCHES("switch-layer"),
        KM_POSTS("km-post-layer"),
        TRACK_NUMBER_DIAGRAM("track-number-diagram-layer"),
        GEOMETRY_ALIGNMENTS("geometry-alignment-layer"),
        GEOMETRY_SWITCHES("geometry-switch-layer"),
        GEOMETRY_KM_POSTS("geometry-km-post-layer"),
        PLAN_AREA("geometry-area-layer"),
    }

    fun showLayer(layer: MapLayer): E2EMapLayerPanel = apply {
        logger.info("Show map layer $layer")

        if (!isSelected(layer)) {
            toggleLayer(layer)
        }
    }

    fun hideLayer(layer: MapLayer): E2EMapLayerPanel = apply {
        logger.info("Hide map layer $layer")
        if (isSelected(layer)) {
            toggleLayer(layer)
        }
    }

    private fun isSelected(mapLayer: MapLayer) =
        childElement(ByChained(byQaId(mapLayer.qaId), By.tagName("input"))).isSelected

    private fun toggleLayer(mapLayer: MapLayer) = clickChild(byQaId(mapLayer.qaId))
}
