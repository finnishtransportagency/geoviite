package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.testdata.createTrackLayoutTrackNumber
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.Assertions.assertEquals

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class GeometryElementListTestUI @Autowired constructor(
    private val testGeometryPlanService: TestGeometryPlanService,
    private val switchStructureDao: SwitchStructureDao,
    private val switchDao: LayoutSwitchDao,
    private val geometryDao: GeometryDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
) : SeleniumTest() {

    @BeforeAll
    fun cleanup() {
        clearAllTestData()
    }

    @Test
    fun `List layout geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        val planVersion = geometryDao.insertPlan(
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

        startGeoviite()
        val elementListPage = navigationBar.goToElementListPage()
        elementListPage.selectLocationTrack("foo")

        val results = elementListPage.resultList
        val firstRow = results.getItemWhenMatches { r -> r.length == "2.828" }
        assertEquals("foo test track", firstRow.locationTrack)
        assertEquals("1.000", firstRow.locationStartE)
        assertEquals(5, results.items.size)

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
}
