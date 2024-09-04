package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun KRV43_270_1_9_514() =
    SwitchStructure(
        type = SwitchType("KRV43-270-1:9,514"),
        presentationJointNumber = JointNumber(5),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(17.540, 0.0)),
                SwitchJoint(JointNumber(2), Point(35.080, 0.0)),
                SwitchJoint(JointNumber(4), Point(0.096, 1.833)),
                SwitchJoint(JointNumber(3), Point(34.984, -1.833)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(17.540, 0.0)),
                            SwitchElementLine(start = Point(17.540, 0.0), end = Point(35.080, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.096, 1.833), end = Point(17.540, 0.0)),
                            SwitchElementLine(start = Point(17.540, 0.0), end = Point(35.080, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(6.010, 0.0)),
                            SwitchElementCurve(start = Point(6.010, 0.0), end = Point(29.006, -1.206), radius = 220.0),
                            SwitchElementLine(start = Point(29.006, -1.206), end = Point(34.984, -1.833)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.096, 1.833), end = Point(6.0734, 1.206)),
                            SwitchElementCurve(start = Point(6.0734, 1.206), end = Point(29.07, 0.0), radius = 220.0),
                            SwitchElementLine(start = Point(29.07, 0.0), end = Point(35.080, 0.0)),
                        ),
                ),
            ),
    )
