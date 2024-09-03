package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_5000_2500_1_26_O() =
    SwitchStructure(
        type = SwitchType("YV60-5000/2500-1:26-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(56.563, 0.0)),
                SwitchJoint(JointNumber(2), Point(107.180, 0.0)),
                SwitchJoint(JointNumber(3), Point(107.143, -1.945)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(56.563, 0.0)),
                            SwitchElementLine(start = Point(56.563, 0.0), end = Point(107.180, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(36.979, -0.182), radius = 5000.0),
                            SwitchElementCurve(
                                start = Point(36.979, -0.182),
                                end = Point(105.327, -1.876),
                                radius = 2500.0,
                            ),
                            SwitchElementLine(start = Point(105.327, -1.876), end = Point(107.143, -1.945)),
                        ),
                ),
            ),
    )

fun YV60_5000_2500_1_26_V() = YV60_5000_2500_1_26_O().flipAlongYAxis().copy(type = SwitchType("YV60-5000/2500-1:26-V"))
