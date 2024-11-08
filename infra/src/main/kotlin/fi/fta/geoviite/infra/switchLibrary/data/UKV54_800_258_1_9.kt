package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE:
// This switch type may contain inaccurate point locations and wrong elements
// as this model is constructed with insufficient information from IM models and
// with limited understanding of domain.

fun UKV54_800_258_1_9_V() =
    SwitchStructure(
        type = SwitchType("UKV54-800/258-1:9-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(7), Point(9.709, 0.0)),
                SwitchJoint(JointNumber(8), Point(14.138, 0.0)),
                SwitchJoint(JointNumber(10), Point(28.230, 1.398)),
                SwitchJoint(JointNumber(11), Point(28.268, -0.499)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(10)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(19.403, 0.731), radius = 258.0),
                            SwitchElementLine(start = Point(19.403, 0.731), end = Point(28.230, 1.398)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(11)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(28.268, -0.499), radius = 800.0)),
                ),
            ),
    )

fun UKV54_800_258_1_9_O() = UKV54_800_258_1_9_V().flipAlongYAxis().copy(type = SwitchType("UKV54-800/258-1:9-O"))
