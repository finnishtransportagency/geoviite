package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.VICircularCurve
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.infraModelFile
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.ui.LocalHostWebClient
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.testdata.createGeometryKmPost
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class VerticalGeometryElementListTestUI
@Autowired
constructor(
    private val geometryDao: GeometryDao,
    private val geometryService: GeometryService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val webClient: LocalHostWebClient,
) : SeleniumTest() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `List plan vertical geometry`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        insertGoodPlan(trackNumber)
        insertMinimalPlan(trackNumber)
        startGeoviite()
        val page = navigationBar.goToVerticalGeometryListPage().planListPage()
        page.selectPlan("goodplan")
        val results = page.resultList
        results.waitUntilItemCount(2)
        val resultItems = results.items
        assertEquals(listOf("5.280", "5.240"), resultItems.map { it.pviPointHeight })
        assertEquals(listOf("385808.877", "385943.755"), resultItems.map { it.pviPointLocationE })

        val csv = webClient.get().uri(page.downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        Assertions.assertTrue(csv.isNotEmpty())
    }

    @Test
    fun `List layout vertical geometry`() {
        val (trackNumber, trackNumberId) = mainOfficialContext.createTrackNumberAndId()
        val goodPlan = insertGoodPlan(trackNumber)
        insertMinimalPlan(trackNumber)

        linkPlanToSomeLocationTrack(goodPlan, trackNumberId)
        startGeoviite()
        val page = navigationBar.goToVerticalGeometryListPage()
        page.selectLocationTrack("foo test track")
        val results = page.resultList
        results.waitUntilItemCount(2)
        val resultItems = results.items
        assertEquals(listOf("5.280", "5.240"), resultItems.map { it.pviPointHeight })
        assertEquals(listOf("385808.877", "385943.755"), resultItems.map { it.pviPointLocationE })

        val csv = webClient.get().uri(page.downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        Assertions.assertTrue(csv.isNotEmpty())
    }

    @Test
    fun `List entire track vertical geometry`() {
        val (trackNumber, trackNumberId) = mainOfficialContext.createTrackNumberAndId()
        val goodPlan = insertGoodPlan(trackNumber)
        insertMinimalPlan(trackNumber)

        linkPlanToSomeLocationTrack(goodPlan, trackNumberId)
        geometryService.makeEntireVerticalGeometryListingCsv()
        startGeoviite()
        val page = navigationBar.goToVerticalGeometryListPage().entireNetworkPage()

        val csv = webClient.get().uri(page.downloadUrl).retrieve().bodyToMono(String::class.java).block()

        assertNotNull(csv)
        Assertions.assertTrue(csv.isNotEmpty())
    }

    private fun someGeometryProfile() =
        GeometryProfile(
            name = PlanElementName("test-profile"),
            elements =
                listOf(
                    VIPoint(PlanElementName("startpoint"), Point(0.0, 5.3)),
                    VICircularCurve(PlanElementName("one curve"), Point(30.0, 5.28), BigDecimal(2000), BigDecimal(4)),
                    VICircularCurve(
                        PlanElementName("another curve"),
                        Point(197.0, 5.24),
                        BigDecimal(-3000),
                        BigDecimal(22.5),
                    ),
                    VIPoint(PlanElementName("endpoint"), Point(216.386446, 5.094602)),
                ),
        )

    // no km posts, no elements, profile only, final destination
    private fun insertMinimalPlan(trackNumber: TrackNumber): RowVersion<GeometryPlan> =
        geometryDao.insertPlan(
            plan =
                plan(
                    trackNumber = trackNumber,
                    srid = LAYOUT_SRID,
                    alignments =
                        listOf(geometryAlignment(name = "test-alignment-name", profile = someGeometryProfile())),
                    coordinateSystemName = CoordinateSystemName("testcrs"),
                    verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
                ),
            file = infraModelFile("minimalplan.xml"),
            boundingBoxInLayoutCoordinates = null,
        )

    private fun insertGoodPlan(trackNumber: TrackNumber): RowVersion<GeometryPlan> =
        geometryDao.insertPlan(
            plan =
                plan(
                    trackNumber = trackNumber,
                    srid = LAYOUT_SRID,
                    kmPosts =
                        listOf(
                            createGeometryKmPost(
                                staInternal = BigDecimal(-10),
                                location = DEFAULT_BASE_POINT,
                                kmNumber = "0045",
                            )
                        ),
                    alignments =
                        listOf(
                            geometryAlignment(
                                name = "test-alignment-name",
                                elements =
                                    listOf(
                                        lineAtBasePoint(Point(1.0, 1.0), Point(150.0, 100.0)),
                                        lineAtBasePoint(Point(150.0, 100.0), Point(300.0, 300.0)),
                                    ),
                                profile = someGeometryProfile(),
                            )
                        ),
                    coordinateSystemName = CoordinateSystemName("testcrs"),
                    verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
                ),
            file = infraModelFile("goodplan.xml"),
            boundingBoxInLayoutCoordinates = null,
        )

    private fun linkPlanToSomeLocationTrack(
        planVersion: RowVersion<GeometryPlan>,
        trackNumberId: IntId<LayoutTrackNumber>,
    ) {
        val geometryPlan = geometryDao.fetchPlan(planVersion)
        val geoAlignmentA = geometryPlan.alignments[0]
        val locationTrackGeometry =
            trackGeometryOfSegments(
                segment(Point(1.0, 1.0), Point(200.0, 200.0), sourceId = geoAlignmentA.elements[0].id),
                segment(Point(200.0, 200.0), Point(300.0, 300.0), sourceId = geoAlignmentA.elements[0].id),
                segment(Point(300.0, 300.0), Point(400.0, 400.0)),
                segment(Point(400.0, 400.0), Point(500.0, 500.0), sourceId = geoAlignmentA.elements[1].id),
                segment(Point(500.0, 500.0), Point(600.0, 600.0), sourceId = geoAlignmentA.elements[1].id),
            )
        locationTrackDao.save(
            locationTrack(trackNumberId = trackNumberId, name = "foo test track", draft = false),
            locationTrackGeometry,
        )
    }
}

private fun lineAtBasePoint(start: Point, end: Point) = line(start + DEFAULT_BASE_POINT, end + DEFAULT_BASE_POINT)
