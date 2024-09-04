package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE:
// Joint number 5 is not accurate and copied from the switch
// "TYV54-225-1:6.46", but in most cases this won't cause
// any problems.

fun TYV54_225_1_6_46TPE() =
    SwitchStructure(
        type = SwitchType("TYV54-225-1:6,46TPE"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(8.706, 0.0)),
                SwitchJoint(JointNumber(2), Point(21.140, 0.949)),
                SwitchJoint(JointNumber(3), Point(21.140, -0.949)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(17.421, 0.663), radius = 225.0),
                            SwitchElementLine(start = Point(17.421, 0.663), end = Point(21.140, 0.949)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(17.421, -0.663), radius = 225.0),
                            SwitchElementLine(start = Point(17.421, 0.663), end = Point(21.140, -0.949)),
                        ),
                ),
            ),
    )
