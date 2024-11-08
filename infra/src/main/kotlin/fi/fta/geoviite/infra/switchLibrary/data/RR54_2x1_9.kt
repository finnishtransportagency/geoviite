package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun RR54_2x1_9() =
    SwitchStructure(
        type = SwitchType("RR54-2x1:9"),
        presentationJointNumber = JointNumber(5),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(-11.148, -1.239)),
                SwitchJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(2), Point(11.148, 1.239)),
                SwitchJoint(JointNumber(4), Point(-11.148, 1.239)),
                SwitchJoint(JointNumber(3), Point(11.148, -1.239)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(-11.148, -1.239), end = Point(0.0, 0.0)),
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.148, 1.239)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(-11.148, 1.239), end = Point(0.0, 0.0)),
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.148, -1.239)),
                        ),
                ),
            ),
    )
