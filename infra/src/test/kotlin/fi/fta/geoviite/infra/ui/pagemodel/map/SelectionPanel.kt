package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.fetch
import getElementsWhenVisible
import org.openqa.selenium.By
import tryWait

class E2ESelectionPanel(
    parentFetch: ElementFetch,
) : E2EViewFragment(fetch(parentFetch, By.className("track-layout__navi"))) {

    val trackNumbersList: E2ETrackNumberSelectionList by lazy {
        E2ETrackNumberSelectionList(this.elementFetch)
    }

    val kmPostsList: E2EKmPostSelectionList by lazy {
        E2EKmPostSelectionList(this.elementFetch)
    }

    val referenceLinesList: E2EReferenceLineSelectionList by lazy {
        E2EReferenceLineSelectionList(this.elementFetch)
    }

    val locationTracksList: E2ELocationTrackSelectionList by lazy {
        E2ELocationTrackSelectionList(this.elementFetch)
    }

    val switchesList: E2ESwitchesSelectionList by lazy {
        E2ESwitchesSelectionList(this.elementFetch)
    }

    val geometryPlans: List<E2EGeometryPlanAccordion> by lazy {
        getElementsWhenVisible(By.cssSelector(".geometry-plan-panel .accordion__header-title"))
            .map { it.text }
            .map { E2EGeometryPlanAccordion(By.xpath("//div[@class='accordion' and parent::div[@class='geometry-plan-panel'] and h4/span[text() = '${it}']]")) }
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

    fun selectPlanAlignment(planName: String, name: String): E2ESelectionPanel = apply {
        geometryPlanByName(planName).selectAlignment(name)
    }

    fun selectPlanSwitch(planName: String, name: String): E2ESelectionPanel = apply {
        geometryPlanByName(planName).selectSwitch(name)
    }

    fun selectPlanKmPost(planName: String, name: String): E2ESelectionPanel = apply {
        geometryPlanByName(planName).selectKmPost(name)
    }

    fun geometryPlanByName(name: String): E2EGeometryPlanAccordion {
        return geometryPlans.first { it.header == name }
    }

    fun waitUntilSwitchNotVisible(name: String): E2ESelectionPanel = apply {
        tryWait(
            { switchesList.items.none { it.name == name } },
            { "Switch did not disappear from navigation: switch=$name visible=${switchesList.items.map { it.name }}" },
        )
    }

    fun waitUntilLocationTrackVisible(name: String): E2ESelectionPanel = apply {
        locationTracksList.waitUntilItemMatches { it.name == name }
    }
}
