package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

// NOTE:
// This switch type may contain inaccurate point locations and wrong elements
// as this model is constructed with insufficient information from IM models and
// with limited understanding of domain.

fun YV60_300P_1_9_O() =
    SwitchStructure(
        type = SwitchType("YV60-300P-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(16.615, 0.0)),
                SwitchJoint(JointNumber(2), Point(33.23, 0.0)),
                SwitchJoint(JointNumber(3), Point(33.128, -1.835)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(16.615, 0.0)),
                            SwitchElementLine(start = Point(16.615, 0.0), end = Point(33.23, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(33.128, -1.835), radius = 300.0)),
                ),
            ),
    )

fun YV60_300P_1_9_V() = YV60_300P_1_9_O().flipAlongYAxis().copy(type = SwitchType("YV60-300P-1:9-V"))
