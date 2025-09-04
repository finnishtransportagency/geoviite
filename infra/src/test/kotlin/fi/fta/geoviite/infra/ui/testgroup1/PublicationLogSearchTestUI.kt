package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationMessage
import fi.fta.geoviite.infra.publication.PublicationRequest
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.setUser
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class PublicationLogSearchTestUI
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val testPublicationUpdaterDao: TestPublicationUpdaterDao,
) : SeleniumTest() {

    @Test
    fun `Publication log date search works`() {
        testDBService.clearAllTables()

        val someTrackNumberId = mainDraftContext.save(trackNumber(TrackNumber("Test track number"))).id
        val someReferenceLine = HelsinkiTestData.westReferenceLine(someTrackNumberId, draft = true)
        val someTrack = HelsinkiTestData.westMainLocationTrack(someTrackNumberId, draft = true)

        val publicationRequests =
            listOf(
                PublicationRequest(
                    content = publicationRequestIds(trackNumbers = listOf(someTrackNumberId)),
                    message = PublicationMessage.of("some test publication 1"),
                ),
                PublicationRequest(
                    content =
                        publicationRequestIds(
                            referenceLines =
                                listOf(mainDraftContext.save(someReferenceLine.first, someReferenceLine.second).id)
                        ),
                    message = PublicationMessage.of("some test publication 2"),
                ),
                PublicationRequest(
                    content =
                        publicationRequestIds(
                            locationTracks = listOf(mainDraftContext.save(someTrack.first, someTrack.second).id)
                        ),
                    message = PublicationMessage.of("some test publication 3"),
                ),
            )

        val testDateBeforeAnyTestPublications = Instant.parse("2023-01-01T12:34:00Z")

        val testPublicationDates =
            listOf(
                Instant.parse("2023-01-02T12:34:00Z"),
                Instant.parse("2023-02-15T00:00:00Z"),
                Instant.parse("2023-03-01T00:00:00Z"),
            )

        val testDateAfterAllTestPublications = Instant.parse("2023-04-01T00:00:00Z")

        publicationRequests
            .map { publicationRequest -> testPublish(publicationRequest) }
            .zip(testPublicationDates)
            .map { (publicationId, newDate) ->
                // The publication service calls the publication dao, which uses the postgres
                // now() method to set the publication date value during publication.
                //
                // As we want to test the publication log search with different dates, the
                // previously assigned publication dates are manually updated here.
                testPublicationUpdaterDao.forcefullyUpdatePublicationDate(publicationId, newDate)
            }

        startGeoviite()
        val publicationLog = goToFrontPage().openPublicationLog()

        // Nothing outside search range should be displayed.
        publicationLog.setSearchStartDate(testDateBeforeAnyTestPublications)
        publicationLog.setSearchEndDate(testDateBeforeAnyTestPublications)
        publicationLog.waitUntilLoaded()
        assertEquals(0, publicationLog.rows.size)

        // Everything in search range should be displayed. Publication log shows publications in descending
        // timestamp order by default, hence the reversed order of indexing publication rows
        publicationLog.setSearchEndDate(testDateAfterAllTestPublications)
        publicationLog.waitUntilLoaded()
        assertEquals(3, publicationLog.rows.size)
        assertContains(publicationLog.rows[2].message, publicationRequests[0].message)
        assertContains(publicationLog.rows[1].message, publicationRequests[1].message)
        assertContains(publicationLog.rows[0].message, publicationRequests[2].message)

        // Only publications in between start & end dates should be displayed.
        publicationLog.setSearchEndDate(testPublicationDates[1])
        publicationLog.waitUntilLoaded()
        assertEquals(2, publicationLog.rows.size)
        assertContains(publicationLog.rows[1].message, publicationRequests[0].message)
        assertContains(publicationLog.rows[0].message, publicationRequests[1].message)

        // Same start & end date should display publications on the given date.
        publicationLog.setSearchStartDate(testPublicationDates[1])
        publicationLog.setSearchEndDate(testPublicationDates[1])
        publicationLog.waitUntilLoaded()
        assertContains(publicationLog.rows[0].message, publicationRequests[1].message)

        publicationLog.setSearchStartDate(testPublicationDates[2])
        publicationLog.setSearchEndDate(testPublicationDates[2])
        publicationLog.waitUntilLoaded()
        assertContains(publicationLog.rows[0].message, publicationRequests[2].message)

        publicationLog.returnToFrontPage()
    }

    private fun testPublish(publicationRequest: PublicationRequest): IntId<Publication> {
        val versions = publicationService.getValidationVersions(LayoutBranch.main, publicationRequest.content)
        val calculatedChanges = publicationService.getCalculatedChanges(versions)
        val result =
            publicationService.publishChanges(
                LayoutBranch.main,
                versions,
                calculatedChanges,
                publicationRequest.message,
                PublicationCause.MANUAL,
            )

        return result.publicationId!!
    }
}

@Component
class TestPublicationUpdaterDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun forcefullyUpdatePublicationDate(publicationId: IntId<Publication>, newPublicationTime: Instant) {
        jdbcTemplate.setUser()
        val sql =
            """
            update publication.publication
            set publication_time = :new_publication_time
            where id = :publication_id
        """
                .trimIndent()

        val params = MapSqlParameterSource()
        params.addValue("publication_id", publicationId.intValue, java.sql.Types.INTEGER)
        params.addValue(
            "new_publication_time",
            newPublicationTime.atOffset(ZoneOffset.UTC),
            java.sql.Types.TIMESTAMP_WITH_TIMEZONE,
        )

        jdbcTemplate.update(sql, params)
    }
}
