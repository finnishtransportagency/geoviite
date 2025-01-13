package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.infraModelFile
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.minimalClothoid
import fi.fta.geoviite.infra.geometry.minimalCurve
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.ui.LocalHostWebClient
import fi.fta.geoviite.infra.ui.SeleniumTest
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class GeometryElementListTestUI
@Autowired
constructor(
    private val geometryDao: GeometryDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryService: GeometryService,
    private val webClient: LocalHostWebClient,
) : SeleniumTest() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `List layout geometry`() {
        val trackNumber = TrackNumber("foo")
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(trackNumber).id as IntId
        val planVersion = insertSomePlan(trackNumber)
        linkPlanToSomeLocationTrack(planVersion, trackNumberId)

        startGeoviite()
        val elementListPage = navigationBar.goToElementListPage()
        elementListPage.selectLocationTrack("foo")

        val results = elementListPage.resultList
        results.waitUntilItemCount(5)
        val firstRow = results.items[0]
        assertEquals("foo test track", firstRow.locationTrack)
        assertEquals("1.000", firstRow.locationStartE)

        elementListPage.line.click()
        results.waitUntilItemCount(3)
        elementListPage.line.click()
        elementListPage.clothoid.click()
        elementListPage.curve.click()
        elementListPage.missingGeometry.click()
        results.waitUntilItemCount(2)
        elementListPage.missingGeometry.click()
        results.waitUntilItemCount(3)
    }

    @Test
    fun `List plan geometry`() {
        insertSomePlan(testDBService.getUnusedTrackNumber())
        startGeoviite()
        val planListPage = navigationBar.goToElementListPage().planListPage()
        planListPage.selectPlan("testfile")
        val results = planListPage.resultList
        results.waitUntilItemCount(4)
        planListPage.clothoid.click()
        results.waitUntilItemCount(3)
        planListPage.clothoid.click()
        planListPage.line.click()
        results.waitUntilItemCount(2)
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
        val (trackNumber, trackNumberId) = mainOfficialContext.createTrackNumberAndId()
        val planVersion = insertSomePlan(trackNumber)
        linkPlanToSomeLocationTrack(planVersion, trackNumberId)
        geometryService.makeElementListingCsv()

        startGeoviite()
        val page = navigationBar.goToElementListPage().entireNetworkPage()
        val downloadUrl = page.downloadUrl
        val csv = webClient.get().uri(downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        assertTrue(csv.isNotEmpty())
    }

    private fun insertSomePlan(trackNumber: TrackNumber): RowVersion<GeometryPlan> =
        geometryDao.insertPlan(
            plan =
                plan(
                    trackNumber = trackNumber,
                    alignments =
                        listOf(
                            geometryAlignment(
                                name = "test-alignment-name",
                                elements =
                                    listOf(
                                        line(Point(1.0, 1.0), Point(3.0, 3.0)),
                                        minimalClothoid(Point(3.0, 3.0), Point(5.0, 6.0)),
                                        line(Point(5.0, 6.0), Point(9.0, 10.0)),
                                        minimalCurve(Point(9.0, 10.0), Point(12.0, 13.0)),
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
        trackNumberId: IntId<LayoutTrackNumber>,
    ) {
        val geometryPlan = geometryDao.fetchPlan(planVersion)
        val geoAlignmentA = geometryPlan.alignments[0]
        val locationTrackAlignment =
            alignmentDao.insert(
                alignment(
                    segment(Point(1.0, 1.0), Point(2.0, 2.0))
                        .copy(sourceId = geoAlignmentA.elements[0].id as IndexedId),
                    segment(Point(2.0, 2.0), Point(3.0, 3.0))
                        .copy(sourceId = geoAlignmentA.elements[1].id as IndexedId),
                    segment(Point(3.0, 3.0), Point(4.0, 4.0)),
                    segment(Point(4.0, 4.0), Point(5.0, 5.0))
                        .copy(sourceId = geoAlignmentA.elements[2].id as IndexedId),
                    segment(Point(5.0, 5.0), Point(6.0, 6.0)).copy(sourceId = geoAlignmentA.elements[3].id as IndexedId),
                )
            )
        locationTrackDao.save(
            locationTrack(
                trackNumberId = trackNumberId,
                name = "foo test track",
                alignmentVersion = locationTrackAlignment,
                draft = false,
            )
        )
    }
}
