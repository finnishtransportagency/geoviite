package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE:
// This switch type may contain inaccurate point locations and wrong elements
// as this model is constructed with insufficient information from IM models and
// with limited understanding of domain.

fun UKV54_1500_228_1_9_O() =
    SwitchStructure(
        type = SwitchType("UKV54-1500/228-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(7), Point(10.476, 0.0)),
                SwitchJoint(JointNumber(8), Point(14.143, 0.0)),
                SwitchJoint(JointNumber(10), Point(28.228, -1.480)),
                SwitchJoint(JointNumber(11), Point(28.284, 0.256)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(10)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(1.054, 0.0)),
                            SwitchElementCurve(start = Point(1.054, 0.0), end = Point(19.865, 0.785), radius = 228.0),
                            SwitchElementLine(start = Point(19.865, 0.785), end = Point(28.228, -1.480)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(11)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(28.284, 0.256), radius = 1500.0)),
                ),
            ),
    )

fun UKV54_1500_228_1_9_V() = UKV54_1500_228_1_9_O().flipAlongYAxis().copy(type = SwitchType("UKV54-1500/228-1:9-V"))
