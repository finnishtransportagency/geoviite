package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV30_270_1_9_514_V() =
    SwitchStructure(
        type = SwitchType("YV30-270-1:9,514-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(10.422, 0.0)),
                SwitchJoint(JointNumber(2), Point(27.962, 0.0)),
                SwitchJoint(JointNumber(3), Point(27.866, 1.833)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(10.422, 0.0)),
                            SwitchElementLine(start = Point(10.422, 0.0), end = Point(27.962, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(22.722, 1.293), radius = 236.0),
                            SwitchElementLine(start = Point(22.722, 1.293), end = Point(27.866, 1.833)),
                        ),
                ),
            ),
    )

fun YV30_270_1_9_514_O() = YV30_270_1_9_514_V().flipAlongYAxis().copy(type = SwitchType("YV30-270-1:9,514-O"))
