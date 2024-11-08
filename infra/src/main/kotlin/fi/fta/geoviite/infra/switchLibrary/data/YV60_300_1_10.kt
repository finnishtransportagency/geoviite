package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_300_1_10_V() =
    SwitchStructure(
        type = SwitchType("YV60-300-1:10-V"),
        id = IntId(123456),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(12.97, 0.0)),
                SwitchJoint(JointNumber(2), Point(32.008, 0.0)),
                SwitchJoint(JointNumber(3), Point(31.914, 1.894)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(12.97, 0.0)),
                            SwitchElementLine(start = Point(12.97, 0.0), end = Point(32.008, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(25.876, 1.291), radius = 260.047),
                            SwitchElementLine(start = Point(25.876, 1.291), end = Point(31.914, 1.894)),
                        ),
                ),
            ),
    )

fun YV60_300_1_10_O() = YV60_300_1_10_V().flipAlongYAxis().copy(type = SwitchType("YV60-300-1:10-O"))
