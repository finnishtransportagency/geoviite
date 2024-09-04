package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun KRV54_200_1_9() =
    SwitchStructure(
        type = SwitchType("KRV54-200-1:9"),
        presentationJointNumber = JointNumber(5),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(17.223, 0.0)),
                SwitchJoint(JointNumber(2), Point(34.446, 0.0)),
                SwitchJoint(JointNumber(4), Point(0.105, 1.902)),
                SwitchJoint(JointNumber(3), Point(34.341, -1.902)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(17.223, 0.0)),
                            SwitchElementLine(start = Point(17.223, 0.0), end = Point(34.446, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.105, 1.902), end = Point(17.223, 0.0)),
                            SwitchElementLine(start = Point(17.223, 0.0), end = Point(34.446, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(6.15, 0.0)),
                            SwitchElementCurve(start = Point(6.15, 0.0), end = Point(28.232, -1.223), radius = 200.0),
                            SwitchElementLine(start = Point(28.232, -1.223), end = Point(34.341, -1.902)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.105, 1.902), end = Point(6.214, 1.223)),
                            SwitchElementCurve(start = Point(6.214, 1.223), end = Point(28.300, 0.0), radius = 200.0),
                            SwitchElementLine(start = Point(28.300, 0.0), end = Point(34.446, 0.0)),
                        ),
                ),
            ),
    )
