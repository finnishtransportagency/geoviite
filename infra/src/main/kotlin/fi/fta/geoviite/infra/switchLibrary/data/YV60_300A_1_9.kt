package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_300A_1_9_O() =
    SwitchStructure(
        type = SwitchType("YV60-300A-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(16.615, 0.0)),
                SwitchJoint(JointNumber(2), Point(33.230, 0.0)),
                SwitchJoint(JointNumber(3), Point(33.130, -1.828)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(16.615, 0.0)),
                            SwitchElementLine(start = Point(16.615, 0.0), end = Point(33.230, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(33.130, -1.828), radius = 300.0)),
                ),
            ),
    )

fun YV60_300A_1_9_V() = YV60_300A_1_9_O().flipAlongYAxis().copy(type = SwitchType("YV60-300A-1:9-V"))
