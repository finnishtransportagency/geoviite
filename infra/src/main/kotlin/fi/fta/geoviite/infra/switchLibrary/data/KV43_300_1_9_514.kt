package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun KV43_300_1_9_514_V() =
    SwitchStructure(
        type = SwitchType("KV43-300-1:9,514-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(11.31, 0.0)),
                SwitchJoint(JointNumber(6), Point(23.38, 0.0)),
                SwitchJoint(JointNumber(2), Point(40.92, 0.0)),
                SwitchJoint(JointNumber(3), Point(28.754, 1.834)),
                SwitchJoint(JointNumber(4), Point(40.92, -1.834)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(6), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.31, 0.0)),
                            SwitchElementLine(start = Point(11.31, 0.0), end = Point(23.38, 0.0)),
                            SwitchElementLine(start = Point(23.38, 0.0), end = Point(40.92, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(23.35, 1.265), radius = 231.0),
                            SwitchElementLine(start = Point(23.35, 1.265), end = Point(28.754, 1.834)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(4)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(11.274, 0.0)),
                            SwitchElementCurve(start = Point(11.274, 0.0), end = Point(35.42, -1.266), radius = 231.0),
                            SwitchElementLine(start = Point(35.42, -1.266), end = Point(40.824, -1.834)),
                        ),
                ),
            ),
    )

fun KV43_300_1_9_514_O() = KV43_300_1_9_514_V().flipAlongYAxis().copy(type = SwitchType("KV43-300-1:9,514-O"))
