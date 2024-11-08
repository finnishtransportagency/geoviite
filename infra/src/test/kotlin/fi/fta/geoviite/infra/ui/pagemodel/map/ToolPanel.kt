package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EInfoBox
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2EToolPanel(parentView: E2EViewFragment) : E2EViewFragment(parentView, By.className("tool-panel")) {

    fun selectToolPanelTab(tabName: String) {
        logger.info("Select tab $tabName")

        clickChild(By.xpath("//div[@qa-id='tool-panel-tabs']/a[text() = '$tabName']"))
    }

    val layoutKmPostGeneral: E2ELayoutKmPostGeneralInfoBox by lazy {
        infoBox("km-post-infobox", ::E2ELayoutKmPostGeneralInfoBox)
    }

    val layoutKmPostLocation: E2ELayoutKmPostLocationInfoBox by lazy {
        infoBox("layout-km-post-location-infobox", ::E2ELayoutKmPostLocationInfoBox)
    }

    val trackNumberGeneralInfo: E2ETrackNumberGeneralInfoBox by lazy {
        infoBox("track-number-infobox", ::E2ETrackNumberGeneralInfoBox)
    }

    val referenceLineLocation: E2ETrackNumberLocationInfoBox by lazy {
        infoBox("reference-line-location-infobox", ::E2ETrackNumberLocationInfoBox)
    }

    val locationTrackGeneralInfo: E2ELocationTrackGeneralInfoBox by lazy {
        infoBox("location-track-infobox", ::E2ELocationTrackGeneralInfoBox)
    }

    val locationTrackLocation: E2ELocationTrackLocationInfobox by lazy {
        infoBox("location-track-location-infobox", ::E2ELocationTrackLocationInfobox)
    }

    val locationTrackVerticalGeometry: E2ELocationTrackVerticalGeometryInfoBox by lazy {
        infoBox("location-track-vertical-geometry-infobox", ::E2ELocationTrackVerticalGeometryInfoBox)
    }

    val locationTrackLog: E2ELocationTrackLogInfoBox by lazy {
        infoBox("location-track-log-infobox", ::E2ELocationTrackLogInfoBox)
    }

    // re-used old qa-id so don't worry
    val trackNumberLog: E2ETrackNumberLogInfoBox by lazy {
        infoBox("track-number-log-infobox", ::E2ETrackNumberLogInfoBox)
    }

    val layoutSwitchGeneralInfo: E2ELayoutSwitchGeneralInfoBox by lazy {
        infoBox("switch-infobox", ::E2ELayoutSwitchGeneralInfoBox)
    }

    val layoutSwitchStructureGeneralInfo: E2ESwitchStructureGeneralInfoBox by lazy {
        infoBox("switch-structure-infobox", ::E2ESwitchStructureGeneralInfoBox)
    }

    val layoutSwitchLocation: E2ESwitchCoordinatesInfoBox by lazy {
        infoBox("switch-location-infobox", ::E2ESwitchCoordinatesInfoBox)
    }

    val layoutSwitchAdditionalInfo: E2ELayoutSwitchAdditionalInfoInfoBox by lazy {
        infoBox("switch-additional-infobox", ::E2ELayoutSwitchAdditionalInfoInfoBox)
    }

    val geometryAlignmentGeneral: E2EGeometryAlignmentGeneralInfoBox by lazy {
        infoBox("geometry-alignment-infobox", ::E2EGeometryAlignmentGeneralInfoBox)
    }

    val geometryAlignmentLinking: E2EGeometryAlignmentLinkingInfoBox by lazy {
        infoBox("geometry-alignment-linking-infobox", ::E2EGeometryAlignmentLinkingInfoBox)
    }

    val geometryKmPostLinking: E2EGeometryKmPostLinkingInfoBox by lazy {
        infoBox("geometry-km-post-linking-infobox", ::E2EGeometryKmPostLinkingInfoBox)
    }

    val geometrySwitchGeneral: E2EGeometrySwitchGeneralInfoBox by lazy {
        infoBox("geometry-switch-infobox", ::E2EGeometrySwitchGeneralInfoBox)
    }

    val geometrySwitchLinking: E2EGeometrySwitchLinkingInfoBox by lazy {
        infoBox("geometry-switch-linking-infobox", ::E2EGeometrySwitchLinkingInfoBox)
    }

    val geometryPlanGeneral: E2EGeometryPlanGeneralInfoBox by lazy {
        infoBox("geometry-plan-general-infobox", ::E2EGeometryPlanGeneralInfoBox)
    }

    val geometryPlanQuality: E2EGeometryPlanQualityInfobox by lazy {
        infoBox("geometry-plan-quality-infobox", ::E2EGeometryPlanQualityInfobox)
    }

    private fun <T : E2EInfoBox> infoBox(qaId: String, creator: (By) -> T) =
        childComponent(byQaId(qaId), creator).also { it.waitUntilLoaded() }
}
