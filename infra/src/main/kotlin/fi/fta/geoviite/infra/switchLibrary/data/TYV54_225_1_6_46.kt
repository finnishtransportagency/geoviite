package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun TYV54_225_1_6_46() =
    SwitchStructure(
        type = SwitchType("TYV54-225-1:6,46"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(8.706, 0.0)),
                SwitchJoint(JointNumber(2), Point(20.942, 0.941)),
                SwitchJoint(JointNumber(3), Point(20.942, -0.941)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(17.322, 0.663), radius = 225.0),
                            SwitchElementLine(start = Point(17.322, 0.663), end = Point(20.942, 0.941)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(17.322, -0.663), radius = 225.0),
                            SwitchElementLine(start = Point(17.322, 0.663), end = Point(20.942, -0.941)),
                        ),
                ),
            ),
    )
