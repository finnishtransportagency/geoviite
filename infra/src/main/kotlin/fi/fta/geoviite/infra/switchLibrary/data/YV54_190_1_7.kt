package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE:
// This switch is not in RATO4 but found from IM models

fun YV54_190_1_7_O() =
    SwitchStructure(
        type = SwitchType("YV54-190-1:7-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(13.504, 0.0)),
                SwitchJoint(JointNumber(2), Point(27.006, 0.0)),
                SwitchJoint(JointNumber(3), Point(26.87, -1.91)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(13.504, 0.0)),
                            SwitchElementLine(start = Point(13.504, 0.0), end = Point(27.006, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(26.87, -1.91), radius = 190.0)),
                ),
            ),
    )

fun YV54_190_1_7_V() = YV54_190_1_7_O().flipAlongYAxis().copy(type = SwitchType("YV54-190-1:7-V"))
