package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.ratko.FakeRatkoService
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.ratko.ratkoRouteNumber
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import fi.fta.geoviite.infra.ui.testdata.createTrackLayoutTrackNumber
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class FrontPageTestUI @Autowired constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val publicationDao: PublicationDao,
    private val fakeRatkoService: FakeRatkoService,
    private val ratkoPushDao: RatkoPushDao,
) : SeleniumTest() {

    @BeforeEach
    fun setup() {
        clearAllTestData()
        deleteFromTables("integrations", "ratko_push_content")
    }

    @Test
    fun `Retry failed publication`() {
        val originalTrackNumberVersion = trackNumberDao.insert(
            createTrackLayoutTrackNumber("original name").copy(externalId = Oid("1.2.3.4.5"))
        ).rowVersion
        val trackNumberId = originalTrackNumberVersion.id
        val alignmentVersion = alignmentDao.insert(
            alignment(segment(toSegmentPoints(Point(0.0, 0.0), Point(10.0, 0.0))))
        )
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = alignmentVersion, draft = false))

        val successfulPublicationId = publicationDao.createPublication("successful")
        publicationDao.insertCalculatedChanges(successfulPublicationId, changesTouchingTrackNumber(trackNumberId))

        trackNumberDao.update(
            trackNumberDao.fetch(originalTrackNumberVersion).copy(number = TrackNumber("updated name"))
        )

        val failingPublicationId = publicationDao.createPublication("failing test publication")
        publicationDao.insertCalculatedChanges(failingPublicationId, changesTouchingTrackNumber(trackNumberId))

        val failedRatkoPushId = ratkoPushDao.startPushing(listOf(failingPublicationId))
        ratkoPushDao.updatePushStatus(failedRatkoPushId, RatkoPushStatus.FAILED)

        startGeoviite()

        // two publications; an original one that succeeded (with the original name), then a new one above it that
        // failed
        E2EFrontPage()
            .openNthPublication(2)
            .apply { assertEquals(rows.first().trackNumbers, "original name") }
            .returnToFrontPage()
            .openNthPublication(1)
            .apply {
                rows.first().also { r ->
                    assertEquals(r.trackNumbers, "updated name")
                    assertEquals(r.pushedToRatko, "Ei")
                }
            }
            .returnToFrontPage()

        val fakeRatko = fakeRatkoService.start()
        fakeRatko.isOnline()
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))

        E2EFrontPage().pushToRatko()

        // the publication list will only update with the changeTimes mechanism, which can take up to 15 seconds,
        // so we're not going to bother checking that; we'll just poll Ratko to see that the change went through instead
        val maxWaitUntil = Instant.now().plusSeconds(2)
        while (Instant.now().isBefore(maxWaitUntil) && fakeRatko.getPushedRouteNumber(Oid("1.2.3.4.5")).isEmpty()) {
            Thread.sleep(100)
        }
        assertEquals("updated name", fakeRatko.getPushedRouteNumber(Oid("1.2.3.4.5"))[0].name)
    }

    private fun changesTouchingTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>): CalculatedChanges =
        CalculatedChanges(
            DirectChanges(
                trackNumberChanges = listOf(
                    TrackNumberChange(
                        trackNumberId,
                        changedKmNumbers = setOf(),
                        isStartChanged = false,
                        isEndChanged = false,
                    )
                ),
                kmPostChanges = listOf(),
                switchChanges = listOf(),
                locationTrackChanges = listOf(),
                referenceLineChanges = listOf(),
            ),
            IndirectChanges(
                trackNumberChanges = listOf(),
                switchChanges = listOf(),
                locationTrackChanges = listOf(),
            ),
        )
}
