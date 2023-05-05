package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.*
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel.Companion.browser
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel.Companion.getElementWhenVisible
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel.Companion.getElementsWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class MapNavigationPanel {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun selectTrackNumber(name: String) {
        logger.info("Select tracknumber $name")
        val trackNumbers = trackNumbers()
        val trackNumber =
            trackNumbers.find { trackLayoutTrackNumber -> trackLayoutTrackNumber.name().equals(name) }
                ?: throw RuntimeException("Track number ${name} not found! Available track numbers $trackNumbers")
        trackNumber.select()
    }

    fun trackNumbers(): List<TrackLayoutTrackNumber> =
        getElementsWhenVisible(By.xpath("//li[@class='track-number-panel__track-number']"))
            .map { element -> TrackLayoutTrackNumber(element) }.also { logger.info("Track numbers $it") }

    fun selectTrackLayoutKmPost(name: String) {
        logger.info("Select km-post $name")
        val kmPosts = kmPosts()
        val kmPost = kmPosts.find { kmPost -> kmPost.name().equals(name) }
            ?: throw RuntimeException("KM-Post $name not found! Available KM posts $kmPosts")
        kmPost.select()
    }

    fun kmPosts(): List<TrackLayoutKmPost> {
        return try{
            DynamicList(By.xpath("//ol[@class='km-posts-panel__km-posts']")).listElements()
                .map { TrackLayoutKmPost(it) }.also { logger.info("KM posts $it") }
        } catch (ex: TimeoutException) {
            emptyList()
        }
    }

    fun selectReferenceLine(name: String) {
        logger.info("Select reference line $name")
        val referenceLines = referenceLines()
        val refenceLine =
            referenceLines.find { trackLayoutAlignment -> trackLayoutAlignment.name().equals(name) }
                ?: throw RuntimeException("Reference line '$name' not found! Available reference lines are: ${referenceLines}")
        refenceLine.select()
    }

    fun referenceLines(): List<TrackLayoutAlignment> {
        //Wait until alignment panel exists then check if it contains alignments
        return try {
            DynamicList(By.xpath("//ol[@qa-id='reference-lines-list']")).listElements()
                .map { element -> TrackLayoutAlignment(element) }
                .also { logger.info("Reference lines $it") }
        } catch (ex: TimeoutException) {
            emptyList()
        }
    }

    fun selectLocationTrack(locationTrackName: String) {
        logger.info("Select location track $locationTrackName")
        val locationTracks = locationTracks()
        val locationTrack =
            locationTracks.find { trackLayoutAlignment -> trackLayoutAlignment.name() == locationTrackName }
                ?: throw RuntimeException("Location track '$locationTrackName' not found! Available location tracks are: ${locationTracks}")
        locationTrack.select()
    }

    fun locationTracks(): List<TrackLayoutAlignment> {
        logger.info("Get all location tracks")
        return try {
            DynamicList(By.xpath("//ol[@qa-id='location-tracks-list']")).listElements()
                .map { element -> TrackLayoutAlignment(element) }
                .also { logger.info("Location tracks $it") }
        } catch (ex: TimeoutException) {
            logger.warn("No location tracks found")
            emptyList()
        }
    }

    fun waitForLocationTrackNamesTo(namePredicate: (names: List<String>) -> Boolean) {
        logger.info("Wait for location track names")
        WebDriverWait(browser(), Duration.ofSeconds(5))
            .until { _ ->
                namePredicate(DynamicList(By.xpath("//ol[@qa-id='location-tracks-list']")).listElements()
                    .map { element -> TrackLayoutAlignment(element) }.also { logger.info("Location tracks $it") }
                    .map { alignment -> alignment.name() })
            }
    }

    fun selectTrackLayoutSwitch(switchName: String) {
        logger.info("Select switch $switchName")
       try {
           getElementWhenVisible(By.xpath("//ol[@class = 'switch-panel__switches']//span[text() = '$switchName']")).click()
       }catch (ex: TimeoutException) {
           val switches = switches()
           throw RuntimeException("Switch $switchName not found! Available switches $switches")
       }
    }

    fun switches(): List<TrackLayoutSwitch> {
        //Wait until switches panel exists then check if it contains alignments
        try {
            return DynamicList(By.xpath("//ol[@class='switch-panel__switches']")).listElements()
                .map { element -> TrackLayoutSwitch(element) }.also { logger.info("Switches $it") }
        } catch (ex: TimeoutException) {
            return emptyList()
        }
    }

    fun geometryPlanByName(planName: String): GeometryPlanAccordion {
        logger.info("Select geometry plan '$planName'")
        Thread.sleep(500) //Only way to ensure the list is stable and not updating
        val plans = geometryPlans()
        return plans.find { plan -> plan.header().equals(planName) }
            ?: throw RuntimeException("Geometry plan '$planName' not found! Available plans ${plans.map { plan -> plan.header() }}")
    }

    fun geometryPlans(): List<GeometryPlanAccordion> {
        logger.info("Get all geometry plans")
        getElementWhenVisible(By.cssSelector("div.geometry-plan-panel div.accordion"))
        try {
            val planNames = getElementsWhenVisible(By.cssSelector("span.accordion__header-title"))
                .map { it.text }
            logger.info("Geometry plans: $planNames")
            return planNames.map { GeometryPlanAccordion(By.xpath("//div[@class='accordion' and h4/span[text() = '${it}']]")) }
        } catch (ex: TimeoutException) {
            logger.warn("No geometry plans found")
            return emptyList()
        }
    }

    private class DynamicList(by: By): PageModel(by) {
        fun listElements() = getChildElementsStaleSafe(By.tagName("li"))
    }
}
