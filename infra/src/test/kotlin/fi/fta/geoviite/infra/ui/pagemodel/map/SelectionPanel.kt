package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import org.openqa.selenium.By

class E2ESelectionPanel(
    parentView: E2EViewFragment,
) : E2EViewFragment(parentView, By.className("track-layout__navi")) {

    val trackNumbersList: E2ETrackNumberSelectionList by lazy {
        E2ETrackNumberSelectionList(viewBy)
    }

    val kmPostsList: E2EKmPostSelectionList by lazy {
        E2EKmPostSelectionList(viewBy)
    }

    val referenceLinesList: E2EReferenceLineSelectionList by lazy {
        E2EReferenceLineSelectionList(viewBy)
    }

    val locationTracksList: E2ELocationTrackSelectionList by lazy {
        E2ELocationTrackSelectionList(viewBy)
    }

    val switchesList: E2ESwitchesSelectionList by lazy {
        E2ESwitchesSelectionList(viewBy)
    }

    val geometryPlans: List<E2EGeometryPlanAccordion> by lazy {
        childElements(By.cssSelector(".geometry-plan-panel .accordion__header-title"))
            .map { it.text }
            .map { E2EGeometryPlanAccordion(By.xpath("//div[@class='accordion' and parent::div[@class='geometry-plan-panel'] and h4/span[text() = '${it}']]")) }
    }

    fun selectOrUnselectTrackNumber(name: String): E2ESelectionPanel = apply {
        logger.info("Select trackNumber $name (or unselect if previously selected)")
        trackNumbersList.selectByName(name)
    }

    fun selectOrUnselectKmPost(name: String): E2ESelectionPanel = apply {
        logger.info("Select km-post $name (or unselect if previously selected)")
        kmPostsList.selectByName(name)
    }

    fun selectOrUnselectReferenceLine(name: String): E2ESelectionPanel = apply {
        logger.info("Select reference line $name (or unselect if previously selected)")
        referenceLinesList.selectByName(name)
    }

    fun selectOrUnselectLocationTrack(name: String): E2ESelectionPanel = apply {
        logger.info("Select location track $name (or unselect if previously selected)")
        locationTracksList.selectByName(name)
    }

    fun selectOrUnselectSwitch(name: String): E2ESelectionPanel = apply {
        logger.info("Select switch $name (or unselect if previously selected)")
        switchesList.selectByName(name)
    }

    fun selectOrUnselectPlanAlignment(planName: String, name: String): E2ESelectionPanel = apply {
        logger.info("Select plan $planName alignment $name (or unselect if previously selected)")

        geometryPlanByName(planName).selectAlignment(name)
    }

    fun selectOrUnselectPlanSwitch(planName: String, name: String): E2ESelectionPanel = apply {
        logger.info("Select plan $planName switch $name (or unselect if previously selected)")

        geometryPlanByName(planName).selectSwitch(name)
    }

    fun selectOrUnselectPlanKmPost(planName: String, name: String): E2ESelectionPanel = apply {
        logger.info("Select plan $planName km post $name (or unselect if previously selected)")

        geometryPlanByName(planName).selectKmPost(name)
    }

    fun geometryPlanByName(name: String): E2EGeometryPlanAccordion {
        return geometryPlans.first { it.header == name }
    }

    fun waitUntilSwitchNotVisible(name: String): E2ESelectionPanel = apply {
        switchesList.waitUntilItemIsRemoved { it.name == name }
    }

    fun waitUntilLocationTrackVisible(name: String): E2ESelectionPanel = apply {
        locationTracksList.waitUntilItemMatches { it.name == name }
    }
}
