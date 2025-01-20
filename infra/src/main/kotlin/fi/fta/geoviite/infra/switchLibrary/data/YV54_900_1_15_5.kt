package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV54_900_1_15_5_V() =
    SwitchStructureData(
        type = SwitchType("YV54-900-1:15,5-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(30.06, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(59.1, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(59.04, 1.872)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(30.06, 0.0)),
                            SwitchStructureLine(start = Point(30.06, 0.0), end = Point(59.1, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(59.04, 1.872), radius = 900.0)),
                ),
            ),
    )

fun YV54_900_1_15_5_O() = YV54_900_1_15_5_V().flipAlongYAxis().copy(type = SwitchType("YV54-900-1:15,5-O"))
