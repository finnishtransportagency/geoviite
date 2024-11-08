package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV54_165_1_7_V() =
    SwitchStructure(
        type = SwitchType("YV54-165-1:7-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(13.482, 0.0)),
                SwitchJoint(JointNumber(2), Point(26.506, 0.0)),
                SwitchJoint(JointNumber(3), Point(26.376, 1.842)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(13.482, 0.0)),
                            SwitchElementLine(start = Point(13.482, 0.0), end = Point(26.506, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(25.09, 1.658), radius = 165.0),
                            SwitchElementLine(start = Point(25.09, 1.658), end = Point(26.376, 1.842)),
                        ),
                ),
            ),
    )

fun YV54_165_1_7_O() = YV54_165_1_7_V().flipAlongYAxis().copy(type = SwitchType("YV54-165-1:7-O"))
