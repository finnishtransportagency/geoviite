package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.ListContentItem
import fi.fta.geoviite.infra.ui.pagemodel.common.ListModel
import fi.fta.geoviite.infra.ui.pagemodel.common.byLiTag
import fi.fta.geoviite.infra.ui.util.fetch
import getChildrenWhenVisible
import getElementWhenVisible
import getElementsWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tryWait

// TODO: GVT-1935 The contents of these lists should be implemented as own components using ListModel (see ListModel.kt)
//   As an example, there is already a list for locationTracks, though some tests don't use it yet
class MapNavigationPanel {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val locationTracksList: LocationTracksNavigationList by lazy { LocationTracksNavigationList() }
    val kmPostsList: KmPostsNavigationList by lazy { KmPostsNavigationList() }

    fun selectTrackNumber(name: String) {
        logger.info("Select trackNumber $name")
        val trackNumbers = trackNumbers()
        val trackNumber = trackNumbers.find { trackLayoutTrackNumber -> trackLayoutTrackNumber.name() == name }
            ?: throw RuntimeException("Track number $name not found! Available track numbers $trackNumbers")
        trackNumber.select()
    }

    fun trackNumbers(): List<TrackLayoutTrackNumber> =
        getElementsWhenVisible(By.xpath("//li[@class='track-number-panel__track-number']")).map { element ->
            TrackLayoutTrackNumber(element)
        }.also { logger.info("Track numbers $it") }

    fun selectTrackLayoutKmPost(name: String) {
        logger.info("Select km-post $name")
        val kmPosts = kmPosts()
        val kmPost = kmPosts.find { kmPost -> kmPost.name() == name }
            ?: throw RuntimeException("KM-Post $name not found! Available KM posts $kmPosts")
        kmPost.select()
    }

    fun kmPosts(): List<TrackLayoutKmPost> {
        return try {
            getListElements(By.xpath("//ol[@class='km-posts-panel__km-posts']")).map { TrackLayoutKmPost(it) }
                .also { logger.info("KM posts $it") }
        } catch (ex: TimeoutException) {
            emptyList()
        }
    }

    fun selectReferenceLine(name: String) {
        logger.info("Select reference line $name")
        val referenceLines = referenceLines()
        val referenceLine = referenceLines.find { trackLayoutAlignment -> trackLayoutAlignment.name() == name }
            ?: throw RuntimeException("Reference line '$name' not found! Available reference lines are: $referenceLines")
        referenceLine.select()
    }

    fun referenceLines(): List<TrackLayoutAlignment> {
        //Wait until alignment panel exists then check if it contains alignments
        return try {
            getListElements(By.xpath("//ol[@qa-id='reference-lines-list']")).map { element ->
                TrackLayoutAlignment(
                    element
                )
            }.also { logger.info("Reference lines $it") }
        } catch (ex: TimeoutException) {
            emptyList()
        }
    }

    @Deprecated("Use mapNavigationPanel.locationTracksList instead")
    fun selectLocationTrack(locationTrackName: String) {
        logger.info("Select location track $locationTrackName")
        val locationTracks = locationTracks()
        val locationTrack = locationTracks.find { track -> track.name() == locationTrackName }
            ?: throw RuntimeException("Location track '$locationTrackName' not found! Available location tracks are: $locationTracks")
        locationTrack.select()
    }

    @Deprecated("Use mapNavigationPanel.locationTracksList instead")
    fun locationTracks(): List<TrackLayoutAlignment> {
        logger.info("Get all location tracks")
        return try {
            getListElements(By.xpath("//ol[@qa-id='location-tracks-list']")).map { element ->
                TrackLayoutAlignment(
                    element
                )
            }.also { logger.info("Location tracks $it") }
        } catch (ex: TimeoutException) {
            logger.warn("No location tracks found")
            emptyList()
        }
    }

    fun waitForReferenceLineNamesTo(namePredicate: (names: List<String>) -> Boolean) {
        logger.info("Wait for reference line names to match expectation")
        tryWait({ namePredicate(referenceLines().map { it.name() }) },
            { "Failed to match expectation for reference line names, names=${referenceLines().map { it.name() }}" })
    }

