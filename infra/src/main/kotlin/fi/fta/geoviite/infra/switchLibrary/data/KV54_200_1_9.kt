package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun KV54_200_1_9_O() =
    SwitchStructure(
        type = SwitchType("KV54-200-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(11.077, 0.0)),
                SwitchJoint(JointNumber(6), Point(22.4, 0.0)),
                SwitchJoint(JointNumber(2), Point(39.623, 0.0)),
                SwitchJoint(JointNumber(3), Point(28.195, -1.902)),
                SwitchJoint(JointNumber(4), Point(39.518, 1.902)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(6), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.077, 0.0)),
                            SwitchElementLine(start = Point(11.077, 0.0), end = Point(22.4, 0.0)),
                            SwitchElementLine(start = Point(22.4, 0.0), end = Point(39.623, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(22.086, -1.223), radius = 200.0),
                            SwitchElementLine(start = Point(22.086, -1.223), end = Point(28.195, -1.902)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(4)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.323, 0.0)),
                            SwitchElementCurve(start = Point(11.323, 0.0), end = Point(33.406, 1.223), radius = 200.0),
                            SwitchElementLine(start = Point(33.406, 1.223), end = Point(39.518, 1.902)),
                        ),
                ),
            ),
    )

fun KV54_200_1_9_V() = KV54_200_1_9_O().flipAlongYAxis().copy(type = SwitchType("KV54-200-1:9-V"))

fun KV54_200N_1_9_O() = KV54_200_1_9_O().copy(type = SwitchType("KV54-200N-1:9-O"))

fun KV54_200N_1_9_V() = KV54_200_1_9_V().copy(type = SwitchType("KV54-200N-1:9-V"))
