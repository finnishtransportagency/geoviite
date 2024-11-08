package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_500_1_14_O() =
    SwitchStructure(
        type = SwitchType("YV60-500-1:14-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(17.834, 0.0)),
                SwitchJoint(JointNumber(2), Point(44.943, 0.0)),
                SwitchJoint(JointNumber(3), Point(44.874, -1.931)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(17.834, 0.0)),
                            SwitchElementLine(start = Point(17.834, 0.0), end = Point(44.943, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(35.623, -1.271), radius = 500.0),
                            SwitchElementLine(start = Point(35.623, -1.271), end = Point(44.874, -1.931)),
                        ),
                ),
            ),
    )

fun YV60_500_1_14_V() = YV60_500_1_14_O().flipAlongYAxis().copy(type = SwitchType("YV60-500-1:14-V"))

fun YV60_500A_1_14_O() = YV60_500_1_14_O().copy(type = SwitchType("YV60-500A-1:14-O"))

fun YV60_500A_1_14_V() = YV60_500_1_14_V().copy(type = SwitchType("YV60-500A-1:14-V"))
