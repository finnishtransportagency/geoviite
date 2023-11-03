package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
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
class KilometerLengthsTestUI @Autowired constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val referenceLineDao: ReferenceLineDao,
    private val kmPostDao: LayoutKmPostDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val webClient: LocalHostWebClient,
) : SeleniumTest() {

    @BeforeEach
    fun cleanup() {
        clearAllTestData()

        val trackNumberId = trackNumberDao.insert(trackNumber(TrackNumber("foo"))).id

        val alignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(4000.0, 0.0))))
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = alignment))
        locationTrackDao.insert(locationTrack(trackNumberId, name = "foo bar", alignmentVersion = alignment))
        kmPostDao.insert(kmPost(trackNumberId, KmNumber(1), Point(900.0, 1.0)))
        kmPostDao.insert(kmPost(trackNumberId, KmNumber(2), Point(2000.0, -1.0)))
        kmPostDao.insert(kmPost(trackNumberId, KmNumber(3), Point(3000.0, 0.0)))
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
        val kmLengthsPage = navigationBar.goToKilometerLengthsPage().entireNetworkPage()
        val downloadUrl = kmLengthsPage.downloadUrl
        val csv = webClient.get().uri(downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        Assertions.assertTrue(csv.isNotEmpty())
    }

}
