package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchElementLine
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun RR43_1_9_514() =
    SwitchStructure(
        type = SwitchType("RR43-1:9,514"),
        presentationJointNumber = JointNumber(5),
        joints =
            listOf(
                SwitchJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(1), Point(-17.44, 1.84)),
                SwitchJoint(JointNumber(2), Point(17.44, -1.84)),
                SwitchJoint(JointNumber(4), Point(-17.44, -1.84)),
                SwitchJoint(JointNumber(3), Point(17.44, 1.84)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(-17.44, 1.84), end = Point(0.0, 0.0)),
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(17.44, -1.84)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(start = Point(-17.44, -1.84), end = Point(0.0, 0.0)),
                            SwitchElementLine(start = Point(0.0, 0.0), end = Point(17.44, 1.84)),
                        ),
                ),
            ),
    )
