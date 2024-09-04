package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV54_900_1_15_5_V() =
    SwitchStructure(
        type = SwitchType("YV54-900-1:15,5-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(30.06, 0.0)),
                SwitchJoint(JointNumber(2), Point(59.1, 0.0)),
                SwitchJoint(JointNumber(3), Point(59.04, 1.872)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(30.06, 0.0)),
                            SwitchElementLine(start = Point(30.06, 0.0), end = Point(59.1, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(59.04, 1.872), radius = 900.0)),
                ),
            ),
    )

fun YV54_900_1_15_5_O() = YV54_900_1_15_5_V().flipAlongYAxis().copy(type = SwitchType("YV54-900-1:15,5-O"))
