package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun UKV60_1000_244_1_9_O() =
    SwitchStructure(
        type = SwitchType("UKV60-1000/244-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(7), Point(10.074, 0.0)),
                SwitchJoint(JointNumber(8), Point(14.141, 0.0)),
                SwitchJoint(JointNumber(10), Point(28.290, -0.400)),
                SwitchJoint(JointNumber(11), Point(28.217, 1.498)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(10)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(28.290, -0.400), radius = 1000.0)
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(11)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(28.217, 1.498), radius = 244.45)),
                ),
            ),
    )

fun UKV60_1000_244_1_9_V() = UKV60_1000_244_1_9_O().flipAlongYAxis().copy(type = SwitchType("UKV60-1000/244-1:9-V"))
