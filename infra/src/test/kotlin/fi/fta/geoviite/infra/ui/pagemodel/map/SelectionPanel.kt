package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import org.openqa.selenium.By

class E2ESelectionPanel(parentView: E2EViewFragment) : E2EViewFragment(parentView, By.className("track-layout__navi")) {

    val trackNumbersList: E2ETrackNumberSelectionList by lazy { E2ETrackNumberSelectionList(viewBy) }

    val kmPostsList: E2EKmPostSelectionList by lazy { E2EKmPostSelectionList(viewBy) }

    val referenceLinesList: E2EReferenceLineSelectionList by lazy { E2EReferenceLineSelectionList(viewBy) }

    val locationTracksList: E2ELocationTrackSelectionList by lazy { E2ELocationTrackSelectionList(viewBy) }

    val switchesList: E2ESwitchesSelectionList by lazy { E2ESwitchesSelectionList(viewBy) }

    val operationalPointsList: E2EOperationalPointSelectionList by lazy { E2EOperationalPointSelectionList(viewBy) }

    val geometryPlans: List<E2EGeometryPlanAccordion> by lazy {
        childElements(By.cssSelector(".geometry-plan-panel .accordion__header-title"))
            .map { it.text }
            .map {
                E2EGeometryPlanAccordion(
                    By.xpath(
                        "//div[@class='accordion' and parent::div[@class='geometry-plan-panel'] and h4/span[text() = '${it}']]"
                    ),
                    null,
                )
            }
    }

    fun selectTrackNumber(name: String): E2ESelectionPanel = apply {
        logger.info("Select trackNumber $name")
        trackNumbersList.selectByName(name)
    }

    fun selectKmPost(name: String): E2ESelectionPanel = apply {
        logger.info("Select km-post $name")
        kmPostsList.selectByName(name)
    }

    fun selectReferenceLine(name: String): E2ESelectionPanel = apply {
        logger.info("Select reference line $name")
        referenceLinesList.selectByName(name)
    }

    fun selectLocationTrack(name: String): E2ESelectionPanel = apply {
        logger.info("Select location track $name")
        locationTracksList.selectByName(name)
    }

    fun selectSwitch(name: String): E2ESelectionPanel = apply {
        logger.info("Select switch $name")
        switchesList.selectByName(name)
    }

    fun selectOperationalPoint(name: String): E2ESelectionPanel = apply {
        logger.info("Select operational point $name")
        operationalPointsList.selectByName(name)
    }

    fun selectPlanAlignment(planName: String, name: String): E2ESelectionPanel = apply {
        logger.info("Select plan $planName alignment $name")

        geometryPlanByName(planName).selectAlignment(name)
    }

    fun selectPlanSwitch(planName: String, name: String): E2ESelectionPanel = apply {
        logger.info("Select plan $planName switch $name")

        geometryPlanByName(planName).selectSwitch(name)
    }

    fun selectPlanKmPost(planName: String, name: String): E2ESelectionPanel = apply {
        logger.info("Select plan $planName km post $name")

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
