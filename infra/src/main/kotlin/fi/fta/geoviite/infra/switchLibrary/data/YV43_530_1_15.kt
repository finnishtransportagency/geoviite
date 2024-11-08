package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE:
// This switch may contain inaccurate location for joint point 3 as
// that point was not marked in the IM file but was reasoned by a developer.

fun YV43_530_1_15_O() =
    SwitchStructure(
        type = SwitchType("YV43-530-1:15-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(15.802, 0.0)),
                SwitchJoint(JointNumber(2), Point(43.195, 0.0)),
                SwitchJoint(JointNumber(3), Point(42.984, -1.689)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(15.802, 0.0)),
                            SwitchElementLine(start = Point(15.802, 0.0), end = Point(43.195, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(35.256, -1.174), radius = 530.0),
                            SwitchElementLine(start = Point(35.256, -1.174), end = Point(42.984, -1.689)),
                        ),
                ),
            ),
    )

fun YV43_530_1_15_V() = YV43_530_1_15_O().flipAlongYAxis().copy(type = SwitchType("YV43-530-1:15-V"))
