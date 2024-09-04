package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV43_300_1_9_514_O() =
    SwitchStructure(
        type = SwitchType("YV43-300-1:9,514-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(11.31, 0.0)),
                SwitchJoint(JointNumber(2), Point(28.85, 0.0)),
                SwitchJoint(JointNumber(3), Point(28.754, -1.833)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.31, 0.0)),
                            SwitchElementLine(start = Point(11.31, 0.0), end = Point(28.85, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(23.35, -1.265), radius = 231.0),
                            SwitchElementLine(start = Point(23.35, -1.265), end = Point(28.754, -1.833)),
                        ),
                ),
            ),
    )

fun YV43_300_1_9_514_V() = YV43_300_1_9_514_O().flipAlongYAxis().copy(type = SwitchType("YV43-300-1:9,514-V"))

fun YV43_300_1_9_514_1435_O() = YV43_300_1_9_514_O().copy(type = SwitchType("YV43-300-1:9,514-1435-O"))

fun YV43_300_1_9_514_1435_V() = YV43_300_1_9_514_V().copy(type = SwitchType("YV43-300-1:9,514-1435-V"))
