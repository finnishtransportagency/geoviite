package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun KRV43_233_1_9() =
    SwitchStructure(
        type = SwitchType("KRV43-233-1:9"),
        presentationJointNumber = JointNumber(5),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(16.846, 0.0)),
                SwitchJoint(JointNumber(2), Point(33.692, 0.0)),
                SwitchJoint(JointNumber(4), Point(0.103, 1.86)),
                SwitchJoint(JointNumber(3), Point(33.589, -1.86)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(16.846, 0.0)),
                            SwitchElementLine(start = Point(16.846, 0.0), end = Point(33.692, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.103, 1.86), end = Point(16.846, 0.0)),
                            SwitchElementLine(start = Point(16.846, 0.0), end = Point(33.692, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            // NOTE:
                            // Most probably there should be line elements before and after the
                            // curve,
                            // but did not have information about those elements and therefore this
                            // is simplified. This does not affect current functionality, joint
                            // point
                            // locations are the most important information.
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(33.589, -1.86), radius = 233.0)
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(2)),
                    elements =
                        listOf(
                            // NOTE:
                            // Most probably there should be line elements before and after the
                            // curve,
                            // but did not have information about those elements and therefore this
                            // is simplified. This does not affect current functionality, joint
                            // point
                            // locations are the most important information.
                            SwitchElementCurve(start = Point(0.103, 1.86), end = Point(33.692, 0.0), radius = 233.0)
                        ),
                ),
            ),
    )
