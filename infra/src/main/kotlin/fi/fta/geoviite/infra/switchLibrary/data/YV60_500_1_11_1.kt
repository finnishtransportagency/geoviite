package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_500_1_11_1_O() =
    SwitchStructure(
        type = SwitchType("YV60-500-1:11,1-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(22.471, 0.0)),
                SwitchJoint(JointNumber(2), Point(44.943, 0.0)),
                SwitchJoint(JointNumber(3), Point(44.852, -2.016)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(22.471, 0.0)),
                            SwitchElementLine(start = Point(22.471, 0.0), end = Point(44.943, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(44.852, -2.016), radius = 500.0)),
                ),
            ),
    )

fun YV60_500_1_11_1_V() = YV60_500_1_11_1_O().flipAlongYAxis().copy(type = SwitchType("YV60-500-1:11,1-V"))

fun YV60_500A_1_11_1_O() = YV60_500_1_11_1_O().copy(type = SwitchType("YV60-500A-1:11,1-O"))

fun YV60_500A_1_11_1_V() = YV60_500_1_11_1_V().copy(type = SwitchType("YV60-500A-1:11,1-V"))
