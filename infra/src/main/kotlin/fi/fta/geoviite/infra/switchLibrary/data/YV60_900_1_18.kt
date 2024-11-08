package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_900_1_18_O() =
    SwitchStructure(
        type = SwitchType("YV60-900-1:18-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(25.999, 0.0)),
                SwitchJoint(JointNumber(2), Point(59.700, 0.0)),
                SwitchJoint(JointNumber(3), Point(59.648, -1.869)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(25.999, 0.0)),
                            SwitchElementLine(start = Point(25.999, 0.0), end = Point(59.700, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(50.941, -1.386), radius = 900.0),
                            SwitchElementLine(start = Point(50.941, -1.386), end = Point(59.648, -1.869)),
                        ),
                ),
            ),
    )

fun YV60_900_1_18_V() = YV60_900_1_18_O().flipAlongYAxis().copy(type = SwitchType("YV60-900-1:18-V"))

fun YV60_900A_1_18_O() = YV60_900_1_18_O().copy(type = SwitchType("YV60-900A-1:18-O"))

fun YV60_900A_1_18_V() = YV60_900_1_18_V().copy(type = SwitchType("YV60-900A-1:18-V"))

fun YV60_900P_1_18_O() = YV60_900_1_18_O().copy(type = SwitchType("YV60-900P-1:18-O"))

fun YV60_900P_1_18_V() = YV60_900_1_18_V().copy(type = SwitchType("YV60-900P-1:18-V"))
