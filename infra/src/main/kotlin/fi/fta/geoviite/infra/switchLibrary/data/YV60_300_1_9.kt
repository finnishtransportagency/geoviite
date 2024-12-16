package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchElementCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchElementLine
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV60_300_1_9_O() =
    SwitchStructure(
        type = SwitchType("YV60-300-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(16.615, 0.0)),
                SwitchJoint(JointNumber(2), Point(34.430, 0.0)),
                SwitchJoint(JointNumber(3), Point(34.321, -1.967)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(16.615, 0.0)),
                            SwitchElementLine(start = Point(16.615, 0.0), end = Point(34.430, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(start = Point(0.0, 0.0), end = Point(33.128, -1.835), radius = 300.0),
                            SwitchElementLine(start = Point(33.128, -1.835), end = Point(34.321, -1.967)),
                        ),
                ),
            ),
    )

fun YV60_300_1_9_V() = YV60_300_1_9_O().flipAlongYAxis().copy(type = SwitchType("YV60-300-1:9-V"))

fun YV60_300A_1_9_O() = YV60_300_1_9_O().copy(type = SwitchType("YV60-300A-1:9-O"))

fun YV60_300A_1_9_V() = YV60_300_1_9_V().copy(type = SwitchType("YV60-300A-1:9-V"))


fun YV60_300E_1_9_O() = YV60_300_1_9_O().copy(type = SwitchType("YV60-300E-1:9-O"))

fun YV60_300E_1_9_V() = YV60_300_1_9_V().copy(type = SwitchType("YV60-300E-1:9-V"))
