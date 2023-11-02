package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LinearUnit
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class VerticalGeometryDiagramTestUI @Autowired constructor(
    private val referenceLineService: ReferenceLineService,
    private val testGeometryPlanService: TestGeometryPlanService,
    private val switchDao: LayoutSwitchDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val kmPostDao: LayoutKmPostDao,
    private val locationTrackService: LocationTrackService,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
) : SeleniumTest() {

    @BeforeEach
    fun cleanup() {
        clearAllTestData()
    }

    @Test
    fun `Vertical geometry diagram for location track loads`() {
        val trackNumber = insertOfficialTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumber),
            alignment(segment(DEFAULT_BASE_POINT + Point(0.0, 0.0), DEFAULT_BASE_POINT + Point(1000.0, 0.0)))
        )
        kmPostDao.insert(kmPost(trackNumber, km = KmNumber(0), location = DEFAULT_BASE_POINT + Point(0.0, 0.0)))
        val plan = geometryDao.fetchPlan(
            geometryDao.insertPlan(
                plan(
                    trackNumber, units = GeometryUnits(
                        coordinateSystemSrid = LAYOUT_SRID,
                        coordinateSystemName = null,
                        verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
                        directionUnit = AngularUnit.GRADS,
                        linearUnit = LinearUnit.METER,
                    ), alignments = listOf(
                        geometryAlignment(
                            trackNumber, elements = listOf(
                                line(
                                    DEFAULT_BASE_POINT + Point(0.0, 0.0), DEFAULT_BASE_POINT + Point(1000.0, 0.0)
                                )
                            ), profile = GeometryProfile(
                                PlanElementName("aoeu"), listOf(
                                    VIPoint(PlanElementName("startpoint"), Point(0.0, 50.0)),
                                    VICircularCurve(
                                        PlanElementName("rounding"),
                                        Point(500.0, 50.0),
                                        BigDecimal(20000),
                                        BigDecimal(155),
                                    ),
                                    VIPoint(PlanElementName("endpoint"), Point(600.0, 51.0)),
                                )
                            )
                        )

                    )
                ), testFile(), null
            )
        )
        locationTrackService.saveDraft(
            locationTrack(trackNumber, name = "foo bar"), alignment(
                segment(DEFAULT_BASE_POINT + Point(0.0, 0.0), DEFAULT_BASE_POINT + Point(1000.0, 0.0)).copy(
                    sourceId = plan.alignments[0].elements[0].id,
                    sourceStart = 0.0,
                )
            )
        )
        startGeoviite()
        val page = goToMap().switchToDraftMode().zoomToScale(E2ETrackLayoutPage.MapScale.M_500)
        page.selectionPanel.selectLocationTrack("foo bar")
        page.toolPanel.locationTrackVerticalGeometry.toggleVerticalGeometryDiagram()
        page.verticalGeometryDiagram.waitForContent()
    }

}
