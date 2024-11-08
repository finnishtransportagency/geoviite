package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV54_200_1_9_O() =
    SwitchStructure(
        type = SwitchType("YV54-200-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(11.077, 0.0)),
                SwitchJoint(JointNumber(2), Point(28.300, 0.0)),
                SwitchJoint(JointNumber(3), Point(28.195, -1.902)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.077, 0.0)),
                            SwitchElementLine(start = Point(11.077, 0.0), end = Point(28.300, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(22.086, -1.223), radius = 300.0),
                            SwitchElementLine(start = Point(22.086, -1.223), end = Point(28.195, -1.902)),
                        ),
                ),
            ),
    )

fun YV54_200_1_9_V() = YV54_200_1_9_O().flipAlongYAxis().copy(type = SwitchType("YV54-200-1:9-V"))

fun YV54_200N_1_9_O() = YV54_200_1_9_O().copy(type = SwitchType("YV54-200N-1:9-O"))

fun YV54_200N_1_9_V() = YV54_200_1_9_V().copy(type = SwitchType("YV54-200N-1:9-V"))

fun YV54_200_1_9_1435_O() = YV54_200_1_9_O().copy(type = SwitchType("YV54-200-1:9-1435-O"))

fun YV54_200_1_9_1435_V() = YV54_200_1_9_V().copy(type = SwitchType("YV54-200-1:9-1435-V"))

fun YV54_200N_1_9_1435_O() = YV54_200_1_9_O().copy(type = SwitchType("YV54-200N-1:9-1435-O"))

fun YV54_200N_1_9_1435_V() = YV54_200_1_9_V().copy(type = SwitchType("YV54-200N-1:9-1435-V"))

fun YV54_200_1_9_1524_1435_O() = YV54_200_1_9_O().copy(type = SwitchType("YV54-200-1:9-1524/1435-O"))

fun YV54_200_1_9_1524_1435_V() = YV54_200_1_9_V().copy(type = SwitchType("YV54-200-1:9-1524/1435-V"))
