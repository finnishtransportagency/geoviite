package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationMessage
import fi.fta.geoviite.infra.publication.PublicationRequest
import fi.fta.geoviite.infra.publication.PublicationRequestIds
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EPublicationLog
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData
import fi.fta.geoviite.infra.ui.util.browser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import waitUntilExists

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class PublicationLogUrlParamsTestUI
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val testPublicationUpdaterDao: TestPublicationUpdaterDao,
) : SeleniumTest() {

    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Dates chosen well outside default range (last month) to avoid interference with defaults
    private val pub1Date = Instant.parse("2023-01-02T12:00:00Z")
    private val pub2Date = Instant.parse("2023-02-15T12:00:00Z")
    private val pub3Date = Instant.parse("2023-03-01T12:00:00Z")

    private fun setupThreePublications() {
        testDBService.clearAllTables()
        val westId =
            mainDraftContext
                .save(trackNumber(TrackNumber("Test track number")), HelsinkiTestData.westReferenceLineGeometry())
                .id
        val eastId =
            mainDraftContext
                .save(trackNumber(TrackNumber("Test track number 2")), HelsinkiTestData.eastReferenceLineGeometry())
                .id
        val someTrack = HelsinkiTestData.westMainLocationTrack(westId, draft = true)
        val locationTrackId = mainDraftContext.save(someTrack.first, someTrack.second).id

        listOf(
                publish(publicationRequestIds(trackNumbers = listOf(westId)), "pub 1") to pub1Date,
                publish(publicationRequestIds(trackNumbers = listOf(eastId)), "pub 2") to pub2Date,
                publish(publicationRequestIds(locationTracks = listOf(locationTrackId)), "pub 3") to pub3Date,
            )
            .forEach { (id, date) -> testPublicationUpdaterDao.forcefullyUpdatePublicationDate(id, date) }
    }

    private fun publish(content: PublicationRequestIds, message: String): IntId<Publication> {
        val request = PublicationRequest(content = content, message = PublicationMessage.of(message))
        val versions = publicationService.getValidationVersions(LayoutBranch.main, request.content)
        val changes = publicationService.getCalculatedChanges(versions)
        return publicationService
            .publishChanges(LayoutBranch.main, versions, changes, request.message, PublicationCause.MANUAL)
            .publicationId!!
    }

    @Test
    fun `Setting search dates updates URL query params`() {
        testDBService.clearAllTables()
        startGeoviite()
        val log = goToFrontPage().openPublicationLog()

        log.setSearchStartDate(pub1Date)
        log.setSearchEndDate(pub2Date)

        val url = requireNotNull(browser().currentUrl)
        assertContains(url, "startDate=2023-01-02")
        assertContains(url, "endDate=2023-02-15")
    }

    @Test
    fun `Navigating directly to URL with date params restores search state`() {
        setupThreePublications()
        startGeoviite()
        val log = goToFrontPage().openPublicationLog()

        log.setSearchStartDate(pub1Date)
        log.setSearchEndDate(pub2Date)
        log.waitUntilLoaded()
        // Jan 2 and Feb 15 publications are within range; Mar 1 is not
        assertEquals(2, log.rows.size)

        // Reload the page — URL params should restore the search state
        browser().navigate().refresh()
        waitUntilExists(By.className("publication-log"))
        val reloadedLog = E2EPublicationLog()
        reloadedLog.waitUntilLoaded()

        assertEquals("02.01.2023", reloadedLog.startDateValue)
        assertEquals("15.02.2023", reloadedLog.endDateValue)
        assertEquals(2, reloadedLog.rows.size)
    }

    @Test
    fun `Browser back from front page returns to filtered publication log`() {
        setupThreePublications()
        startGeoviite()
        val log = goToFrontPage().openPublicationLog()

        // Filter to show only the Feb 15 publication
        log.setSearchStartDate(pub2Date)
        log.setSearchEndDate(pub2Date)
        log.waitUntilLoaded()
        assertEquals(1, log.rows.size)

        // Navigate to front page via the breadcrumb link — pushes a new history entry
        log.returnToFrontPage()

        // Back should restore /publications with the filter params intact
        browser().navigate().back()
        waitUntilExists(By.className("publication-log"))

        val restoredLog = E2EPublicationLog()
        restoredLog.waitUntilLoaded()

        val restoredUrl = requireNotNull(browser().currentUrl)
        assertContains(restoredUrl, "startDate=2023-02-15")
        assertContains(restoredUrl, "endDate=2023-02-15")
        assertEquals(1, restoredLog.rows.size)
        assertContains(restoredLog.rows[0].message, "pub 2")
    }

    @Test
    fun `Publication log shows default date range when navigated to without URL params`() {
        testDBService.clearAllTables()
        startGeoviite()
        val log = goToFrontPage().openPublicationLog()

        val helsinki = ZoneId.of("Europe/Helsinki")
        val today = LocalDate.now(helsinki)
        val oneMonthAgo = today.minusMonths(1)

        assertEquals(displayDateFormatter.format(oneMonthAgo), log.startDateValue)
        assertEquals(displayDateFormatter.format(today), log.endDateValue)
    }

    @Test
    fun `Navigating directly to URL with invalid specific item ID shows not found error`() {
        testDBService.clearAllTables()
        startGeoviite()
        // Navigate via clicks first so React Router is mounted, then set hash params via JS
        goToFrontPage().openPublicationLog()
        (browser() as JavascriptExecutor).executeScript(
            "window.location.hash = '/publications?specificType=LOCATION_TRACK&specificId=INT_99999'"
        )
        waitUntilExists(By.className("publication-log__table-header-error"))
        val log = E2EPublicationLog()
        assertEquals("Muutoskohdetta ei löydy.", log.tableHeaderError)
    }

    @Test
    fun `Publication log date search filters rows correctly`() {
        setupThreePublications()
        startGeoviite()
        val log = goToFrontPage().openPublicationLog()

        // Set a wide range covering all three publications
        log.setSearchStartDate(pub1Date)
        log.setSearchEndDate(pub3Date)
        log.waitUntilLoaded()
        assertEquals(3, log.rows.size)

        // Narrow to Feb 15 only
        log.setSearchStartDate(pub2Date)
        log.setSearchEndDate(pub2Date)
        log.waitUntilLoaded()
        assertEquals(1, log.rows.size)
        assertContains(log.rows[0].message, "pub 2")

        // Expand start date back to Jan 2 — now Jan 2 and Feb 15 both visible
        log.setSearchStartDate(pub1Date)
        log.waitUntilLoaded()
        assertEquals(2, log.rows.size)
    }
}
