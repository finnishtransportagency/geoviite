package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE:
// Joint number 5 is not accurate and copied from the switch
// "TYV54-200-1:4.44", but in most cases this won't cause
// any problems.

fun TYV54_200_1_4_44TPE() =
    SwitchStructure(
        type = SwitchType("TYV54-200-1:4,44TPE"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(11.077, 0.0)),
                SwitchJoint(JointNumber(2), Point(22.305, 1.236)),
                SwitchJoint(JointNumber(3), Point(22.305, -1.236)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(2)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(22.305, 1.236), radius = 200.0)),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(22.305, -1.236), radius = 200.0)),
                ),
            ),
    )
