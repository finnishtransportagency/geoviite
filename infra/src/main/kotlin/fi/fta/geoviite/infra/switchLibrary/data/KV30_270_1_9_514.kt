package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun KV30_270_1_9_514_O() =
    SwitchStructure(
        type = SwitchType("KV30-270-1:9,514-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(10.42, 0.0)),
                SwitchJoint(JointNumber(6), Point(20.42, 0.0)),
                SwitchJoint(JointNumber(2), Point(37.96, 0.0)),
                SwitchJoint(JointNumber(3), Point(27.864, -1.833)),
                SwitchJoint(JointNumber(4), Point(37.96, 1.833)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(6), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(10.42, 0.0)),
                            SwitchElementLine(start = Point(10.42, 0.0), end = Point(20.42, 0.0)),
                            SwitchElementLine(start = Point(20.42, 0.0), end = Point(37.96, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(20.783, -1.089), radius = 198.825),
                            SwitchElementLine(start = Point(20.783, -1.089), end = Point(27.864, -1.833)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(4)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(10.0, 0.0)),
                            SwitchElementCurve(start = Point(10.0, 0.0), end = Point(30.783, 1.089), radius = 198.825),
                            SwitchElementLine(start = Point(30.783, 1.089), end = Point(37.864, 1.833)),
                        ),
                ),
            ),
    )

fun KV30_270_1_9_514_V() = KV30_270_1_9_514_O().flipAlongYAxis().copy(type = SwitchType("KV30-270-1:9,514-V"))
