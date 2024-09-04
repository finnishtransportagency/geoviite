package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE: Joint point locations are inferred from geometry elements with just a little domain
// knowledge and therefore this switch model may be somewhat wrong.

fun SRR54_2x1_9_6_0() =
    SwitchStructure(
        type = SwitchType("SRR54-2x1:9-6,0"),
        presentationJointNumber = JointNumber(5),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(-9.927, -9.927 / 9.0)),
                SwitchJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(2), Point(9.927, 9.927 / 9.0)),
                SwitchJoint(JointNumber(3), Point(9.927, -9.927 / 9.0)),
                SwitchJoint(JointNumber(4), Point(-9.927, 9.927 / 9.0)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(-9.927, -9.927 / 9.0), end = Point(0.0, 0.0)),
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(9.927, 9.927 / 9.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(-9.927, 9.927 / 9.0), end = Point(0.0, 0.0)),
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(9.927, -9.927 / 9.0)),
                        ),
                ),
            ),
    )
