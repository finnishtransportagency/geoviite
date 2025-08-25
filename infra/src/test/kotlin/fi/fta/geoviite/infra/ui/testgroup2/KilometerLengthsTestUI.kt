package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.ui.LocalHostWebClient
import fi.fta.geoviite.infra.ui.SeleniumTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class KilometerLengthsTestUI
@Autowired
constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val referenceLineDao: ReferenceLineDao,
    private val kmPostDao: LayoutKmPostDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val webClient: LocalHostWebClient,
) : SeleniumTest() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()

        val trackNumberId = trackNumberDao.save(trackNumber(TrackNumber("foo"), draft = false)).id

        val lineSegments = listOf((segment(Point(0.0, 0.0), Point(4000.0, 0.0))))
        referenceLineDao.save(
            referenceLine(trackNumberId, alignmentVersion = alignmentDao.insert(alignment(lineSegments)), draft = false)
        )
        locationTrackDao.save(
            locationTrack(trackNumberId = trackNumberId, name = "foo bar", draft = false),
            trackGeometryOfSegments(lineSegments),
        )
        kmPostDao.save(kmPost(trackNumberId, KmNumber(1), kmPostGkLocation(900.0, 1.0), draft = false))
        kmPostDao.save(kmPost(trackNumberId, KmNumber(2), kmPostGkLocation(2000.0, -1.0), draft = false))
        kmPostDao.save(kmPost(trackNumberId, KmNumber(3), kmPostGkLocation(3000.0, 0.0), draft = false))
    }

    @Test
    fun `Display kilometer lengths`() {
        startGeoviite()
        val kmLengthsPage = navigationBar.goToKilometerLengthsPage()
        kmLengthsPage.selectLocationTrack("foo")
        val results = kmLengthsPage.resultList
        results.waitUntilItemCount(4)
        val items = results.items
        assertEquals(listOf("0.000", "900.000", "2000.000", "3000.000"), items.map { it.stationStart })

        val downloadUrl = kmLengthsPage.downloadUrl
        val csv = webClient.get().uri(downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        Assertions.assertTrue(csv.isNotEmpty())
    }

    @Test
    fun `Download all kilometer lengths`() {
        startGeoviite()
        val kmLengthsPage = navigationBar.goToKilometerLengthsPage().openEntireNetworkTab()
        val downloadUrl = kmLengthsPage.downloadUrl
        val csv = webClient.get().uri(downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        Assertions.assertTrue(csv.isNotEmpty())
    }
}
