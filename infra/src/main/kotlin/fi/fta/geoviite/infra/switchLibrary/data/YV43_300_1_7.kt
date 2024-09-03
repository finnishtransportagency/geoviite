package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV43_300_1_7_O() =
    SwitchStructure(
        type = SwitchType("YV43-300-1:7-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(13.481, 0.0)),
                SwitchJoint(JointNumber(2), Point(26.506, 0.0)),
                SwitchJoint(JointNumber(3), Point(26.375, -1.842)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(13.481, 0.0)),
                            SwitchElementLine(start = Point(13.481, 0.0), end = Point(26.506, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(26.145, -1.809), radius = 180.0),
                            SwitchElementLine(start = Point(26.145, -1.809), end = Point(26.375, -1.842)),
                        ),
                ),
            ),
    )

fun YV43_300_1_7_V() = YV43_300_1_7_O().flipAlongYAxis().copy(type = SwitchType("YV43-300-1:7-V"))