    fun waitForTrackNumberNamesTo(namePredicate: (names: List<String>) -> Boolean) {
        logger.info("Wait for track numbers to match expectation")
        tryWait({ namePredicate(trackNumbers().map { it.name() }) },
            { "Failed to match expectation for track number names, names=${trackNumbers().map { it.name() }}" })
    }

    fun selectTrackLayoutSwitch(switchName: String) {
        logger.info("Select switch $switchName")
        val switches = switches()
        val switch = switches.find { switch -> switch.name() == switchName }
            ?: throw RuntimeException("Switch $switchName not found! Available switches $switches")

        switch.select()
    }

    fun switches(): List<TrackLayoutSwitch> {
        //Wait until switches panel exists then check if it contains alignments
        return try {
            getListElements(By.xpath("//ol[@class='switch-panel__switches']")).map { element ->
                TrackLayoutSwitch(
                    element
                )
            }.also { logger.info("Switches $it") }
        } catch (ex: TimeoutException) {
            emptyList()
        }
    }

    fun geometryPlanByName(planName: String): GeometryPlanAccordion {
        logger.info("Select geometry plan '$planName'")
        Thread.sleep(500) //Only way to ensure the list is stable and not updating
        val plans = geometryPlans()
        return plans.find { plan -> plan.header() == planName }
            ?: throw RuntimeException("Geometry plan '$planName' not found! Available plans ${plans.map { plan -> plan.header() }}")
    }

    private fun geometryPlans(): List<GeometryPlanAccordion> {
        logger.info("Get all geometry plans")
        getElementWhenVisible(By.cssSelector("div.geometry-plan-panel div.accordion"))
        return try {
            val planNames = getElementsWhenVisible(By.cssSelector("span.accordion__header-title")).map { it.text }
            logger.info("Geometry plans: $planNames")
            planNames.map { GeometryPlanAccordion(By.xpath("//div[@class='accordion' and h4/span[text() = '${it}']]")) }
        } catch (ex: TimeoutException) {
            logger.warn("No geometry plans found")
            emptyList()
        }
    }

    @Deprecated("Implement lists through ListModel")
    fun getListElements(listBy: By) = getChildrenWhenVisible(fetch(listBy), By.tagName("li"))
    fun waitUntilSwitchNotVisible(switchName: String) {
        tryWait(
            { switches().none { it.name() == switchName } },
            { "Switch did not disappear from navigation: switch=$switchName visible=${switches().map { it.name() }}" },
        )
    }
}

data class KmPostListItem(
    val name: String,
    override val index: Int,
): ListContentItem {
    constructor(index: Int, element: WebElement) : this(
        name = element.findElement(By.xpath("./div/span")).text,
        index = index
    )
}

class KmPostsNavigationList : ListModel<KmPostListItem>(
    listBy = By.xpath("//ol[@class='km-posts-panel__km-posts']"),
    itemsBy = byLiTag,
    getContent = { i: Int, e: WebElement -> KmPostListItem(i, e) }) {
    fun selectByName(name: String) = selectItemWhenMatches { lt -> lt.name == name }

    fun waitUntilNameVisible(name: String) = waitUntilItemMatches { lt -> lt.name == name }
}

class LocationTracksNavigationList :
    ListModel<LocationTrackListItem>(listBy = By.xpath("//ol[@qa-id='location-tracks-list']"),
        itemsBy = byLiTag,
        getContent = { i: Int, e: WebElement -> LocationTrackListItem(i, e) }) {
    fun selectByName(name: String) = selectItemWhenMatches { lt -> lt.name == name }

    fun waitUntilNameVisible(name: String) = waitUntilItemMatches { lt -> lt.name == name }

    // TODO: GVT-1935 implement a generic way to handle list selection status through qa-id
    override fun isSelected(element: WebElement): Boolean =
        element.findElement(By.xpath("./*[1]")).getAttribute("class").contains("selected")
}

data class LocationTrackListItem(
    val name: String,
    val type: String,
    override val index: Int,
) : ListContentItem {
    constructor(index: Int, element: WebElement) : this(
        name = element.findElement(By.xpath("./div/span")).text,
        type = element.findElement(By.xpath("./span")).text,
        index = index,
    )
}
