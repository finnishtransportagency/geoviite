package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.GeometrySwitchJoint
import fi.fta.geoviite.infra.geometry.SwitchData
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.geometryLine
import fi.fta.geoviite.infra.geometry.geometrySwitch
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackNumber
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class SwitchFittingServiceIT
@Autowired
constructor(
    private val switchFittingService: SwitchFittingService,
    private val geometryDao: GeometryDao,
    private val switchLibraryService: SwitchLibraryService,
) : DBTestBase() {

    @Test
    fun `fitGeometrySwitch successfully fits good switch`() {
        val plan =
            insertPlanWithAlignmentUsingSwitch(
                geometrySwitch(
                    switchStructureId = yv60SwitchId(),
                    joints =
                        listOf(
                            GeometrySwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                            GeometrySwitchJoint(JointNumber(2), Point(0.0, 34.4)),
                            GeometrySwitchJoint(JointNumber(3), Point(2.0, 34.3)),
                            GeometrySwitchJoint(JointNumber(5), Point(0.0, 16.6)),
                        ),
                )
            )
        linkPlanAlignmentGeometry(plan)
        val result = firstSwitchFitResult(plan)
        assertTrue(result is GeometrySwitchFittingSuccess)
    }

    @Test
    fun `fitGeometrySwitch() reports bad joint positioning`() {
        val plan =
            insertPlanWithAlignmentUsingSwitch(
                geometrySwitch(
                    switchStructureId = yv60SwitchId(),
                    joints =
                        listOf(
                            GeometrySwitchJoint(JointNumber(1), Point(1.0, 1.0)),
                            GeometrySwitchJoint(JointNumber(2), Point(2.0, 1.0)),
                            GeometrySwitchJoint(JointNumber(3), Point(3.0, 1.0)),
                            GeometrySwitchJoint(JointNumber(5), Point(4.0, 1.0)),
                        ),
                )
            )
        linkPlanAlignmentGeometry(plan)
        assertEquals(
            GeometrySwitchFittingFailure(GeometrySwitchSuggestionFailureReason.INVALID_JOINTS),
            firstSwitchFitResult(plan),
        )
    }

    @Test
    fun `fitGeometrySwitch() reports lack of geometry link`() {
        val plan = insertPlanWithAlignmentUsingSwitch(geometrySwitch(switchStructureId = yv60SwitchId()))
        assertEquals(
            GeometrySwitchFittingFailure(GeometrySwitchSuggestionFailureReason.RELATED_TRACKS_NOT_LINKED),
            firstSwitchFitResult(plan),
        )
    }

    @Test
    fun `fitGeometrySwitch() reports lack of switch structure ID`() {
        val plan = insertPlanWithAlignmentUsingSwitch(geometrySwitch(switchStructureId = null))
        linkPlanAlignmentGeometry(plan)
        assertEquals(
            GeometrySwitchFittingFailure(GeometrySwitchSuggestionFailureReason.NO_SWITCH_STRUCTURE_ID_ON_SWITCH),
            firstSwitchFitResult(plan),
        )
    }

    @Test
    fun `fitGeometrySwitch() reports missing plan SRID`() {
        val plan = insertPlanWithAlignmentUsingSwitch(geometrySwitch(switchStructureId = yv60SwitchId()), srid = null)
        linkPlanAlignmentGeometry(plan)
        assertEquals(
            GeometrySwitchFittingFailure(GeometrySwitchSuggestionFailureReason.NO_SRID_ON_PLAN),
            firstSwitchFitResult(plan),
        )
    }

    @Test
    fun `fitGeometrySwitch() reports insufficient joint counts`() {
        val plan =
            insertPlanWithAlignmentUsingSwitch(
                geometrySwitch(
                    switchStructureId = yv60SwitchId(),
                    joints = listOf(GeometrySwitchJoint(JointNumber(1), Point(1.0, 1.0))),
                )
            )
        linkPlanAlignmentGeometry(plan)
        assertEquals(
            GeometrySwitchFittingFailure(GeometrySwitchSuggestionFailureReason.LESS_THAN_TWO_JOINTS),
            firstSwitchFitResult(plan),
        )
    }

    private fun yv60SwitchId() =
        switchLibraryService.getSwitchStructures().find { it.type == SwitchType("YV60-300-1:9-O") }!!.id as IntId

    private fun firstSwitchFitResult(
        plan: GeometryPlan,
        branch: LayoutBranch = LayoutBranch.main,
    ): GeometrySwitchFittingResult = switchFittingService.fitGeometrySwitch(branch, plan.switches[0].id as IntId)

    private fun insertPlanWithAlignmentUsingSwitch(switch: GeometrySwitch, srid: Srid? = LAYOUT_SRID) =
        geometryDao.fetchPlan(
            geometryDao.insertPlan(
                plan(
                    srid = srid,
                    alignments =
                        listOfNotNull(
                            geometryAlignment(geometryLine(elementSwitchData = SwitchData(switch.id, null, null)))
                        ),
                    switches = listOf(switch),
                ),
                testFile(),
                null,
            )
        )

    private fun linkPlanAlignmentGeometry(
        plan: GeometryPlan,
        layoutContext: LayoutContext = MainLayoutContext.official,
    ) {
        val context = testDBService.testContext(layoutContext.branch, layoutContext.state)
        val trackNumber = context.insert(trackNumber(testDBService.getUnusedTrackNumber())).id
        val segment =
            segment(Point(0.0, 0.0), Point(1.0, 0.0))
                .copy(sourceId = plan.alignments[0].elements[0].id as IndexedId<GeometryElement>)
        context.insert(locationTrackAndAlignment(trackNumber, segment))
    }
}
