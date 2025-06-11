package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LinearUnit
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.GeometryUnits
import fi.fta.geoviite.infra.geometry.VICircularCurve
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackDbName
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import java.math.BigDecimal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class VerticalGeometryDiagramTestUI
@Autowired
constructor(
    private val referenceLineService: ReferenceLineService,
    private val kmPostDao: LayoutKmPostDao,
    private val locationTrackService: LocationTrackService,
    private val geometryDao: GeometryDao,
) : SeleniumTest() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Vertical geometry diagram for location track loads`() {
        val (trackNumber, trackNumberId) = mainOfficialContext.createTrackNumberAndId()
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            alignment(segment(DEFAULT_BASE_POINT + Point(0.0, 0.0), DEFAULT_BASE_POINT + Point(1000.0, 0.0))),
        )
        kmPostDao.save(
            kmPost(
                trackNumberId = trackNumberId,
                km = KmNumber(0),
                roughLayoutLocation = DEFAULT_BASE_POINT + Point(0.0, 0.0),
                draft = false,
            )
        )
        val plan =
            geometryDao.fetchPlan(
                geometryDao.insertPlan(
                    plan(
                        trackNumber = trackNumber,
                        units =
                            GeometryUnits(
                                coordinateSystemSrid = LAYOUT_SRID,
                                coordinateSystemName = null,
                                verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
                                directionUnit = AngularUnit.GRADS,
                                linearUnit = LinearUnit.METER,
                            ),
                        alignments =
                            listOf(
                                geometryAlignment(
                                    elements =
                                        listOf(
                                            line(
                                                DEFAULT_BASE_POINT + Point(0.0, 0.0),
                                                DEFAULT_BASE_POINT + Point(1000.0, 0.0),
                                            )
                                        ),
                                    profile =
                                        GeometryProfile(
                                            PlanElementName("aoeu"),
                                            listOf(
                                                VIPoint(PlanElementName("startpoint"), Point(0.0, 50.0)),
                                                VICircularCurve(
                                                    PlanElementName("rounding"),
                                                    Point(500.0, 50.0),
                                                    BigDecimal(20000),
                                                    BigDecimal(155),
                                                ),
                                                VIPoint(PlanElementName("endpoint"), Point(600.0, 51.0)),
                                            ),
                                        ),
                                )
                            ),
                    ),
                    testFile(),
                    null,
                )
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId = trackNumberId, name = locationTrackDbName("foo bar"), draft = true),
            trackGeometryOfSegments(
                segment(
                    DEFAULT_BASE_POINT + Point(0.0, 0.0),
                    DEFAULT_BASE_POINT + Point(1000.0, 0.0),
                    sourceId = plan.alignments[0].elements[0].id as IndexedId,
                    sourceStartM = 0.0,
                )
            ),
        )
        startGeoviite()
        val page = goToMap().switchToDraftMode().zoomToScale(E2ETrackLayoutPage.MapScale.M_500)
        page.selectionPanel.selectLocationTrack("foo bar")
        page.toolPanel.locationTrackVerticalGeometry.toggleVerticalGeometryDiagram()
        page.verticalGeometryDiagram.waitForContent()
    }
}
