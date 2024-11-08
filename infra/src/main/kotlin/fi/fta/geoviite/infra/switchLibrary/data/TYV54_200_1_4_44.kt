package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun TYV54_200_1_4_44() =
    SwitchStructure(
        type = SwitchType("TYV54-200-1:4,44"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(11.077, 0.0)),
                SwitchJoint(JointNumber(2), Point(22.086, 1.223)),
                SwitchJoint(JointNumber(3), Point(22.086, -1.223)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(2)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(22.086, 1.223), radius = 200.0)),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(22.086, -1.223), radius = 200.0)),
                ),
            ),
    )
