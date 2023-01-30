package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.openqa.selenium.By
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MapToolPanel {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun selectToolPanelTab(tabName: String) {
        val selectBy = By.xpath("//div[@qa-id='tool-panel-tabs']/button[span[text() = '$tabName']]")
        PageModel.getElementWhenClickable(selectBy).click()
    }

    fun layoutKmPostGeneral(): LayoutKmPostGeneralInfoBox =
        LayoutKmPostGeneralInfoBox(By.xpath("//div[@qa-id='km-post-infobox']"))

    fun layoutKmPostLocation(): LayoutKmPostLocationInfoBox =
        LayoutKmPostLocationInfoBox(By.xpath("//div[@qa-id='layout-km-post-location-infobox']"))

    fun trackNumberGeneralInfo(): TrackNumberGeneralInfoBox =
        TrackNumberGeneralInfoBox(By.xpath("//div[@qa-id='track-number-infobox']"))

    fun referenceLineLocation(): ReferenceLineLocationInfoBox =
        ReferenceLineLocationInfoBox(By.xpath("//div[@qa-id='reference-line-location-infobox']"))

    fun locationTrackGeneralInfo(): LocationTrackGeneralInfoBox =
        LocationTrackGeneralInfoBox(By.xpath("//div[@qa-id='location-track-infobox']"))

    fun locationTrackLocation(): LocationTrackLocationInfobox =
        LocationTrackLocationInfobox(By.xpath("//div[@qa-id='location-track-location-infobox']"))

    fun locationTrackLog(): LocationTrackLogInfoBox =
        LocationTrackLogInfoBox(By.xpath("//div[@qa-id='location-track-log-infobox']"))

    //re-used old qa-id so don't worry
    fun trackNumberLog(): TrackNumberLogInfoBox =
        TrackNumberLogInfoBox(By.xpath("//div[@qa-id='track-number-log-infobox']"))

    fun layoutSwitchGeneralInfo(): LayoutSwitchGeneralInfoBox =
        LayoutSwitchGeneralInfoBox(By.xpath("//div[@qa-id='switch-infobox']"))

    fun layoutSwitchStructureGeneralInfo(): SwitchStructureGeneralInfoBox =
        SwitchStructureGeneralInfoBox(By.xpath("//div[@qa-id='switch-structure-infobox']"))

    fun layoutSwitchLocation(): SwitchCoordinatesInfoBox =
        SwitchCoordinatesInfoBox(By.xpath("//div[@qa-id='switch-location-infobox']"))

    fun layoutSwitchAdditionalInfo(): LayoutSwitchAdditionalInfoInfoBox =
        LayoutSwitchAdditionalInfoInfoBox(By.xpath("//div[@qa-id='switch-additional-infobox']"))

    fun geometryAlignmentGeneral(): GeometryAlignmentGeneralInfoBox =
        GeometryAlignmentGeneralInfoBox(By.xpath("//div[@qa-id='geometry-alignment-infobox']"))

    fun geometryAlignmentLinking(): GeometryAlignmentLinkingInfoBox =
        GeometryAlignmentLinkingInfoBox(By.xpath("//div[@qa-id='geometry-alignment-linking-infobox']"))

    fun geometryKmPostLinking(): GeometryKmPostLinkingInfoBox =
        GeometryKmPostLinkingInfoBox(By.xpath("//div[@qa-id='geometry-km-post-linking-infobox']"))

    fun geometrySwitchGeneral(): GeometrySwitchGeneralInfoBox =
        GeometrySwitchGeneralInfoBox(By.xpath("//div[@qa-id='geometry-switch-infobox']"))

    fun geometrySwitchLinking(): GeometrySwitchLinkingInfoBox =
        GeometrySwitchLinkingInfoBox(By.xpath("//div[@qa-id='geometry-switch-linking-infobox']"))

    fun geometryPlanGeneral(): GeometryPlanGeneralInfoBox =
        GeometryPlanGeneralInfoBox(By.xpath("//div[@qa-id='geometry-plan-general-infobox']"))

    fun geometryPlanQuality(): GeometryPlanQualityInfobox =
        GeometryPlanQualityInfobox(By.xpath("//div[@qa-id='geometry-plan-quality-infobox']"))

}
