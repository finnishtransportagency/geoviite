package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_5000_3000_1_28_O() =
    SwitchStructure(
        type = SwitchType("YV60-5000/3000-1:28-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(58.220, 0.0)),
                SwitchJoint(JointNumber(2), Point(112.039, 0.0)),
                SwitchJoint(JointNumber(3), Point(112.005, -1.921)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(58.220, 0.0)),
                            SwitchElementLine(start = Point(58.220, 0.0), end = Point(112.039, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(24.653, -0.074), radius = 5000.0),
                            SwitchElementCurve(
                                start = Point(24.653, -0.074),
                                end = Point(112.005, -1.921),
                                radius = 3000.0,
                            ),
                        ),
                ),
            ),
    )

fun YV60_5000_3000_1_28_V() = YV60_5000_3000_1_28_O().flipAlongYAxis().copy(type = SwitchType("YV60-5000/3000-1:28-V"))
