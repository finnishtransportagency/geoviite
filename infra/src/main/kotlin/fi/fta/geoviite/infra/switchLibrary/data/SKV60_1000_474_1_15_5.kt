package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun SKV60_1000_474_1_15_5_O() =
    SwitchStructure(
        type = SwitchType("SKV60-1000/474-1:15,5-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(13), Point(29.555, 0.0)),
                SwitchJoint(JointNumber(16), Point(59.058, -1.745)),
                SwitchJoint(JointNumber(17), Point(58.896, -3.553)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(16)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(59.058, -1.745), radius = 1000.0)
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(17)),
                    elements =
                        listOf(SwitchElementCurve(start = Point(0.0, 0.0), end = Point(58.896, -3.553), radius = 474.0)),
                ),
            ),
    )

fun SKV60_1000_474_1_15_5_V() =
    SKV60_1000_474_1_15_5_O().flipAlongYAxis().copy(type = SwitchType("SKV60-1000/474-1:15,5-V"))
