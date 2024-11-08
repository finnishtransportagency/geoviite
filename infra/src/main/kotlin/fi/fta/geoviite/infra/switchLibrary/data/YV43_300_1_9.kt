package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV43_300_1_9_O() =
    SwitchStructure(
        type = SwitchType("YV43-300-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(12.004, 0.0)),
                SwitchJoint(JointNumber(2), Point(28.85, 0.0)),
                SwitchJoint(JointNumber(3), Point(28.747, -1.86)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(12.004, 0.0)),
                            SwitchElementLine(start = Point(12.004, 0.0), end = Point(28.85, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(23.935, -1.326), radius = 216.737),
                            SwitchElementLine(start = Point(23.935, -1.326), end = Point(28.747, -1.86)),
                        ),
                ),
            ),
    )

fun YV43_300_1_9_V() = YV43_300_1_9_O().flipAlongYAxis().copy(type = SwitchType("YV43-300-1:9-V"))
