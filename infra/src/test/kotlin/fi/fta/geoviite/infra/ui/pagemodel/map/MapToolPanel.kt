package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.InfoBox
import getElementWhenClickable
import org.openqa.selenium.By
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MapToolPanel {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun selectToolPanelTab(tabName: String) {
        val selectBy = By.xpath("//div[@qa-id='tool-panel-tabs']/button[span[text() = '$tabName']]")
        getElementWhenClickable(selectBy).click()
    }

    fun layoutKmPostGeneral() = infoBox("km-post-infobox", ::LayoutKmPostGeneralInfoBox)

    fun layoutKmPostLocation() = infoBox("layout-km-post-location-infobox", ::LayoutKmPostLocationInfoBox)

    fun trackNumberGeneralInfo() = infoBox("track-number-infobox", ::TrackNumberGeneralInfoBox)

    fun referenceLineLocation() = infoBox("reference-line-location-infobox", ::ReferenceLineLocationInfoBox)

    fun locationTrackGeneralInfo() = infoBox("location-track-infobox", ::LocationTrackGeneralInfoBox)

    fun locationTrackLocation() = infoBox("location-track-location-infobox", ::LocationTrackLocationInfobox)

    fun locationTrackLog() = infoBox("location-track-log-infobox", ::LocationTrackLogInfoBox)

    //re-used old qa-id so don't worry
    fun trackNumberLog() = infoBox("track-number-log-infobox", ::TrackNumberLogInfoBox)

    fun layoutSwitchGeneralInfo() = infoBox("switch-infobox", ::LayoutSwitchGeneralInfoBox)

    fun layoutSwitchStructureGeneralInfo() = infoBox("switch-structure-infobox", ::SwitchStructureGeneralInfoBox)

    fun layoutSwitchLocation() = infoBox("switch-location-infobox", ::SwitchCoordinatesInfoBox)

    fun layoutSwitchAdditionalInfo() = infoBox("switch-additional-infobox", ::LayoutSwitchAdditionalInfoInfoBox)

    fun geometryAlignmentGeneral() = infoBox("geometry-alignment-infobox", ::GeometryAlignmentGeneralInfoBox)

    fun geometryAlignmentLinking() = infoBox("geometry-alignment-linking-infobox", ::GeometryAlignmentLinkingInfoBox)

    fun geometryKmPostLinking() = infoBox("geometry-km-post-linking-infobox", ::GeometryKmPostLinkingInfoBox)

    fun geometrySwitchGeneral() = infoBox("geometry-switch-infobox", ::GeometrySwitchGeneralInfoBox)

    fun geometrySwitchLinking() = infoBox("geometry-switch-linking-infobox", ::GeometrySwitchLinkingInfoBox)

    fun geometryPlanGeneral() = infoBox("geometry-plan-general-infobox", ::GeometryPlanGeneralInfoBox)

    fun geometryPlanQuality() = infoBox("geometry-plan-quality-infobox", ::GeometryPlanQualityInfobox)

    private fun <T : InfoBox> infoBox(qaId: String, creator: (by: By) -> T) =
        creator(By.xpath("//div[@qa-id='$qaId']")).also { b -> b.waitUntilLoaded() }
}
