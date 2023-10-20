package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.LocalHostWebClient
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.testdata.createTrackLayoutTrackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class GeometryElementListTestUI @Autowired constructor(
    private val geometryDao: GeometryDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryService: GeometryService,
    private val webClient: LocalHostWebClient,
) : SeleniumTest() {

    @BeforeEach
    fun cleanup() {
        clearAllTestData()
    }

    @Test
    fun `List layout geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        val planVersion = insertSomePlan(trackNumberId)
        linkPlanToSomeLocationTrack(planVersion, trackNumberId)

        startGeoviite()
        val elementListPage = navigationBar.goToElementListPage()
        elementListPage.selectLocationTrack("foo")

        val results = elementListPage.resultList
        results.waitUntilCount(5)
        val firstRow = results.items[0]
        assertEquals("foo test track", firstRow.locationTrack)
        assertEquals("1.000", firstRow.locationStartE)

        elementListPage.line.click()
        results.waitUntilCount(3)
        elementListPage.line.click()
        elementListPage.clothoid.click()
        elementListPage.curve.click()
        elementListPage.missingGeometry.click()
        results.waitUntilCount(2)
        elementListPage.missingGeometry.click()
        results.waitUntilCount(3)
    }

    @Test
    fun `List plan geometry`() {
        insertSomePlan(trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id)
        startGeoviite()
        val planListPage = navigationBar.goToElementListPage().planListPage()
        planListPage.selectPlan("testfile")
        val results = planListPage.resultList
        results.waitUntilCount(4)
        planListPage.clothoid.click()
        results.waitUntilCount(3)
        planListPage.clothoid.click()
        planListPage.line.click()
        results.waitUntilCount(2)
        val resultItems = results.items
        assertEquals("Siirtymäkaari", resultItems[0].elementType)
        assertEquals("3.606", resultItems[0].length)
        assertEquals("Kaari", resultItems[1].elementType)
        assertEquals("4.263", resultItems[1].length)
        val downloadUrl = planListPage.downloadUrl
        val csv = webClient.get().uri(downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        assertTrue(csv.isNotEmpty())
    }

    @Test
    fun `List whole network geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        val planVersion = insertSomePlan(trackNumberId)
        linkPlanToSomeLocationTrack(planVersion, trackNumberId)
        geometryService.makeElementListingCsv()

        startGeoviite()
        val page = navigationBar.goToElementListPage().entireNetworkPage()
        val downloadUrl = page.downloadUrl
        val csv = webClient.get().uri(downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        assertTrue(csv.isNotEmpty())
    }

    private fun insertSomePlan(trackNumberId: IntId<TrackLayoutTrackNumber>): RowVersion<GeometryPlan> =
        geometryDao.insertPlan(
            plan = plan(
                trackNumberId = trackNumberId,
                alignments = listOf(
                    geometryAlignment(
                        name = "test-alignment-name",
                        elements = listOf(
                            line(Point(1.0, 1.0), Point(3.0, 3.0)),
                            minimalClothoid(Point(3.0, 3.0), Point(5.0, 6.0)),
                            line(Point(5.0, 6.0), Point(9.0, 10.0)),
                            minimalCurve(Point(9.0, 10.0), Point(12.0, 13.0))
                        ),
                    )
                ),
                coordinateSystemName = CoordinateSystemName("testcrs"),
                verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
            ),
            file = infraModelFile("testfile.xml"),
            boundingBoxInLayoutCoordinates = null,
        )

    private fun linkPlanToSomeLocationTrack(
        planVersion: RowVersion<GeometryPlan>,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ) {
        val geometryPlan = geometryDao.fetchPlan(planVersion)
        val geoAlignmentA = geometryPlan.alignments[0]
        val locationTrackAlignment = alignmentDao.insert(
            alignment(
                segment(Point(1.0, 1.0), Point(2.0, 2.0)).copy(sourceId = geoAlignmentA.elements[0].id),
                segment(Point(2.0, 2.0), Point(3.0, 3.0)).copy(sourceId = geoAlignmentA.elements[1].id),
                segment(Point(3.0, 3.0), Point(4.0, 4.0)),
                segment(Point(4.0, 4.0), Point(5.0, 5.0)).copy(sourceId = geoAlignmentA.elements[2].id),
                segment(Point(5.0, 5.0), Point(6.0, 6.0)).copy(sourceId = geoAlignmentA.elements[3].id),
            )
        )
        locationTrackDao.insert(
            locationTrack(trackNumberId, name = "foo test track").copy(alignmentVersion = locationTrackAlignment)
        )
    }
}
